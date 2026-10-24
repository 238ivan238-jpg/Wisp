package app.wisp.voice;

import android.Manifest;
import android.media.MediaRecorder;
import android.os.SystemClock;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowMediaRecorder;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35,shadows=VoiceMeterTest.LevelRecorder.class)
public class VoiceMeterTest {
    @Implements(MediaRecorder.class)
    public static class LevelRecorder extends ShadowMediaRecorder {
        static int amplitude,reads;
        static boolean fail;
        @Implementation protected int getMaxAmplitude(){reads++;if(fail)throw new IllegalStateException("Meter unavailable");return amplitude;}
    }
    @Before public void setup(){LevelRecorder.amplitude=0;LevelRecorder.reads=0;LevelRecorder.fail=false;}
    @Test public void pausedAndMissingRecordersAreNeverPolledAndReadErrorsAreVisualOnly(){
        MediaRecorder recorder=new MediaRecorder(RuntimeEnvironment.getApplication());LevelRecorder.amplitude=9000;
        assertEquals(0,RecorderLevel.read(null,false));assertEquals(0,RecorderLevel.read(recorder,true));assertEquals(0,LevelRecorder.reads);
        assertEquals(9000,RecorderLevel.read(recorder,false));LevelRecorder.fail=true;assertEquals(0,RecorderLevel.read(recorder,false));recorder.release();
    }
    @Test public void viewClearsItsMeterOnPauseProcessingAndANewRecording(){
        VoiceBubble bubble=new VoiceBubble(RuntimeEnvironment.getApplication());bubble.setState(VoiceBubble.RECORDING);bubble.setAmplitude(12000);
        VoiceLevel meter=ReflectionHelpers.getField(bubble,"meter");meter.snap();assertTrue(meter.level(1)>.9f);
        bubble.setState(VoiceBubble.PAUSED);bubble.setAmplitude(20000);assertEquals(0,meter.level(1),0);assertTrue(bubble.getContentDescription().toString().contains("паузе"));
        bubble.setState(VoiceBubble.RECORDING);assertEquals(0,meter.level(1),0);bubble.setAmplitude(20000);meter.snap();assertTrue(meter.level(1)>.9f);
        bubble.setState(VoiceBubble.WORKING);assertEquals(0,meter.level(1),0);bubble.setState(VoiceBubble.IDLE);assertEquals(0,meter.level(1),0);
    }
    @Test public void legacyServiceFeedsActualRecorderSamplesAndMeterFailureDoesNotStopAudio()throws Exception{
        var app=RuntimeEnvironment.getApplication();Shadows.shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO);app.getSharedPreferences("wisp_private",0).edit().putString("api_data","fixture-not-decryptable").commit();
        var controller=Robolectric.buildService(WispImeService.class).create();try{
            var service=controller.get();service.onCreateInputView();PauseAndPreflightTest.allow(service);EditorInfo info=new EditorInfo();info.inputType=InputType.TYPE_CLASS_TEXT;service.onStartInput(info,false);
            VoiceControls controls=ReflectionHelpers.getField(service,"mic");controls.getChildAt(0).performClick();PauseAndPreflightTest.finishCheck(service);
            MediaRecorder recorder=ReflectionHelpers.getField(service,"recorder");assertNotNull(recorder);LevelRecorder.amplitude=12000;Runnable tick=ReflectionHelpers.getField(service,"tick");tick.run();
            VoiceLevel meter=ReflectionHelpers.getField(controls.getChildAt(0),"meter");meter.snap();assertTrue(meter.level(1)>.9f);assertTrue(LevelRecorder.reads>0);
            controls.getChildAt(1).performClick();int reads=LevelRecorder.reads;SystemClock.sleep(200);tick.run();assertEquals(reads,LevelRecorder.reads);assertEquals(0,meter.level(1),0);
            controls.getChildAt(1).performClick();LevelRecorder.fail=true;tick.run();assertSame(recorder,ReflectionHelpers.getField(service,"recorder"));
        }finally{controller.destroy();}
    }
}
