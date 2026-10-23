package app.wisp.voice;

import android.content.Context;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import javax.crypto.KeyGenerator;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class RecoveryTest {
    private Context context;
    private File dir,audio;
    private RecoveryStore store;
    @Before public void setup()throws Exception{
        context=RuntimeEnvironment.getApplication();dir=Files.createTempDirectory("wisp-recovery-test").toFile();
        var key=KeyGenerator.getInstance("AES").generateKey();store=new RecoveryStore(dir,()->key);
        audio=new File(context.getCacheDir(),"fixture-audio.m4a");Files.write(audio.toPath(),new byte[256]);
    }
    private RecoveryStore.Entry entry(String mode)throws Exception{return store.create(audio,System.currentTimeMillis(),2300,mode,"auto","GitHub",false,true);}
    @Test public void encryptedAudioAndTextSurviveReopeningAndDeleteTogether()throws Exception{
        var e=entry("clean");e.original="секретный исходный текст";e.text="Готовый текст 🙏";e.error="access";store.save(e);
        String disk=new String(Files.readAllBytes(new File(dir,e.id+".json").toPath()),java.nio.charset.StandardCharsets.UTF_8);
        assertFalse(disk.contains(e.original));assertFalse(disk.contains(e.text));assertFalse(disk.contains("GitHub"));
        var restored=store.get(e.id);assertEquals(e.original,restored.original);assertEquals(e.text,restored.text);assertEquals("access",restored.error);
        File copy=store.audio(restored,context.getCacheDir());assertArrayEquals(Files.readAllBytes(audio.toPath()),Files.readAllBytes(copy.toPath()));copy.delete();
        store.delete(e.id);assertTrue(store.list().isEmpty());assertEquals(0,Objects.requireNonNull(dir.list()).length);
    }
    @Test public void tamperingFailsAuthenticationInsteadOfReturningCorruptedText()throws Exception{
        var e=entry("clean");File f=new File(dir,e.id+".json");byte[] data=Files.readAllBytes(f.toPath());data[data.length-1]^=1;Files.write(f.toPath(),data);
        assertThrows(javax.crypto.AEADBadTagException.class,()->store.get(e.id));
    }
    @Test public void retentionIsBoundedAndExpiredItemsAreDeleted()throws Exception{
        for(int i=0;i<13;i++){entry("raw");Thread.sleep(2);}
        assertEquals(10,store.list().size());var old=store.list().get(0);
        assertTrue(new File(dir,old.id+".json").setLastModified(System.currentTimeMillis()-RecoveryStore.MAX_AGE_MS-1000));
        assertEquals(9,store.list().size());assertFalse(new File(dir,old.id+".audio").exists());
    }
    @Test public void deniedUploadRetainsAudioAndRetryUploadsTheSameRecording()throws Exception{
        var e=entry("raw");List<String> calls=new ArrayList<>();
        var denied=new GroqClient((path,method,key,type,body)->{calls.add(path);return new GroqClient.Response(403,"{}");});
        assertThrows(GroqClient.ApiException.class,()->RecoveryJob.run(store,e,context.getCacheDir(),denied,"fixture",s->{}));
        assertTrue(store.get(e.id).original.isEmpty());assertTrue(new File(dir,e.id+".audio").isFile());
        var ready=new GroqClient((path,method,key,type,body)->{calls.add(path);return new GroqClient.Response(200,"{\"text\":\"Привет\"}");});
        var result=RecoveryJob.run(store,store.get(e.id),context.getCacheDir(),ready,"fixture",s->{});
        assertEquals("Привет",result.text());assertEquals(List.of("/audio/transcriptions","/audio/transcriptions"),calls);
        assertEquals("Привет",store.get(e.id).text);
    }
    @Test public void cleanupRetryUsesCheckpointWithoutUploadingAudioAgainAndFinishedTextNeedsNoNetwork()throws Exception{
        var e=entry("clean");List<String> calls=new ArrayList<>();
        GroqClient first=new GroqClient((path,method,key,type,body)->{calls.add(path);return path.contains("audio")?new GroqClient.Response(200,"{\"text\":\"ну привет\"}"):new GroqClient.Response(403,"{}");});
        var fallback=RecoveryJob.run(store,e,context.getCacheDir(),first,"fixture",s->{});assertEquals("access",fallback.errorCategory());assertEquals("ну привет",store.get(e.id).original);
        GroqClient next=new GroqClient((path,method,key,type,body)->{calls.add(path);return new GroqClient.Response(200,"{\"choices\":[{\"message\":{\"content\":\"Привет!\"},\"finish_reason\":\"stop\"}]}");});
        var fixed=RecoveryJob.run(store,store.get(e.id),context.getCacheDir(),next,"fixture",s->{});assertEquals("Привет!",fixed.text());
        assertEquals(List.of("/audio/transcriptions","/chat/completions","/chat/completions"),calls);
        GroqClient offline=new GroqClient((p,m,k,t,b)->{throw new AssertionError("Cached text must not need network");});
        assertEquals("Привет!",RecoveryJob.run(store,store.get(e.id),context.getCacheDir(),offline,"",s->{}).text());
        assertFalse(new DiagnosticLog(context).report().contains("ну привет"));
    }
    @Test public void interruptedProcessingStillLeavesTheOriginalCheckpoint()throws Exception{
        var e=entry("clean");GroqClient api=new GroqClient((p,m,k,t,b)->new GroqClient.Response(200,"{\"text\":\"Длинная личная запись\"}"));
        try{assertThrows(java.io.InterruptedIOException.class,()->RecoveryJob.run(store,e,context.getCacheDir(),api,"fixture",s->Thread.currentThread().interrupt()));}
        finally{Thread.interrupted();}
        assertEquals("Длинная личная запись",store.get(e.id).original);
    }
}
