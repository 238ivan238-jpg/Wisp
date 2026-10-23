package app.wisp.voice;

import android.graphics.Color;
import android.graphics.Insets;
import android.inputmethodservice.InputMethodService;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.util.ReflectionHelpers;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class KeyboardTest {
    @Before public void disableAutomaticNetworkChecks(){RuntimeEnvironment.getApplication().getSharedPreferences("wisp_updates",0).edit().putBoolean("auto_check",false).commit();}
    private TextView find(View v,String text){
        if(v instanceof TextView t&&text.contentEquals(t.getText()))return t;
        if(v instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){TextView t=find(group.getChildAt(i),text);if(t!=null)return t;}return null;
    }
    private VoiceBubble bubble(View view){if(view instanceof VoiceBubble b)return b;if(view instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){VoiceBubble b=bubble(g.getChildAt(i));if(b!=null)return b;}return null;}
    private int generation(WispImeService s){return ReflectionHelpers.getField(s,"generation");}
    private void deliver(WispImeService s,int token,String text,String warning)throws Exception{
        Method m=WispImeService.class.getDeclaredMethod("deliver",int.class,DictationPipeline.Result.class);m.setAccessible(true);m.invoke(s,token,new DictationPipeline.Result(text,text,warning));
    }
    private Editable connect(WispImeService s,String value){
        EditorInfo info=new EditorInfo();info.inputType=InputType.TYPE_CLASS_TEXT;s.onStartInput(info,false);
        Editable text=new SpannableStringBuilder(value);Selection.setSelection(text,text.length());
        ReflectionHelpers.setField(s,"mStartedInputConnection",new BaseInputConnection(new View(s),true){@Override public Editable getEditable(){return text;}});return text;
    }
    @Test public void settingsExposeUpdatesLanguageAndCleanupWithoutBlockingScreenshots(){
        try(var a=Robolectric.buildActivity(MainActivity.class).setup()){
            View root=a.get().getWindow().getDecorView();assertNotNull(find(root,"Проверить обновления"));
            assertNotNull(find(root,"Язык: русский + English (авто)"));assertTrue(((android.widget.Switch)find(root,"Чистый текст")).isChecked());
            assertFalse(((android.widget.Switch)find(root,"Добавлять эмодзи")).isChecked());
            find(root,"Язык: русский + English (авто)").performClick();assertNotNull(find(root,"Язык: русский"));
            assertEquals(0,a.get().getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_SECURE);
        }
    }
    @Test public void formerMessageAndNotesSettingsUseTheSingleEditorOnUpgrade(){
        var context=RuntimeEnvironment.getApplication();
        for(String old:new String[]{"message","notes","clean","translate"}){
            context.getSharedPreferences("wisp_private",0).edit().putString("mode",old).commit();
            assertEquals("clean",new SecureSettings(context).mode());
            try(var a=Robolectric.buildActivity(MainActivity.class).setup()){
                View root=a.get().getWindow().getDecorView();
                assertTrue(((android.widget.Switch)find(root,"Чистый текст")).isChecked());
                for(String removed:new String[]{"Обработка: сообщение","Обработка: список","Обработка: умный текст","Письмо","Заметка"})assertNull(find(root,removed));
            }
        }
    }
    @Test public void cleanupTogglePersistsAndPreservesAnExplicitlyDisabledEditor(){
        var context=RuntimeEnvironment.getApplication();context.getSharedPreferences("wisp_private",0).edit().putString("mode","raw").commit();
        try(var a=Robolectric.buildActivity(MainActivity.class).setup()){
            android.widget.Switch toggle=(android.widget.Switch)find(a.get().getWindow().getDecorView(),"Чистый текст");
            assertFalse(toggle.isChecked());toggle.performClick();assertEquals("clean",new SecureSettings(context).mode());
            toggle.performClick();assertEquals("raw",new SecureSettings(context).mode());
        }
        try(var a=Robolectric.buildActivity(MainActivity.class).setup()){
            assertFalse(((android.widget.Switch)find(a.get().getWindow().getDecorView(),"Чистый текст")).isChecked());
        }
    }
    @Test public void translationIsOneShotAndNeverChangesTheNormalRecordingPreference(){
        var context=RuntimeEnvironment.getApplication();var settings=new SecureSettings(context);
        for(String mode:new String[]{"clean","raw"}){
            settings.setMode(mode);assertEquals("translate",settings.modeForRecording(true));
            assertEquals(mode,settings.modeForRecording(false));assertEquals(mode,new SecureSettings(context).mode());
        }
        try(var a=Robolectric.buildActivity(MainActivity.class).setup()){
            View root=a.get().getWindow().getDecorView();assertNull(find(root,"Переводить на английский"));assertNotNull(find(root,"Перевод — по кнопке EN"));
        }
    }
    @Test public void emojiToggleEnablesProcessingPersistsAndCoexistsWithTranslation(){
        var context=RuntimeEnvironment.getApplication();var settings=new SecureSettings(context);settings.setMode("raw");
        assertFalse(settings.emojis());
        try(var a=Robolectric.buildActivity(MainActivity.class).setup()){
            View root=a.get().getWindow().getDecorView();find(root,"Добавлять эмодзи").performClick();
            assertTrue(((android.widget.Switch)find(root,"Чистый текст")).isChecked());assertEquals("clean",settings.mode());assertTrue(settings.emojis());
            assertEquals("translate",settings.modeForRecording(true));assertEquals("clean",settings.mode());assertTrue(settings.emojis());
        }
        try(var a=Robolectric.buildActivity(MainActivity.class).setup()){
            View root=a.get().getWindow().getDecorView();assertTrue(((android.widget.Switch)find(root,"Добавлять эмодзи")).isChecked());
            find(root,"Добавлять эмодзи").performClick();assertFalse(settings.emojis());assertEquals("clean",settings.mode());
            find(root,"Добавлять эмодзи").performClick();assertTrue(settings.emojis());
            find(root,"Чистый текст").performClick();assertEquals("raw",settings.mode());assertFalse(settings.emojis());
            assertFalse(((android.widget.Switch)find(root,"Добавлять эмодзи")).isChecked());assertEquals("translate",settings.modeForRecording(true));
        }
        try(var a=Robolectric.buildActivity(MainActivity.class).setup()){
            View root=a.get().getWindow().getDecorView();assertFalse(((android.widget.Switch)find(root,"Добавлять эмодзи")).isChecked());
            find(root,"Чистый текст").performClick();assertEquals("clean",settings.mode());assertFalse(settings.emojis());
        }
    }
    @Test public void failedOrStaleInsertionDoesNotCountWordsButSuccessfulInsertionDoes()throws Exception{
        var c=Robolectric.buildService(WispImeService.class).create();try{
            WispImeService s=c.get();s.onCreateInputView();connect(s,"");int old=generation(s);connect(s,"");
            UsageStats stats=new UsageStats(s);deliver(s,old,"stale private words","");assertEquals(0,stats.snapshot().words());
            ReflectionHelpers.setField(s,"mStartedInputConnection",new BaseInputConnection(new View(s),true){@Override public boolean commitText(CharSequence value,int cursor){return false;}});
            deliver(s,generation(s),"Rejected words","");assertEquals(0,stats.snapshot().words());assertEquals(Long.valueOf(1),stats.snapshot().errors().get("input"));
            connect(s,"");
            deliver(s,generation(s),"Готовый API","");assertEquals(2,stats.snapshot().words());
        }finally{c.destroy();}
    }
    @Test public void onlyMicrophoneIsVisibleWhenIdle(){
        var c=Robolectric.buildService(WispImeService.class).create();try{
            ViewGroup root=(ViewGroup)c.get().onCreateInputView();assertNotNull(bubble(root));assertNotNull(find(root,"EN"));
            int visible=0;for(int i=0;i<root.getChildCount();i++)if(root.getChildAt(i).getVisibility()==View.VISIBLE)visible++;
            assertEquals(1,visible);for(String removed:new String[]{"wisp","RU + EN","Чистый","ABC","Отмена","Вставить","Пробел","↶","⌫"})assertNull(find(root,removed));
        }finally{c.destroy();}
    }
    @Test public void passwordFieldsDisableMicrophone(){
        var c=Robolectric.buildService(WispImeService.class).create();try{
            WispImeService s=c.get();View root=s.onCreateInputView();EditorInfo info=new EditorInfo();info.inputType=InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD;
            s.onStartInput(info,false);assertFalse(bubble(root).isEnabled());assertFalse(find(root,"EN").isEnabled());
        }finally{c.destroy();}
    }
    @Test public void processedTextIsAutomaticallyCommittedToActualEditable()throws Exception{
        var c=Robolectric.buildService(WispImeService.class).create();try{
            WispImeService s=c.get();s.onCreateInputView();Editable text=connect(s,"Привет");deliver(s,generation(s),"новый мир","");assertEquals("Привет новый мир",text.toString());
        }finally{c.destroy();}
    }
    @Test public void automaticallyInsertedListKeepsLineBreaksAndEnglishTerms()throws Exception{
        var c=Robolectric.buildService(WispImeService.class).create();try{
            WispImeService s=c.get();s.onCreateInputView();Editable text=connect(s,"");
            String list="На завтра:\n1. Проверить pull request.\n2. Обновить README.\n3. Запустить unit tests.";
            deliver(s,generation(s),list,"");assertEquals(list,text.toString());
        }finally{c.destroy();}
    }
    @Test public void selectedTextIsReplacedWithoutExtraSpace()throws Exception{
        var c=Robolectric.buildService(WispImeService.class).create();try{
            WispImeService s=c.get();s.onCreateInputView();Editable text=connect(s,"old text");Selection.setSelection(text,0,text.length());
            deliver(s,generation(s),"Новая фраза","");assertEquals("Новая фраза",text.toString());
        }finally{c.destroy();}
    }
    @Test public void rawFallbackIsInsertedAndReportedWithoutTranscriptPreview()throws Exception{
        var c=Robolectric.buildService(WispImeService.class).create();try{
            WispImeService s=c.get();View root=s.onCreateInputView();Editable text=connect(s,"");
            deliver(s,generation(s),"Ну, deploy завтра.","Очистка не удалась. Сохранён исходный текст.");
            assertEquals("Ну, deploy завтра.",text.toString());assertNull(find(root,"Ну, deploy завтра."));assertNotNull(find(root,"Очистка не удалась. Вставлен исходный текст."));
        }finally{c.destroy();}
    }
    @Test public void changedFieldCannotReceiveAStaleResult()throws Exception{
        var c=Robolectric.buildService(WispImeService.class).create();try{
            WispImeService s=c.get();s.onCreateInputView();connect(s,"first");int token=generation(s);Editable second=connect(s,"second");
            deliver(s,token,"private old transcript","");assertEquals("second",second.toString());
        }finally{c.destroy();}
    }
    @Test public void hidingBubbleCancelsLateResultAndClearsStatus()throws Exception{
        var c=Robolectric.buildService(WispImeService.class).create();try{
            WispImeService s=c.get();View root=s.onCreateInputView();Editable text=connect(s,"");int token=generation(s);s.onWindowHidden();
            deliver(s,token,"late","");assertEquals("",text.toString());assertNull(find(root,"late"));
        }finally{c.destroy();}
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) public void onlyBubbleRegionReceivesTouchesAndAppIsNotResized(){
        var c=Robolectric.buildService(WispImeService.class).create();try{
            WispImeService s=c.get();View root=s.onCreateInputView();root.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));root.layout(0,0,400,800);
            InputMethodService.Insets out=new InputMethodService.Insets();s.onComputeInsets(out);
            assertEquals(800,out.contentTopInsets);assertEquals(800,out.visibleTopInsets);assertEquals(InputMethodService.Insets.TOUCHABLE_INSETS_REGION,out.touchableInsets);
            VoiceControls mic=(VoiceControls)bubble(root).getParent();assertTrue("Region "+out.touchableRegion+", bubble "+mic.getX()+","+mic.getY(),out.touchableRegion.contains((int)(mic.getX()+mic.getWidth()/2),(int)(mic.getY()+mic.getHeight()/2)));assertFalse(out.touchableRegion.contains(1,1));
            TextView en=find(root,"EN");assertTrue(out.touchableRegion.contains((int)(mic.getX()+en.getX()+en.getWidth()/2),(int)(mic.getY()+en.getY()+en.getHeight()/2)));
            root.dispatchApplyWindowInsets(new WindowInsets.Builder().setInsets(WindowInsets.Type.navigationBars(),Insets.of(0,0,0,120)).build());
            assertTrue(mic.getY()+mic.getHeight()<=680);
        }finally{c.destroy();}
    }
    @Test @Config(qualifiers="night") public void darkSystemThemeUsesWhiteOnBlack(){var app=RuntimeEnvironment.getApplication();assertEquals(Color.BLACK,Ui.background(app));assertEquals(Color.WHITE,Ui.foreground(app));}
    @Test @Config(qualifiers="notnight") public void lightSystemThemeUsesBlackOnWhite(){var app=RuntimeEnvironment.getApplication();assertEquals(Color.WHITE,Ui.background(app));assertEquals(Color.BLACK,Ui.foreground(app));}
    @Test public void allPasswordVariationsRemainProtected(){
        for(int variation:new int[]{InputType.TYPE_TEXT_VARIATION_PASSWORD,InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD})assertTrue(DictationText.sensitive(InputType.TYPE_CLASS_TEXT|variation));
        assertTrue(DictationText.sensitive(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD));assertFalse(DictationText.sensitive(InputType.TYPE_CLASS_TEXT));
    }
    @Test public void holdingMicrophoneOpensSettingsWithoutStartingRecording(){
        var c=Robolectric.buildService(WispImeService.class).create();try{
            WispImeService s=c.get();View root=s.onCreateInputView();VoiceBubble mic=bubble(root);
            MotionEvent down=MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,10,10,0);mic.dispatchTouchEvent(down);down.recycle();
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(ViewConfiguration.getLongPressTimeout()+10));
            android.content.Intent intent=Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity();
            assertEquals(MainActivity.class.getName(),intent.getComponent().getClassName());assertNull(ReflectionHelpers.getField(s,"recorder"));
        }finally{c.destroy();}
    }
    @Test public void draggingBubbleSavesPositionAndDoesNotOpenSettings(){
        var c=Robolectric.buildService(WispImeService.class).create();try{
            WispImeService s=c.get();View root=s.onCreateInputView();root.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));root.layout(0,0,400,800);
            VoiceBubble mic=bubble(root);View controls=(View)mic.getParent();float before=controls.getX();
            for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP}){
                MotionEvent event=MotionEvent.obtain(0,10,action,action==MotionEvent.ACTION_DOWN?100:20,action==MotionEvent.ACTION_DOWN?100:20,0);mic.dispatchTouchEvent(event);event.recycle();
            }
            assertTrue(controls.getX()<before);assertTrue(s.getSharedPreferences("wisp_bubble",0).getFloat("x",1)<1);
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1));
            assertNull(Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity());
        }finally{c.destroy();}
    }
}
