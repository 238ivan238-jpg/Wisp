package app.wisp.voice;

import android.content.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class DiagnosticLogTest {
    private DiagnosticLog log;
    @Before public void setup(){var c=RuntimeEnvironment.getApplication();c.getSharedPreferences("wisp_updates",0).edit().putBoolean("auto_check",false).commit();log=new DiagnosticLog(c);log.clear();java.util.Map<?,?> paths=ReflectionHelpers.getStaticField(androidx.core.content.FileProvider.class,"sCache");paths.clear();}
    @Test public void exceptionMessagesKeysTranscriptsAndArbitraryCodesAreNotStored()throws Exception{
        String secret="secret credential and personal dictated words";
        Exception nested=new IOException(secret);Exception error=new GroqClient.ApiException(401,"sensitive_code_value",false,secret);error.initCause(nested);
        log.failure(DiagnosticLog.session(),DiagnosticLog.Stage.CLEANUP,"clean",error);String report=log.report();
        assertFalse(report.contains(secret));assertFalse(report.contains("sensitive_code_value"));assertTrue(report.contains("httpStatus"));assertTrue(report.contains("DiagnosticLogTest.java"));assertTrue(report.contains("java.io.IOException"));
    }
    @Test public void journalIsBoundedAndPersistsSessionAndVersion()throws Exception{
        String session=DiagnosticLog.session();for(int i=0;i<230;i++)log.event(session,DiagnosticLog.Stage.RECORDING,DiagnosticLog.Outcome.SUCCESS,"clean",i,-1,-1);
        JSONArray events=new JSONObject(new DiagnosticLog(RuntimeEnvironment.getApplication()).report()).getJSONArray("events");assertEquals(200,events.length());assertEquals(30,events.getJSONObject(0).getLong("elapsedMs"));assertEquals(session,events.getJSONObject(199).getString("session"));assertTrue(events.getJSONObject(199).has("appVersion"));
    }
    @Test public void actualClientFailureLogsStageStatusAndNeverRequestOrResponseBody()throws Exception{
        GroqClient client=new GroqClient((p,m,k,t,b)->new GroqClient.Response(429,"{\"error\":{\"code\":\"rate_limit_exceeded\",\"message\":\"private provider body\"}}"),log,DiagnosticLog.session(),"clean");
        assertThrows(GroqClient.ApiException.class,()->client.clean("private dictated phrase","synthetic-key"));String report=log.report();
        assertFalse(report.contains("synthetic-key"));assertFalse(report.contains("private dictated phrase"));assertFalse(report.contains("private provider body"));
        JSONArray entries=new JSONObject(report).getJSONArray("events");JSONObject failure=entries.getJSONObject(entries.length()-1);assertEquals("CLEANUP",failure.getString("stage"));assertEquals(429,failure.getInt("httpStatus"));assertEquals("rate_limit_exceeded",failure.getString("providerCode"));
    }
    @Test public void unchangedSuccessfulEditingIsVisibleWithoutKeepingText()throws Exception{
        File audio=File.createTempFile("wisp-diagnostic-test-",".m4a");audio.deleteOnExit();Files.write(audio.toPath(),new byte[256]);String original="Secret sentence.";
        GroqClient client=new GroqClient((p,m,k,t,b)->new GroqClient.Response(200,p.equals("/audio/transcriptions")?new JSONObject().put("text",original).toString():new JSONObject().put("choices",new JSONArray().put(new JSONObject().put("message",new JSONObject().put("content",original)))).toString()),log,DiagnosticLog.session(),"clean");
        DictationPipeline.run(client,audio,"key","auto",false,"","clean",s->{});String report=log.report();assertTrue(report.contains("UNCHANGED"));assertFalse(report.contains(original));assertTrue(report.contains("inputWords"));assertTrue(report.contains("outputWords"));
    }
    @Test public void exportedFileUsesReadOnlyUriAndProviderCannotExposeOtherFiles()throws Exception{
        var context=RuntimeEnvironment.getApplication();log.event(DiagnosticLog.session(),DiagnosticLog.Stage.INSERTION,DiagnosticLog.Outcome.SUCCESS,"clean",0,1,1);File file=log.export();assertEquals(1,new JSONObject(new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8)).getJSONArray("events").length());
        Intent share=DiagnosticLog.shareIntent(context,file);assertEquals(Intent.ACTION_SEND,share.getAction());assertEquals("application/json",share.getType());assertTrue((share.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0);assertEquals(0,share.getFlags()&Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        assertEquals("app.wisp.voice.diagnostics",share.getClipData().getItemAt(0).getUri().getAuthority());File other=new File(context.getCacheDir(),"private-file");Files.write(other.toPath(),"private".getBytes(StandardCharsets.UTF_8));assertThrows(IllegalArgumentException.class,()->DiagnosticLog.shareIntent(context,other));
    }
    @Test public void exportKeepsOnlyFiveTemporaryReportsAndClearDoesNotEraseUsageStats()throws Exception{
        for(int i=0;i<8;i++)log.export();File dir=new File(RuntimeEnvironment.getApplication().getCacheDir(),"diagnostics");assertEquals(5,dir.listFiles().length);
        UsageStats stats=new UsageStats(RuntimeEnvironment.getApplication());stats.recordUse(System.currentTimeMillis(),1000);log.clear();assertEquals(0,log.events().length());assertEquals(0,dir.listFiles().length);assertEquals(1,stats.snapshot().sessions());
    }
    @Test public void crashJournalIsWrittenBeforeDelegatingToPreviousHandler()throws Exception{
        Thread.UncaughtExceptionHandler baseline=Thread.getDefaultUncaughtExceptionHandler();AtomicBoolean delegated=new AtomicBoolean();
        Thread.setDefaultUncaughtExceptionHandler((t,e)->{delegated.set(true);assertTrue(log.events().toString().contains("APP_CRASH"));});
        WispApplication app=new WispApplication();ReflectionHelpers.callInstanceMethod(app,"attach",ReflectionHelpers.ClassParameter.from(Context.class,RuntimeEnvironment.getApplication()));app.onCreate();try{
            Thread.getDefaultUncaughtExceptionHandler().uncaughtException(Thread.currentThread(),new IllegalStateException("private crash details"));assertTrue(delegated.get());assertFalse(log.report().contains("private crash details"));
        }finally{app.onTerminate();Thread.setDefaultUncaughtExceptionHandler(baseline);}
    }
    private android.widget.TextView find(android.view.View v,String text){if(v instanceof android.widget.TextView t&&text.contentEquals(t.getText()))return t;if(v instanceof android.view.ViewGroup g)for(int i=0;i<g.getChildCount();i++){var t=find(g.getChildAt(i),text);if(t!=null)return t;}return null;}
    @Test public void diagnosticScreenSharesFileThroughAndroidChooser(){
        try(var a=Robolectric.buildActivity(DiagnosticsActivity.class).setup()){
            find(a.get().getWindow().getDecorView(),"Поделиться логами").performClick();Intent chooser=Shadows.shadowOf(a.get()).getNextStartedActivity();assertNotNull(chooser);assertEquals(Intent.ACTION_CHOOSER,chooser.getAction());
            Intent share=chooser.getParcelableExtra(Intent.EXTRA_INTENT);assertEquals(Intent.ACTION_SEND,share.getAction());assertEquals("application/json",share.getType());assertEquals("app.wisp.voice.diagnostics",share.getClipData().getItemAt(0).getUri().getAuthority());
        }
    }
    @Test public void diagnosticScreenCanSaveViaSystemDocumentPicker(){
        try(var a=Robolectric.buildActivity(DiagnosticsActivity.class).setup()){
            find(a.get().getWindow().getDecorView(),"Сохранить файл логов").performClick();Intent save=Shadows.shadowOf(a.get()).getNextStartedActivityForResult().intent;
            assertEquals(Intent.ACTION_CREATE_DOCUMENT,save.getAction());assertEquals("application/json",save.getType());assertTrue(save.hasCategory(Intent.CATEGORY_OPENABLE));
        }
    }
}
