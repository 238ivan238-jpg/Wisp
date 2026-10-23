package app.wisp.voice;

import android.Manifest;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.TextView;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import java.time.Duration;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class QuickTranslationTest {
    @Before public void setup(){
        var app=RuntimeEnvironment.getApplication();Shadows.shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO);
        // A marker lets recording start; it is not a decryptable key, so no real network call is possible.
        app.getSharedPreferences("wisp_private",0).edit().clear().putString("api_data","fixture-not-an-encrypted-key").commit();
    }
    private TextView english(View view){
        if(view instanceof TextView text&&"EN".contentEquals(text.getText()))return text;
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){TextView result=english(group.getChildAt(i));if(result!=null)return result;}return null;
    }
    private VoiceBubble microphone(View view){
        if(view instanceof VoiceBubble bubble)return bubble;
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){VoiceBubble result=microphone(group.getChildAt(i));if(result!=null)return result;}return null;
    }
    private void connect(WispImeService service){
        EditorInfo info=new EditorInfo();info.inputType=InputType.TYPE_CLASS_TEXT;service.onStartInput(info,false);
    }
    @Test public void englishStartsOneTranslatedRecordingAndNormalButtonReturnsToTheSavedMode(){
        var controller=Robolectric.buildService(WispImeService.class).create();try{
            var service=controller.get();View root=service.onCreateInputView();var settings=new SecureSettings(service);settings.setMode("raw");connect(service);
            english(root).performClick();assertNotNull(ReflectionHelpers.getField(service,"recorder"));
            assertEquals("translate",ReflectionHelpers.getField(service,"activeMode"));assertEquals("raw",settings.mode());
            assertTrue(microphone(root).getContentDescription().toString().contains("с переводом"));
            service.onFinishInput();connect(service);microphone(root).performClick();
            assertNotNull(ReflectionHelpers.getField(service,"recorder"));assertEquals("raw",ReflectionHelpers.getField(service,"activeMode"));
            assertTrue(microphone(root).getContentDescription().toString().contains("без перевода"));
        }finally{controller.destroy();}
    }
    @Test public void stopUsesTheModeChosenAtStartEvenIfPreferencesChangeDuringRecording(){
        var controller=Robolectric.buildService(WispImeService.class).create();try{
            var service=controller.get();View root=service.onCreateInputView();connect(service);english(root).performClick();
            assertNotNull(ReflectionHelpers.getField(service,"recorder"));new SecureSettings(service).setMode("raw");SystemClock.sleep(1000);
            microphone(root).performClick();assertNull(ReflectionHelpers.getField(service,"recorder"));
            GroqClient client=ReflectionHelpers.getField(service,"activeApi");assertNotNull(client);assertEquals("translate",ReflectionHelpers.getField(client,"mode"));
        }finally{controller.destroy();}
    }
    @Test public void draggingEnglishMovesTheEntireControlWithoutStartingRecordingOrOpeningSettings(){
        var controller=Robolectric.buildService(WispImeService.class).create();try{
            var service=controller.get();View root=service.onCreateInputView();root.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));root.layout(0,0,400,800);
            View lower=english(root),controls=(View)lower.getParent();float initial=controls.getX();
            for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP}){
                MotionEvent event=MotionEvent.obtain(0,10,action,action==MotionEvent.ACTION_DOWN?100:20,action==MotionEvent.ACTION_DOWN?100:20,0);lower.dispatchTouchEvent(event);event.recycle();
            }
            assertTrue(controls.getX()<initial);assertNull(ReflectionHelpers.getField(service,"recorder"));
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));assertNull(Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity());
        }finally{controller.destroy();}
    }
    @Test public void holdingEnglishOpensSettingsWithoutRecording(){
        var controller=Robolectric.buildService(WispImeService.class).create();try{
            var service=controller.get();View root=service.onCreateInputView();MotionEvent event=MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,20,20,0);english(root).dispatchTouchEvent(event);event.recycle();
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ViewConfiguration.getLongPressTimeout()+10));
            assertEquals(MainActivity.class.getName(),Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity().getComponent().getClassName());assertNull(ReflectionHelpers.getField(service,"recorder"));
        }finally{controller.destroy();}
    }
    @Test public void processingDisablesBothActionsAndIdleClearsTranslationMarker(){
        var controls=new VoiceControls(RuntimeEnvironment.getApplication());controls.setTranslation(true);controls.setState(VoiceBubble.WORKING);controls.setEnabled(false);
        assertFalse(microphone(controls).isEnabled());assertFalse(english(controls).isEnabled());assertEquals("Перевод на английский",microphone(controls).getContentDescription());
        controls.setTranslation(false);controls.setState(VoiceBubble.IDLE);controls.setEnabled(true);assertTrue(english(controls).isEnabled());assertFalse(english(controls).isSelected());
    }
}
