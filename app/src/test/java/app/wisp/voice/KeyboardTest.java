package app.wisp.voice;

import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ServiceController;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import org.robolectric.Shadows;
import android.os.Looper;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Selection;
import android.view.inputmethod.BaseInputConnection;
import org.robolectric.util.ReflectionHelpers;
import android.view.WindowManager;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class KeyboardTest {
    private TextView find(View v,String label){
        if(v instanceof TextView t&&label.contentEquals(t.getText()))return t;
        if(v instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){TextView t=find(g.getChildAt(i),label);if(t!=null)return t;}
        return null;
    }
    @Test public void settingsOpenWithSetupStepsAndNoSecretPrefill(){
        try(var activity=Robolectric.buildActivity(MainActivity.class).setup()){
            View root=activity.get().getWindow().getDecorView();
            assertNotNull(find(root,"01   Подготовьте клавиатуру"));assertNotNull(find(root,"02   Подключите Groq"));
            assertNotNull(find(root,"Попробуйте прямо здесь"));assertNotNull(find(root,"Сохранить"));
            assertEquals(0,activity.get().getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_SECURE);
        }
    }
    @Test public void passwordFieldsDisableMicrophoneAndInsertion(){
        ServiceController<WispImeService> controller=Robolectric.buildService(WispImeService.class).create();
        try {
            WispImeService s=controller.get();View root=s.onCreateInputView();EditorInfo info=new EditorInfo();
            info.inputType=InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD;s.onStartInput(info,false);
            assertFalse(find(root,"● Запись").isEnabled());assertFalse(find(root,"Вставить").isEnabled());
            assertNotNull(find(root,"Защищённое поле · диктовка отключена"));
        } finally { controller.destroy(); }
    }
    @Test public void changingFieldsDropsOldTranscriptAndPendingInsertion() throws Exception {
        var controller=Robolectric.buildService(WispImeService.class).create();
        try {
            WispImeService s=controller.get();View root=s.onCreateInputView();
            for(String name:new String[]{"raw","edited"}){Field f=WispImeService.class.getDeclaredField(name);f.setAccessible(true);f.set(s,"Private dictation from old field");}
            EditorInfo next=new EditorInfo();next.inputType=InputType.TYPE_CLASS_TEXT;s.onStartInput(next,false);
            Field edited=WispImeService.class.getDeclaredField("edited");edited.setAccessible(true);assertEquals("",edited.get(s));
            assertFalse(find(root,"Вставить").isEnabled());
        } finally { controller.destroy(); }
    }
    @Test public void alphabetKeyboardCanBeExpandedAndLanguageChanged(){
        var controller=Robolectric.buildService(WispImeService.class).create();
        try {
            WispImeService s=controller.get();View root=s.onCreateInputView();find(root,"ABC").performClick();
            assertNotNull(find(root,"й"));find(root,"RU + EN").performClick();find(root,"RU").performClick();
            assertNotNull(find(root,"q"));assertNotNull(find(root,"EN"));
        } finally { controller.destroy(); }
    }
    @Test public void allPasswordVariationsAreProtected(){
        for(int variation:new int[]{InputType.TYPE_TEXT_VARIATION_PASSWORD,InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD})
            assertTrue(DictationText.sensitive(InputType.TYPE_CLASS_TEXT|variation));
        assertTrue(DictationText.sensitive(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD));
        assertFalse(DictationText.sensitive(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE));
    }
    @Test public void lateNetworkResultCannotAffectNewEditor() throws Exception {
        var controller=Robolectric.buildService(WispImeService.class).create();
        try {
            WispImeService s=controller.get();s.onCreateInputView();
            Field generation=WispImeService.class.getDeclaredField("generation");generation.setAccessible(true);
            int oldToken=generation.getInt(s);AtomicBoolean delivered=new AtomicBoolean();
            Method post=WispImeService.class.getDeclaredMethod("post",int.class,Runnable.class);post.setAccessible(true);
            EditorInfo next=new EditorInfo();next.inputType=InputType.TYPE_CLASS_TEXT;s.onStartInput(next,false);
            post.invoke(s,oldToken,(Runnable)()->delivered.set(true));
            Shadows.shadowOf(Looper.getMainLooper()).idle();assertFalse(delivered.get());
            post.invoke(s,generation.getInt(s),(Runnable)()->delivered.set(true));
            Shadows.shadowOf(Looper.getMainLooper()).idle();assertTrue(delivered.get());
        } finally {controller.destroy();}
    }
    @Test public void insertingAndUndoingWorksAgainstAnActualEditable() throws Exception {
        var controller=Robolectric.buildService(WispImeService.class).create();
        try {
            WispImeService s=controller.get();View root=s.onCreateInputView();
            EditorInfo info=new EditorInfo();info.inputType=InputType.TYPE_CLASS_TEXT;s.onStartInput(info,false);
            Editable text=new SpannableStringBuilder("Привет");Selection.setSelection(text,text.length());
            BaseInputConnection connection=new BaseInputConnection(new View(s),true){@Override public Editable getEditable(){return text;}};
            ReflectionHelpers.setField(s,"mStartedInputConnection",connection);
            ReflectionHelpers.setField(s,"edited","новый мир");find(root,"Вставить").setEnabled(true);find(root,"Вставить").performClick();
            assertEquals("Привет новый мир",text.toString());find(root,"↶").performClick();assertEquals("Привет",text.toString());
        } finally {controller.destroy();}
    }
    @Test public void backspaceDoesNotLeaveHalfAnEmoji() throws Exception {
        var controller=Robolectric.buildService(WispImeService.class).create();
        try {
            WispImeService s=controller.get();View root=s.onCreateInputView();
            Editable text=new SpannableStringBuilder("A😀");Selection.setSelection(text,text.length());
            ReflectionHelpers.setField(s,"mStartedInputConnection",new BaseInputConnection(new View(s),true){@Override public Editable getEditable(){return text;}});
            find(root,"⌫").performClick();assertEquals("A",text.toString());
        } finally {controller.destroy();}
    }
    @Test public void hidingKeyboardClearsTheVisibleTranscript() throws Exception {
        var controller=Robolectric.buildService(WispImeService.class).create();
        try {
            WispImeService s=controller.get();View root=s.onCreateInputView();
            Field preview=WispImeService.class.getDeclaredField("preview");preview.setAccessible(true);
            ((TextView)preview.get(s)).setText("Private dictation");
            s.onWindowHidden();assertNull(find(root,"Private dictation"));assertFalse(find(root,"Вставить").isEnabled());
        } finally {controller.destroy();}
    }
}
