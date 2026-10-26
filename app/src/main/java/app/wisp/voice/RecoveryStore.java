package app.wisp.voice;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import org.json.JSONObject;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.util.*;

/** Explicit user recovery data, separate from anonymous diagnostics and aggregate statistics. */
final class RecoveryStore {
    static final int MAX_ITEMS=10;
    static final long MAX_AGE_MS=7L*24*60*60*1000;
    private static final Object LOCK=new Object();
    interface Keys { SecretKey get()throws Exception; }
    static final class Entry {
        final String id,mode,language,vocabulary;
        final long time,duration;
        final boolean turbo,emojis;
        String original="",text="",error="",trace="";
        boolean handed,speechCounted,recognitionCounted,editingCounted;
        EmojiStyle style=EmojiStyle.MODERATE;
        long recognitionMs;
        int partCount;
        final List<Part> parts=new ArrayList<>();
        record Part(String hash,String text){}
        String availableText(){return !original.isEmpty()?original:String.join("\n",parts.stream().map(Part::text).toList());}
        Entry(String id,long time,long duration,String mode,String language,String vocabulary,boolean turbo,boolean emojis){this.id=id;this.trace=id;this.time=time;this.duration=duration;this.mode=mode;this.language=language;this.vocabulary=vocabulary;this.turbo=turbo;this.emojis=emojis;}
    }
    private final File directory;
    private final Keys keys;
    RecoveryStore(Context context){this(new File(context.getNoBackupFilesDir(),"recovery"),RecoveryStore::platformKey);}
    RecoveryStore(File directory,Keys keys){this.directory=directory;this.keys=keys;synchronized(LOCK){prune(System.currentTimeMillis());}}
    private static SecretKey platformKey()throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        String alias="wisp_recovery_v1";
        if(store.containsAlias(alias))return (SecretKey)store.getKey(alias,null);
        KeyGenerator generator=KeyGenerator.getInstance("AES","AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
    private File path(String id,String suffix)throws IOException{
        if(!id.matches("[a-f0-9-]{36}"))throw new IOException("Invalid recovery id");
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Recovery unavailable");
        return new File(directory,id+suffix);
    }
    private void write(File file,byte[] plain)throws Exception{
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,keys.get());
        byte[] encrypted=cipher.doFinal(plain);AtomicFile atomic=new AtomicFile(file);FileOutputStream out=null;
        try{out=atomic.startWrite();out.write(cipher.getIV());out.write(encrypted);atomic.finishWrite(out);}
        catch(Exception e){if(out!=null)atomic.failWrite(out);throw e;}
    }
    private byte[] read(File file)throws Exception{
        byte[] bytes;try(var input=new AtomicFile(file).openRead();var buffer=new ByteArrayOutputStream()){byte[] block=new byte[8192];int count;while((count=input.read(block))!=-1)buffer.write(block,0,count);bytes=buffer.toByteArray();}
        if(bytes.length<28)throw new IOException("Invalid recovery data");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,keys.get(),new GCMParameterSpec(128,bytes,0,12));
        return cipher.doFinal(bytes,12,bytes.length-12);
    }
    Entry create(File audio,long time,long duration,String mode,SecureSettings settings)throws Exception{
        return create(audio,time,duration,mode,settings,"");
    }
    Entry create(File audio,long time,long duration,String mode,SecureSettings settings,String trace)throws Exception{
        synchronized(LOCK){Entry e=create(audio,time,duration,mode,settings.language(),settings.vocabulary(),settings.turbo(),settings.emojis());
            e.style=settings.emojiStyle();if(trace.matches("[a-f0-9-]{36}"))e.trace=trace;save(e);return e;}
    }
    Entry create(File audio,long time,long duration,String mode,String language,String vocabulary,boolean turbo,boolean emojis)throws Exception{
        synchronized(LOCK){
            prune(System.currentTimeMillis());
            if(!audio.isFile()||audio.length()<100||audio.length()>24*1024*1024)throw new IOException("Invalid audio");
            Entry entry=new Entry(UUID.randomUUID().toString(),time,duration,mode,language,vocabulary,turbo,emojis);
            write(path(entry.id,".audio"),Files.readAllBytes(audio.toPath()));
            try{save(entry);}catch(Exception e){delete(entry.id);throw e;}
            prune(System.currentTimeMillis());return entry;
        }
    }
    void save(Entry e)throws Exception{
        synchronized(LOCK){
            org.json.JSONArray parts=new org.json.JSONArray();for(var part:e.parts)parts.put(new JSONObject().put("hash",part.hash()).put("text",part.text()));
            JSONObject json=new JSONObject().put("id",e.id).put("time",e.time).put("duration",e.duration).put("mode",e.mode)
                .put("language",e.language).put("vocabulary",e.vocabulary).put("turbo",e.turbo).put("emojis",e.emojis)
                .put("trace",e.trace).put("original",e.original).put("text",e.text).put("error",e.error).put("handed",e.handed).put("style",e.style.code).put("parts",parts).put("partCount",e.partCount).put("recognitionMs",e.recognitionMs).put("speechCounted",e.speechCounted).put("recognitionCounted",e.recognitionCounted).put("editingCounted",e.editingCounted);
            write(path(e.id,".json"),json.toString().getBytes(StandardCharsets.UTF_8));
        }
    }
    Entry get(String id)throws Exception{
        synchronized(LOCK){
            JSONObject j=new JSONObject(new String(read(path(id,".json")),StandardCharsets.UTF_8));
            Entry e=new Entry(id,j.getLong("time"),j.getLong("duration"),j.getString("mode"),j.getString("language"),j.getString("vocabulary"),j.getBoolean("turbo"),j.getBoolean("emojis"));
            e.trace=j.optString("trace",e.id);e.original=j.optString("original");e.text=j.optString("text");e.error=j.optString("error");e.handed=j.optBoolean("handed");e.style=EmojiStyle.parse(j.optString("style"));e.partCount=j.optInt("partCount");e.recognitionMs=j.optLong("recognitionMs");e.speechCounted=j.optBoolean("speechCounted");e.recognitionCounted=j.optBoolean("recognitionCounted");e.editingCounted=j.optBoolean("editingCounted");
            var parts=j.optJSONArray("parts");if(parts!=null)for(int i=0;i<parts.length();i++){var part=parts.getJSONObject(i);e.parts.add(new Entry.Part(part.getString("hash"),part.getString("text")));}return e;
        }
    }
    List<Entry> list()throws Exception{
        synchronized(LOCK){prune(System.currentTimeMillis());List<Entry> entries=new ArrayList<>();
            File[] files=directory.listFiles((d,n)->n.endsWith(".json"));if(files!=null)for(File f:files)try{entries.add(get(f.getName().replace(".json","")));}catch(Exception ignored){/* Keep damaged/locked entries for explicit recovery; do not hide healthy ones. */}
            entries.sort(Comparator.comparingLong((Entry e)->e.time).reversed());return entries;
        }
    }
    private void prune(long now){
        File[] backups=directory.listFiles((d,n)->n.endsWith(".json.bak"));
        if(backups!=null)for(File backup:backups)try(var ignored=new AtomicFile(new File(directory,backup.getName().replace(".bak",""))).openRead()){/* Restore interrupted AtomicFile updates on older Android. */}catch(IOException ignored){}
        File[] metadata=directory.listFiles((d,n)->n.endsWith(".json"));
        if(metadata!=null){Arrays.sort(metadata,Comparator.comparingLong(File::lastModified).reversed());for(int i=0;i<metadata.length;i++)if(i>=MAX_ITEMS||now-metadata[i].lastModified()>MAX_AGE_MS)delete(metadata[i].getName().replace(".json",""));}
        // A crash between audio and metadata must not leave permanent orphan files.
        File[] audio=directory.listFiles((d,n)->n.endsWith(".audio"));if(audio!=null)for(File f:audio){String id=f.getName().replace(".audio","");if(!new File(directory,id+".json").exists())f.delete();}
    }
    File audio(Entry e,File cache)throws Exception{
        synchronized(LOCK){File f=File.createTempFile("recovery-",".m4a",cache);try{Files.write(f.toPath(),read(path(e.id,".audio")));return f;}catch(Exception ex){f.delete();throw ex;}}
    }
    void delete(String id){synchronized(LOCK){if(!id.matches("[a-f0-9-]{36}"))return;for(String suffix:new String[]{".json",".audio"})new AtomicFile(new File(directory,id+suffix)).delete();}}
}
