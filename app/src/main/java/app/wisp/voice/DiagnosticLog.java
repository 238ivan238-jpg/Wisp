package app.wisp.voice;

import android.content.*;
import android.os.Build;
import androidx.core.content.FileProvider;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/** Strictly typed journal. Never accepts dictation, headers, URLs, request bodies or exception messages. */
final class DiagnosticLog {
    static final int MAX_EVENTS=200;
    enum Stage {PREFLIGHT,RECOVERY,PAUSE,RECORDING,TRANSCRIPTION,CLEANUP,TRANSLATION,INSERTION,LIFECYCLE,KEY_CHECK,KEY_STORAGE,MODEL_LIST,UPDATE_CHECK,UPDATE_DOWNLOAD,UPDATE_VERIFY,UPDATE_INSTALL,EXPORT,APP_CRASH,OVERLAY}
    enum Outcome {VPN_ACTIVE,VPN_INACTIVE,RESUMED,START,SUCCESS,FAILURE,CANCELLED,UNCHANGED,FALLBACK,PERMISSION_REQUIRED,HANDED_TO_ANDROID,UNCONFIRMED,CONTEXT_UNAVAILABLE}
    private static final Object LOCK=new Object();
    private final Context context;
    private final SharedPreferences prefs;
    private final String version;
    DiagnosticLog(Context context){
        this.context=context.getApplicationContext();prefs=this.context.getSharedPreferences("wisp_diagnostics",Context.MODE_PRIVATE);
        String name="unknown";try{name=this.context.getPackageManager().getPackageInfo(this.context.getPackageName(),0).versionName;}catch(Exception ignored){}
        version=name!=null&&name.matches("[0-9A-Za-z.+-]{1,40}")?name:"unknown";
    }
    static String session(){return UUID.randomUUID().toString();}
    private JSONArray read(){try{return new JSONArray(prefs.getString("events","[]"));}catch(Exception ignored){return new JSONArray();}}
    void event(String session,Stage stage,Outcome outcome,String mode,long elapsedMs,long inputWords,long outputWords){write(session,stage,outcome,mode,"",elapsedMs,inputWords,outputWords,0,null,false);}
    void request(String session,Stage stage,Outcome outcome,String mode,String model,long elapsedMs,int status,Throwable error){write(session,stage,outcome,mode,model,elapsedMs,-1,-1,status,error,false);}
    void result(String session,Stage stage,Outcome outcome,String mode,String model,long inputWords,long outputWords){write(session,stage,outcome,mode,model,0,inputWords,outputWords,0,null,false);}
    void failure(String session,Stage stage,String mode,Throwable error){write(session,stage,Outcome.FAILURE,mode,"",0,-1,-1,0,error,false);}
    void crash(Throwable error){write(session(),Stage.APP_CRASH,Outcome.FAILURE,"unknown","",0,-1,-1,0,error,true);}
    private void write(String session,Stage stage,Outcome outcome,String mode,String model,long elapsedMs,long inputWords,long outputWords,int status,Throwable error,boolean synchronous){
        synchronized(LOCK){try{
            JSONObject entry=new JSONObject().put("timeUtc",Instant.now().toString()).put("appVersion",version).put("stage",stage.name()).put("outcome",outcome.name());
            if(session!=null&&session.matches("[a-f0-9-]{36}"))entry.put("session",session);
            entry.put("mode",List.of("clean","raw","translate").contains(mode)?mode:"unknown");
            if(GroqClient.CLEANERS.contains(model)||List.of(GroqClient.WHISPER,"whisper-large-v3-turbo").contains(model))entry.put("model",model);
            entry.put("elapsedMs",Math.max(0,elapsedMs));if(inputWords>=0)entry.put("inputWords",inputWords);if(outputWords>=0)entry.put("outputWords",outputWords);
            if(error instanceof GroqClient.ApiException api){if(api.status>0)status=api.status;String code=api.providerCode;
                if(!code.isEmpty())entry.put("providerCode",Set.of("invalid_api_key","rate_limit_exceeded","model_not_found","model_decommissioned","model_permission_blocked","model_access_denied","invalid_request_error","insufficient_quota","tokens","requests").contains(code)?code:"other");
            }
            if(error instanceof UpdateClient.HttpException http)status=http.status;
            if(status>=100&&status<=599)entry.put("httpStatus",status);
            if(error!=null){
                JSONArray causes=new JSONArray();Throwable current=error;
                for(int depth=0;current!=null&&depth<3;depth++,current=current.getCause()){
                    JSONObject cause=new JSONObject().put("type",safeClass(current.getClass().getName()));JSONArray frames=new JSONArray();
                    for(StackTraceElement frame:current.getStackTrace()){
                        if(!frame.getClassName().startsWith("app.wisp.voice."))continue;
                        String file=frame.getFileName();if(file==null||!file.matches("[A-Za-z0-9_.-]{1,80}"))file="unknown";
                        frames.put(safeClass(frame.getClassName())+"."+safeClass(frame.getMethodName())+"("+file+":"+frame.getLineNumber()+")");if(frames.length()==8)break;
                    }
                    cause.put("frames",frames);causes.put(cause);
                }
                entry.put("exceptions",causes);
            }
            JSONArray previous=read(),next=new JSONArray();for(int i=Math.max(0,previous.length()-MAX_EVENTS+1);i<previous.length();i++)next.put(previous.get(i));next.put(entry);
            SharedPreferences.Editor editor=prefs.edit().putString("events",next.toString());if(synchronous)editor.commit();else editor.apply();
        }catch(Exception ignored){/* Logging must never break input or replace the original failure. */}}
    }
    private static String safeClass(String value){return value!=null&&value.matches("[A-Za-z0-9_.$<>]{1,180}")?value:"unknown";}
    String report()throws Exception{
        synchronized(LOCK){
            return new JSONObject().put("reportVersion",1).put("exportedAtUtc",Instant.now().toString()).put("appVersion",version)
                .put("androidSdk",Build.VERSION.SDK_INT).put("events",read()).toString(2);
        }
    }
    JSONArray events(){synchronized(LOCK){return read();}}
    private File directory(){return new File(context.getCacheDir(),"diagnostics");}
    File export()throws Exception{
        File dir=directory();if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Cannot create diagnostics directory");
        File[] files=dir.listFiles((d,name)->name.endsWith(".json"));
        if(files!=null){Arrays.sort(files,Comparator.comparingLong(File::lastModified).reversed());for(int i=4;i<files.length;i++)files[i].delete();}
        File file=File.createTempFile("Wisp-diagnostics-",".json",dir);
        try(OutputStream out=new FileOutputStream(file)){out.write(report().getBytes(StandardCharsets.UTF_8));}return file;
    }
    static Intent shareIntent(Context context,File file){
        android.net.Uri uri=FileProvider.getUriForFile(context,context.getPackageName()+".diagnostics",file);
        Intent intent=new Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClipData(ClipData.newRawUri("Wisp diagnostics",uri));return intent;
    }
    void clear(){synchronized(LOCK){prefs.edit().clear().apply();File[] files=directory().listFiles();if(files!=null)for(File file:files)file.delete();}}
}
