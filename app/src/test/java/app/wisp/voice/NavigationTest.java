package app.wisp.voice;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w393dp-h852dp-420dpi")
public class NavigationTest {
    @Before public void setup(){RuntimeEnvironment.getApplication().getSharedPreferences("wisp_updates",0).edit().putBoolean("auto_check",false).commit();}
    private TextView find(View view,String text){if(view instanceof TextView t&&text.contentEquals(t.getText()))return t;if(view instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){var t=find(g.getChildAt(i),text);if(t!=null)return t;}return null;}
    private View root(MainActivity activity){return activity.getWindow().getDecorView();}
    private void tab(MainActivity activity,int section){root(activity).findViewWithTag("nav_"+section).performClick();}
    @Test public void landingPageShowsOnlyEverydayControlsAndFixedNavigation(){
        try(var a=Robolectric.buildActivity(MainActivity.class).setup()){
            View root=root(a.get());root.measure(View.MeasureSpec.makeMeasureSpec(1032,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2300,View.MeasureSpec.EXACTLY));root.layout(0,0,1032,2300);
            assertNotNull(find(root,"Чистый текст"));assertNotNull(find(root,"Добавлять эмодзи"));assertNull(find(root,"Проверить обновления"));assertNull(find(root,"Сохранить словарь"));assertNull(find(root,"Попробуйте прямо здесь"));
            ViewGroup shell=root.findViewWithTag("app_shell");ScrollView scroll=(ScrollView)shell.getChildAt(0);assertTrue(scroll.getChildAt(0).getHeight()<=scroll.getHeight());
            ViewGroup bar=root.findViewWithTag("bottom_navigation");assertEquals(5,bar.getChildCount());assertFalse(bar.getParent() instanceof ScrollView);
            for(int i=0;i<5;i++)assertTrue(bar.getChildAt(i).getHeight()>=Ui.dp(a.get(),48));assertTrue(root.findViewWithTag("nav_0").isSelected());
        }
    }
    @Test public void dictionaryDraftSurvivesTabChangesAndRecreationWhileSavePersistsOnlyOnRequest(){
        Bundle state=new Bundle();String draft="GitHub, Android, Иван";
        try(var a=Robolectric.buildActivity(MainActivity.class).setup()){
            a.get().showSection(AppNavigation.DICTIONARY);EditText input=root(a.get()).findViewWithTag("dictionary_input");input.setText(draft);tab(a.get(),0);a.get().showSection(AppNavigation.DICTIONARY);
            assertEquals(draft,((EditText)root(a.get()).findViewWithTag("dictionary_input")).getText().toString());assertEquals("",new SecureSettings(a.get()).vocabulary());
            find(root(a.get()),"Сохранить словарь").performClick();assertEquals(draft,new SecureSettings(a.get()).vocabulary());input.setText(draft+", unsaved");a.saveInstanceState(state);
        }
        try(var a=Robolectric.buildActivity(MainActivity.class).create(state).start().resume().visible()){
            assertTrue(root(a.get()).findViewWithTag("nav_4").isSelected());assertEquals(draft+", unsaved",((EditText)root(a.get()).findViewWithTag("dictionary_input")).getText().toString());assertEquals(draft,new SecureSettings(a.get()).vocabulary());
        }
    }
    @Test public void settingsOpenDetailsAndBackReturnsToTheMenuWithoutRecreatingTheUpdater(){
        try(var a=Robolectric.buildActivity(MainActivity.class).setup()){
            Object updater=ReflectionHelpers.getField(a.get(),"updates");tab(a.get(),4);assertNotNull(find(root(a.get()),"Клавиатура Wisp"));assertNull(find(root(a.get()),"Проверить обновления"));
            root(a.get()).findViewWithTag("link_Обновления").performClick();assertNotNull(find(root(a.get()),"Проверить обновления"));assertTrue(root(a.get()).findViewWithTag("nav_4").isSelected());
            a.get().onBackPressed();assertNotNull(find(root(a.get()),"Подключение"));assertSame(updater,ReflectionHelpers.getField(a.get(),"updates"));
            root(a.get()).findViewWithTag("link_Подключение").performClick();assertNotNull(find(root(a.get()),"Выбрать Gboard"));assertNull(find(root(a.get()),"Чистый текст"));
        }
    }
    @Test public void statsAndDiagnosticsTabsOpenTheirDataScreensDirectly(){
        try(var a=Robolectric.buildActivity(MainActivity.class).setup()){
            tab(a.get(),1);assertEquals(RecoveryActivity.class.getName(),Shadows.shadowOf(a.get()).getNextStartedActivity().getComponent().getClassName());
            tab(a.get(),2);assertEquals(StatsActivity.class.getName(),Shadows.shadowOf(a.get()).getNextStartedActivity().getComponent().getClassName());
            tab(a.get(),3);assertEquals(DiagnosticsActivity.class.getName(),Shadows.shadowOf(a.get()).getNextStartedActivity().getComponent().getClassName());
        }
        try(var a=Robolectric.buildActivity(DiagnosticsActivity.class).setup()){
            View root=a.get().getWindow().getDecorView();assertTrue(root.findViewWithTag("nav_3").isSelected());assertNotNull(find(root,"Последние события"));assertNotNull(find(root,"Поделиться логами"));
        }
    }
    @Test public void typedKeyIsNeverIncludedInTheSavedPageState(){
        try(var a=Robolectric.buildActivity(MainActivity.class).setup()){
            tab(a.get(),4);root(a.get()).findViewWithTag("link_Ключ Groq").performClick();EditText input=ReflectionHelpers.getField(a.get(),"keyField");input.setText("do-not-save-typed-key");
            Bundle state=new Bundle();a.saveInstanceState(state);assertEquals("key",state.getString("main_page"));assertFalse(state.toString().contains("do-not-save-typed-key"));assertFalse(input.isSaveEnabled());
        }
    }
    @Test public void everyTabLabelKeepsItsMeasuredHeightAfterSwitchingBetweenMainSections(){
        try(var a=Robolectric.buildActivity(MainActivity.class).setup()){
            for(int section:new int[]{0,AppNavigation.DICTIONARY,4,0,4}){
                a.get().showSection(section);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();View root=root(a.get());root.measure(View.MeasureSpec.makeMeasureSpec(1032,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2300,View.MeasureSpec.EXACTLY));root.layout(0,0,1032,2300);
                ViewGroup bar=root.findViewWithTag("bottom_navigation");for(int i=0;i<5;i++){ViewGroup item=(ViewGroup)bar.getChildAt(i);TextView label=(TextView)item.getChildAt(1);assertTrue(label.getText().toString(),label.getHeight()>0);assertTrue(label.getBottom()<=item.getHeight());}
            }
        }
    }
}
