package app.wisp.voice;

import android.Manifest;
import android.accessibilityservice.InputMethod;
import android.app.KeyguardManager;
import android.os.Binder;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;
import static org.junit.Assert.*;

/** Exercises Android's actual accessibility input wrapper with an unreadable editor. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class UnreadableEditorTest {
    private static final class Editor implements AutoCloseable {
        final ServiceController<GboardCompanionService> controller;
        final GboardCompanionService service;
        final InputMethod input;
        int reads,commits;
        Runnable checkCommit;
        String committed="";
        Editor()throws Exception{
            var app=RuntimeEnvironment.getApplication();Shadows.shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO);
            app.getSharedPreferences("wisp_private",0).edit().putString("api_data","fixture-not-a-decryptable-key").commit();
            controller=Robolectric.buildService(GboardCompanionService.class).create();service=controller.get();PauseAndPreflightTest.allow(service);
            Shadows.shadowOf(service.getSystemService(KeyguardManager.class)).setKeyguardLocked(false);
            android.provider.Settings.Secure.putString(service.getContentResolver(),android.provider.Settings.Secure.DEFAULT_INPUT_METHOD,"com.google.android.inputmethod.latin/.LatinIME");
            input=service.onCreateInputMethod();ReflectionHelpers.setField(service,"mInputMethod",input);
            Class<?> remoteInterface=Class.forName("com.android.internal.inputmethod.IRemoteAccessibilityInputConnection");
            Object remote=Proxy.newProxyInstance(remoteInterface.getClassLoader(),new Class<?>[]{remoteInterface},(proxy,method,args)->{
                if(method.getName().equals("asBinder"))return new Binder();
                if(method.getName().equals("getSurroundingText")){reads++;((CompletableFuture<?>)args[args.length-1]).complete(null);}
                if(method.getName().equals("commitText")){if(checkCommit!=null)checkCommit.run();commits++;committed=args[1].toString();assertEquals(1,args[2]);}
                return null;
            });
            Class<?> group=Class.forName("com.android.internal.inputmethod.CancellationGroup");
            Object connection=Class.forName("com.android.internal.inputmethod.RemoteAccessibilityInputConnection").getConstructor(remoteInterface,group).newInstance(remote,group.getConstructor().newInstance());
            ReflectionHelpers.setField(input,"mStartedInputConnection",connection);
            service.onServiceConnected();EditorInfo info=new EditorInfo();info.inputType=InputType.TYPE_CLASS_TEXT;info.packageName="example.custom.editor";input.onStartInput(info,false);
        }
        void deliver(DictationPipeline.Result result)throws Exception{
            var method=GboardCompanionService.class.getDeclaredMethod("deliver",DictationPipeline.Result.class,long.class,String.class,String.class);method.setAccessible(true);
            method.invoke(service,result,System.currentTimeMillis(),"translate",DiagnosticLog.session());
        }
        public void close(){controller.destroy();}
    }
    @Test public void neitherRecordingActionRequiresReadingSurroundingText()throws Exception{
        for(boolean english:new boolean[]{false,true})try(var editor=new Editor()){
            VoiceControls controls=ReflectionHelpers.getField(editor.service,"bubble");controls.getChildAt(english?1:0).performClick();PauseAndPreflightTest.finishCheck(editor.service);
            assertNotNull(ReflectionHelpers.getField(editor.service,"recorder"));assertEquals(0,editor.reads);
            assertEquals(english?"translate":"clean",ReflectionHelpers.getField(editor.service,"mode"));
        }
    }
    @Test public void actualAccessibilityWrapperSendsOnceAndPreservesWarningsWithoutClaimingConfirmedWords()throws Exception{
        try(var editor=new Editor()){
            String text="Tasks:\n- Review the API. 🙏\n- Keep the budget.";
            String warning="Перевод не удался. Сохранён исходный текст.";
            editor.deliver(new DictationPipeline.Result(text,text,warning));
            assertEquals(text,editor.committed);assertEquals(1,editor.commits);assertEquals(1,editor.reads);
            assertEquals(0,new UsageStats(editor.service).snapshot().words());
            TextView label=ReflectionHelpers.getField(editor.service,"label");assertEquals(warning,label.getText().toString());
            String report=new DiagnosticLog(editor.service).report();assertTrue(report.contains("CONTEXT_UNAVAILABLE"));assertTrue(report.contains("UNCONFIRMED"));assertFalse(report.contains(text));
        }
    }
    @Test public void aClosedEditorStillPreventsAnyInsertion()throws Exception{
        try(var editor=new Editor()){
            editor.input.onFinishInput();editor.deliver(new DictationPipeline.Result("private","private",""));assertEquals(0,editor.commits);
        }
    }
    @Test public void companionPauseFreezesTimeAndKeepsTranslationUntilResumed()throws Exception{
        try(var editor=new Editor()){
            VoiceControls controls=ReflectionHelpers.getField(editor.service,"bubble");TextView lower=(TextView)controls.getChildAt(1);
            lower.performClick();PauseAndPreflightTest.finishCheck(editor.service);assertNotNull(ReflectionHelpers.getField(editor.service,"recorder"));
            android.os.SystemClock.sleep(1000);lower.performClick();RecordingClock clock=ReflectionHelpers.getField(editor.service,"clock");assertTrue(clock.paused());
            long duration=clock.elapsed(android.os.SystemClock.elapsedRealtime());android.os.SystemClock.sleep(10000);assertEquals(duration,clock.elapsed(android.os.SystemClock.elapsedRealtime()));assertEquals("▶",lower.getText().toString());
            lower.performClick();assertFalse(clock.paused());assertEquals("translate",ReflectionHelpers.getField(editor.service,"mode"));assertEquals(0,editor.commits);
        }
    }
    @Test public void aSavedUnconfirmedCommitNeverOffersBlindAutomaticInsertionAgain()throws Exception{
        try(var editor=new Editor()){
            var key=javax.crypto.KeyGenerator.getInstance("AES").generateKey();
            RecoveryStore store=new RecoveryStore(java.nio.file.Files.createTempDirectory("companion-recovery").toFile(),()->key);
            java.io.File audio=new java.io.File(editor.service.getCacheDir(),"fixture.m4a");java.nio.file.Files.write(audio.toPath(),new byte[128]);
            var entry=store.create(audio,System.currentTimeMillis(),1500,"clean","auto","",false,false);entry.original="Текст";entry.text="Текст.";store.save(entry);
            ReflectionHelpers.setField(editor.service,"recovery",store);ReflectionHelpers.setField(editor.service,"draft",entry);
            editor.deliver(new DictationPipeline.Result(entry.original,entry.text,""));assertEquals(1,editor.commits);assertTrue(store.get(entry.id).handed);
            VoiceControls controls=ReflectionHelpers.getField(editor.service,"bubble");assertEquals("EN",((TextView)controls.getChildAt(1)).getText().toString());assertNull(ReflectionHelpers.getField(editor.service,"draft"));
            assertEquals(1,store.list().size());assertEquals(0,new UsageStats(editor.service).snapshot().words());
        }
    }

    @Test public void retryOfReadyTextUsesNoNetworkAndCommitsOnlyOnce()throws Exception{
        try(var editor=new Editor()){
            var key=javax.crypto.KeyGenerator.getInstance("AES").generateKey();RecoveryStore store=new RecoveryStore(java.nio.file.Files.createTempDirectory("ready-recovery").toFile(),()->key);
            java.io.File audio=new java.io.File(editor.service.getCacheDir(),"fixture.m4a");java.nio.file.Files.write(audio.toPath(),new byte[128]);
            var entry=store.create(audio,System.currentTimeMillis(),1500,"clean","auto","",false,false);entry.original="ну привет";entry.text="Привет!";store.save(entry);
            ReflectionHelpers.setField(editor.service,"recovery",store);ReflectionHelpers.setField(editor.service,"draft",entry);
            VoiceControls controls=ReflectionHelpers.getField(editor.service,"bubble");controls.setRetry(true);assertEquals("↻",((TextView)controls.getChildAt(1)).getText().toString());
            controls.getChildAt(1).performClick();PauseAndPreflightTest.finishCheck(editor.service);
            assertEquals("Привет!",editor.committed);assertEquals(1,editor.commits);assertTrue(store.get(entry.id).handed);assertEquals("EN",((TextView)controls.getChildAt(1)).getText().toString());
        }
    }

    @Test @Config(shadows=VoiceMeterTest.LevelRecorder.class) public void companionFeedsTheLiveLevelAndStopsMeteringWhilePaused()throws Exception{
        VoiceMeterTest.LevelRecorder.amplitude=0;VoiceMeterTest.LevelRecorder.reads=0;VoiceMeterTest.LevelRecorder.fail=false;
        try(var editor=new Editor()){
            VoiceControls controls=ReflectionHelpers.getField(editor.service,"bubble");controls.getChildAt(1).performClick();PauseAndPreflightTest.finishCheck(editor.service);
            VoiceMeterTest.LevelRecorder.amplitude=16000;Runnable tick=ReflectionHelpers.getField(editor.service,"tick");tick.run();
            VoiceLevel meter=ReflectionHelpers.getField(controls.getChildAt(0),"meter");meter.snap();assertTrue(meter.level(1)>.9f);
            controls.getChildAt(1).performClick();int reads=VoiceMeterTest.LevelRecorder.reads;tick.run();assertEquals(reads,VoiceMeterTest.LevelRecorder.reads);assertEquals(0,meter.level(1),0);
            controls.getChildAt(1).performClick();VoiceMeterTest.LevelRecorder.fail=true;tick.run();assertNotNull(ReflectionHelpers.getField(editor.service,"recorder"));assertEquals(0,editor.commits);
        }
    }

    @Test public void headsetControlCannotStartRecordingInAPasswordField()throws Exception{
        try(var editor=new Editor()){
            new SecureSettings(editor.service).setHeadsetButtons(true);editor.service.changed();org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            HeadsetButtons buttons=ReflectionHelpers.getField(editor.service,"headset");assertTrue(buttons.handle(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_HEADSETHOOK)));PauseAndPreflightTest.finishCheck(editor.service);assertNotNull(ReflectionHelpers.getField(editor.service,"recorder"));
            EditorInfo password=new EditorInfo();password.inputType=InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD;editor.input.onStartInput(password,false);assertFalse(buttons.handle(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_HEADSETHOOK)));assertNull(ReflectionHelpers.getField(editor.service,"recorder"));
        }
    }

    @Test public void controlsStayDisabledUntilTheActualInsertionFinishes()throws Exception{
        try(var editor=new Editor()){
            VoiceControls controls=ReflectionHelpers.getField(editor.service,"bubble");boolean[] checked={false};editor.checkCommit=()->{assertFalse(controls.getChildAt(0).isEnabled());assertTrue(controls.getChildAt(0).getContentDescription().toString().contains("Вставляю"));checked[0]=true;};
            editor.deliver(new DictationPipeline.Result("Text","Text.",""));assertTrue(checked[0]);assertTrue(controls.getChildAt(0).isEnabled());
        }
    }

}
