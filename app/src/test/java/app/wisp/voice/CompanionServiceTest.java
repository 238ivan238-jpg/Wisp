package app.wisp.voice;

import android.app.KeyguardManager;
import android.content.*;
import android.content.pm.*;
import android.os.Looper;
import android.view.inputmethod.EditorInfo;
import android.text.InputType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class CompanionServiceTest {
    @Test public void serviceUsesSystemInputApiWithoutScreenTreeAccess()throws Exception{
        var context=RuntimeEnvironment.getApplication();var info=context.getPackageManager().getServiceInfo(new ComponentName(context,GboardCompanionService.class),PackageManager.GET_META_DATA);
        assertEquals("android.permission.BIND_ACCESSIBILITY_SERVICE",info.permission);assertTrue(info.enabled);assertTrue((info.getForegroundServiceType()&ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)!=0);
        try(var xml=context.getResources().getXml(info.metaData.getInt("android.accessibilityservice"))){while(xml.next()!=org.xmlpull.v1.XmlPullParser.START_TAG){}
            String ns="http://schemas.android.com/apk/res/android";assertFalse(xml.getAttributeBooleanValue(ns,"canRetrieveWindowContent",true));
            assertTrue((xml.getAttributeIntValue(ns,"accessibilityFlags",0)&android.accessibilityservice.AccessibilityServiceInfo.FLAG_INPUT_METHOD_EDITOR)!=0);
        }
    }
    @Test public void changingOrFinishingEditorCancelsLateInsertionCallback()throws Exception{
        var c=Robolectric.buildService(GboardCompanionService.class).create();try{
            var service=c.get();Shadows.shadowOf(service.getSystemService(KeyguardManager.class)).setKeyguardLocked(false);
            var input=service.onCreateInputMethod();EditorInfo first=new EditorInfo();first.inputType=InputType.TYPE_CLASS_TEXT;first.packageName="example.app";first.fieldId=1;input.onStartInput(first,false);
            int token=ReflectionHelpers.getField(service,"generation");AtomicBoolean inserted=new AtomicBoolean();
            var post=GboardCompanionService.class.getDeclaredMethod("post",int.class,Runnable.class);post.setAccessible(true);post.invoke(service,token,(Runnable)()->inserted.set(true));
            EditorInfo second=new EditorInfo();second.inputType=InputType.TYPE_CLASS_TEXT;second.packageName="another.app";second.fieldId=2;input.onStartInput(second,false);
            Shadows.shadowOf(Looper.getMainLooper()).idle();assertFalse(inserted.get());input.onFinishInput();assertNull(ReflectionHelpers.getField(service,"editor"));assertFalse(ReflectionHelpers.getField(service,"busy"));
        }finally{c.destroy();}
    }
    @Test @Config(sdk=28) public void oldAndroidKeepsLegacyImeAndDisablesCompanion()throws Exception{
        var context=RuntimeEnvironment.getApplication();var component=new ComponentName(context,context.getPackageName()+".GboardCompanionService");
        assertFalse(context.getPackageManager().getServiceInfo(component,PackageManager.MATCH_DISABLED_COMPONENTS).enabled);
        assertTrue(context.getPackageManager().getServiceInfo(new ComponentName(context,context.getPackageName()+".WispImeService"),0).enabled);
    }
    @Test public void passwordFieldsAndLegacyImeCannotShowCompanionBubble()throws Exception{
        var c=Robolectric.buildService(GboardCompanionService.class).create();try{
            var service=c.get();Shadows.shadowOf(service.getSystemService(KeyguardManager.class)).setKeyguardLocked(false);
            android.provider.Settings.Secure.putString(service.getContentResolver(),android.provider.Settings.Secure.DEFAULT_INPUT_METHOD,"com.google.android.inputmethod.latin/.LatinIME");
            var input=service.onCreateInputMethod();var info=new EditorInfo();info.inputType=InputType.TYPE_CLASS_TEXT;input.onStartInput(info,false);
            var allowed=GboardCompanionService.class.getDeclaredMethod("allowed");allowed.setAccessible(true);assertTrue((boolean)allowed.invoke(service));
            var password=new EditorInfo();password.inputType=InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD;input.onStartInput(password,false);assertFalse((boolean)allowed.invoke(service));
            input.onStartInput(info,false);android.provider.Settings.Secure.putString(service.getContentResolver(),android.provider.Settings.Secure.DEFAULT_INPUT_METHOD,"app.wisp.voice/.WispImeService");assertFalse((boolean)allowed.invoke(service));
        }finally{c.destroy();}
    }
    @Test public void overlayWindowIsSmallAndNeverTakesKeyboardFocus(){
        var c=Robolectric.buildService(GboardCompanionService.class).create();try{
            var service=c.get();service.onServiceConnected();android.view.WindowManager.LayoutParams p=ReflectionHelpers.getField(service,"bubbleParams");
            assertEquals(android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,p.type);assertTrue((p.flags&android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)!=0);
            assertEquals(Ui.dp(service,64),p.width);assertEquals(Ui.dp(service,112),p.height);assertTrue(p.x>=0);assertTrue(p.y>=0);
        }finally{c.destroy();}
    }
    @Test public void restartedConnectionWithReusedFieldIdStillInvalidatesPendingResult()throws Exception{
        var c=Robolectric.buildService(GboardCompanionService.class).create();try{
            var service=c.get();Shadows.shadowOf(service.getSystemService(KeyguardManager.class)).setKeyguardLocked(false);
            var input=service.onCreateInputMethod();var info=new EditorInfo();info.inputType=InputType.TYPE_CLASS_TEXT;info.fieldId=123;info.packageName="example.chat";input.onStartInput(info,false);
            int token=ReflectionHelpers.getField(service,"generation");AtomicBoolean inserted=new AtomicBoolean();
            var post=GboardCompanionService.class.getDeclaredMethod("post",int.class,Runnable.class);post.setAccessible(true);post.invoke(service,token,(Runnable)()->inserted.set(true));
            input.onStartInput(info,true);Shadows.shadowOf(Looper.getMainLooper()).idle();assertFalse(inserted.get());assertTrue((int)ReflectionHelpers.getField(service,"generation")>token);
        }finally{c.destroy();}
    }
    @Test public void recordingTickCancelsSessionWhenScreenBecomesLocked(){
        var c=Robolectric.buildService(GboardCompanionService.class).create();try{
            var service=c.get();var keyguard=Shadows.shadowOf(service.getSystemService(KeyguardManager.class));keyguard.setKeyguardLocked(false);
            var input=service.onCreateInputMethod();var info=new EditorInfo();info.inputType=InputType.TYPE_CLASS_TEXT;input.onStartInput(info,false);
            int token=ReflectionHelpers.getField(service,"generation");ReflectionHelpers.setField(service,"busy",true);keyguard.setKeyguardLocked(true);
            Runnable tick=ReflectionHelpers.getField(service,"tick");tick.run();assertFalse(ReflectionHelpers.getField(service,"busy"));assertTrue((int)ReflectionHelpers.getField(service,"generation")>token);
        }finally{c.destroy();}
    }
    @Test public void draggingTheEnglishActionKeepsBothButtonsInsideTheSafeScreenArea(){
        var c=Robolectric.buildService(GboardCompanionService.class).create();try{
            var service=c.get();service.onServiceConnected();VoiceControls controls=ReflectionHelpers.getField(service,"bubble");
            android.view.WindowManager.LayoutParams p=ReflectionHelpers.getField(service,"bubbleParams");
            controls.measure(android.view.View.MeasureSpec.makeMeasureSpec(p.width,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(p.height,android.view.View.MeasureSpec.EXACTLY));controls.layout(0,0,p.width,p.height);
            android.view.View english=controls.getChildAt(1);assertTrue(english.getWidth()>=Ui.dp(service,48));assertTrue(english.getHeight()>=Ui.dp(service,48));
            for(int action:new int[]{android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_MOVE,android.view.MotionEvent.ACTION_UP}){
                var event=android.view.MotionEvent.obtain(0,10,action,action==android.view.MotionEvent.ACTION_DOWN?10:10000,action==android.view.MotionEvent.ACTION_DOWN?10:10000,0);english.dispatchTouchEvent(event);event.recycle();
            }
            var metrics=service.getSystemService(android.view.WindowManager.class).getCurrentWindowMetrics();var safe=metrics.getWindowInsets().getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout());
            assertTrue(p.x+p.width<=metrics.getBounds().width()-safe.right);assertTrue(p.y+p.height<=metrics.getBounds().height()-safe.bottom);
            assertTrue(english.getTop()>=controls.getChildAt(0).getBottom());assertTrue(service.getSharedPreferences("wisp_companion_position",0).contains("y"));
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1));assertNull(Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity());
        }finally{c.destroy();}
    }
}
