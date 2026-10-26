package app.wisp.voice;

import android.Manifest;
import android.content.*;
import android.media.*;
import android.os.*;
import android.view.KeyEvent;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.AudioDeviceInfoBuilder;
import org.robolectric.util.ReflectionHelpers;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class HeadsetControlsTest {
    @Test public void buttonsOnlyWorkWhileEligibleAndIgnoreKeyRepeats(){
        var context=RuntimeEnvironment.getApplication();int[] actions={0,0,0};try(HeadsetButtons buttons=new HeadsetButtons(context,new HeadsetButtons.Actions(){public void toggle(){actions[0]++;}public void pause(){actions[1]++;}public void stop(){actions[2]++;}})){
            KeyEvent press=new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_HEADSETHOOK);assertFalse(buttons.handle(press));buttons.update(true,false,false);assertTrue(buttons.handle(press));assertEquals(1,actions[0]);
            assertFalse(buttons.handle(new KeyEvent(0,0,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_HEADSETHOOK,1)));buttons.handle(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_MEDIA_PAUSE));buttons.handle(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_MEDIA_STOP));assertEquals(1,actions[1]);assertEquals(1,actions[2]);buttons.update(false,false,false);assertFalse(buttons.handle(press));
        }
    }
    @Test public void wiredAndUsbPreferenceIsScopedAndOffByDefault(){
        var context=RuntimeEnvironment.getApplication();AudioManager manager=context.getSystemService(AudioManager.class);AudioDeviceInfo usb=AudioDeviceInfoBuilder.newBuilder().setType(AudioDeviceInfo.TYPE_USB_HEADSET).build();Shadows.shadowOf(manager).setInputDevices(List.of(usb));
        try(HeadsetRoute disabled=new HeadsetRoute(context,false)){assertNull(ReflectionHelpers.getField(disabled,"input"));}
        HeadsetRoute enabled=new HeadsetRoute(context,true);assertSame(usb,ReflectionHelpers.getField(enabled,"input"));assertEquals(MediaRecorder.AudioSource.MIC,enabled.source());enabled.close();assertNull(ReflectionHelpers.getField(enabled,"input"));
    }
    @Test public void bluetoothRouteIsRestoredAndAnExistingCallIsNotTakenOver(){
        var context=RuntimeEnvironment.getApplication();Shadows.shadowOf(context).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT);AudioManager manager=context.getSystemService(AudioManager.class);var shadow=Shadows.shadowOf(manager);
        AudioDeviceInfo bluetooth=AudioDeviceInfoBuilder.newBuilder().setType(AudioDeviceInfo.TYPE_BLUETOOTH_SCO).build();shadow.setAvailableCommunicationDevices(List.of(bluetooth));
        try(HeadsetRoute route=new HeadsetRoute(context,true)){assertEquals(AudioManager.MODE_IN_COMMUNICATION,manager.getMode());assertEquals(MediaRecorder.AudioSource.VOICE_COMMUNICATION,route.source());boolean[] ready={false};route.whenReady(new Handler(Looper.getMainLooper()),result->ready[0]=result);assertTrue(ready[0]);}
        assertEquals(AudioManager.MODE_NORMAL,manager.getMode());manager.setMode(AudioManager.MODE_IN_CALL);try(HeadsetRoute route=new HeadsetRoute(context,true)){assertEquals(AudioManager.MODE_IN_CALL,manager.getMode());assertEquals(MediaRecorder.AudioSource.MIC,route.source());}assertEquals(AudioManager.MODE_IN_CALL,manager.getMode());
    }
}
