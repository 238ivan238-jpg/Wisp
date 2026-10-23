package app.wisp.voice;

import android.Manifest;
import android.os.*;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import java.util.concurrent.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class PauseAndPreflightTest {
    static void allow(Object service){ReflectionHelpers.setField(service,"preflight",(ConnectionCheck.Probe)api->{});}
    static void finishCheck(Object service)throws Exception{
        Future<?> request=ReflectionHelpers.getField(service,"request");if(request!=null)request.get(3,TimeUnit.SECONDS);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
    @Before public void setup(){var app=RuntimeEnvironment.getApplication();Shadows.shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO);app.getSharedPreferences("wisp_private",0).edit().putString("api_data","fixture-not-decryptable").commit();}
    @Test public void clockCountsOnlyActiveAudioIncludingMultiplePauses(){
        RecordingClock c=new RecordingClock();c.start(100);assertEquals(1000,c.elapsed(1100));c.pause(1100);assertEquals(1000,c.elapsed(250000));c.pause(300000);c.resume(310000);assertEquals(2000,c.elapsed(311000));c.pause(311000);assertEquals(2000,c.elapsed(900000));
    }
    @Test public void denialBeforeRecordingDoesNotAcquireRecorderAndIsBrief()throws Exception{
        var controller=Robolectric.buildService(WispImeService.class).create();try{
            var service=controller.get();service.onCreateInputView();EditorInfo info=new EditorInfo();info.inputType=InputType.TYPE_CLASS_TEXT;service.onStartInput(info,false);
            ReflectionHelpers.setField(service,"preflight",(ConnectionCheck.Probe)api->{throw new GroqClient.ApiException(403,"",false,"private response must not appear");});
            VoiceControls controls=ReflectionHelpers.getField(service,"mic");controls.getChildAt(0).performClick();assertNull(ReflectionHelpers.getField(service,"recorder"));assertFalse(controls.getChildAt(0).isEnabled());finishCheck(service);
            assertNull(ReflectionHelpers.getField(service,"recorder"));assertTrue(controls.getChildAt(0).isEnabled());TextView label=ReflectionHelpers.getField(service,"status");assertTrue(label.getText().toString().contains("403"));assertTrue(label.length()<100);
            assertFalse(new DiagnosticLog(service).report().contains("private response"));
        }finally{controller.destroy();}
    }
    @Test public void closingEditorWhilePreflightRunsCannotStartRecordingLater()throws Exception{
        var controller=Robolectric.buildService(WispImeService.class).create();try{
            var service=controller.get();service.onCreateInputView();EditorInfo info=new EditorInfo();info.inputType=InputType.TYPE_CLASS_TEXT;service.onStartInput(info,false);
            CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
            ReflectionHelpers.setField(service,"preflight",(ConnectionCheck.Probe)api->{entered.countDown();release.await();});
            VoiceControls controls=ReflectionHelpers.getField(service,"mic");controls.getChildAt(0).performClick();assertTrue(entered.await(2,TimeUnit.SECONDS));service.onFinishInput();release.countDown();Shadows.shadowOf(Looper.getMainLooper()).idle();assertNull(ReflectionHelpers.getField(service,"recorder"));
        }finally{controller.destroy();}
    }
    @Test public void lowerActionPausesAndResumesWithoutChangingTheChosenMode()throws Exception{
        var controller=Robolectric.buildService(WispImeService.class).create();try{
            var service=controller.get();service.onCreateInputView();allow(service);EditorInfo info=new EditorInfo();info.inputType=InputType.TYPE_CLASS_TEXT;service.onStartInput(info,false);
            VoiceControls controls=ReflectionHelpers.getField(service,"mic");TextView lower=(TextView)controls.getChildAt(1);lower.performClick();finishCheck(service);
            Object recorder=ReflectionHelpers.getField(service,"recorder");assertNotNull(recorder);SystemClock.sleep(1000);lower.performClick();
            RecordingClock clock=ReflectionHelpers.getField(service,"clock");assertTrue(clock.paused());long duration=clock.elapsed(SystemClock.elapsedRealtime());SystemClock.sleep(10000);assertEquals(duration,clock.elapsed(SystemClock.elapsedRealtime()));
            assertEquals("▶",lower.getText().toString());assertEquals("translate",ReflectionHelpers.getField(service,"activeMode"));lower.performClick();assertFalse(clock.paused());assertSame(recorder,ReflectionHelpers.getField(service,"recorder"));assertTrue(lower.getContentDescription().toString().contains("Приостановить"));
        }finally{controller.destroy();}
    }
}
