package app.wisp.voice;

import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class FeaturesUiTest {
    @Before public void disableUpdates(){RuntimeEnvironment.getApplication().getSharedPreferences("wisp_updates",0).edit().putBoolean("auto_check",false).commit();}
    private TextView find(View v,String text){if(v instanceof TextView t&&text.contentEquals(t.getText()))return t;if(v instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){var t=find(g.getChildAt(i),text);if(t!=null)return t;}return null;}
    @Test public void intensityCanBeChangedOnlyWhileEmojiStylingIsOnAndPersists(){
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
            var activity=controller.get();View root=activity.getWindow().getDecorView();TextView intensity=root.findViewWithTag("emoji_intensity");assertFalse(intensity.isEnabled());((Switch)find(root,"Добавлять эмодзи")).setChecked(true);assertTrue(intensity.isEnabled());intensity.performClick();var dialog=ShadowAlertDialog.getLatestAlertDialog();dialog.getListView().performItemClick(null,2,2);assertEquals(EmojiStyle.ACTIVE,new SecureSettings(activity).emojiStyle());assertEquals("Эмодзи: Активно",intensity.getText().toString());((Switch)find(root,"Чистый текст")).setChecked(false);assertFalse(intensity.isEnabled());assertEquals(EmojiStyle.ACTIVE,new SecureSettings(activity).emojiStyle());
        }
    }
    @Test public void quickAccessSettingsAreASeparateCompactPageAndHeadsetIsOptIn(){
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
            var activity=controller.get();activity.showSection(AppNavigation.SETTINGS);View root=activity.getWindow().getDecorView();root.findViewWithTag("link_Шторка и гарнитура").performClick();root=activity.getWindow().getDecorView();assertNotNull(find(root,"Добавить Wisp в шторку"));assertFalse(((Switch)find(root,"Кнопка гарнитуры")).isChecked());assertFalse(((Switch)find(root,"Предпочитать микрофон гарнитуры")).isChecked());((Switch)find(root,"Кнопка гарнитуры")).setChecked(true);assertTrue(new SecureSettings(activity).headsetButtons());
        }
    }
    @Test public void statisticsShowsSpeedAndSeparateStageWaitingTimes(){
        var context=RuntimeEnvironment.getApplication();UsageStats stats=new UsageStats(context);long now=System.currentTimeMillis();stats.recordSpeech(now,"one two three four",2000);stats.recordWait(now,UsageStats.WaitStage.RECOGNITION,2000);stats.recordWait(now,UsageStats.WaitStage.EDITING,800);
        try(var controller=Robolectric.buildActivity(StatsActivity.class).setup()){View root=controller.get().getWindow().getDecorView();assertNotNull(find(root,"Скорость диктовки: 120 слов/мин"));assertNotNull(find(root,"Распознавание: 2.0 с · 1 запись"));assertNotNull(find(root,"Обработка текста: 0.8 с · 1 ответ"));}
    }
}
