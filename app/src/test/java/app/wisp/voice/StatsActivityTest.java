package app.wisp.voice;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.*;
import android.widget.TextView;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class StatsActivityTest {
    private TextView find(View v,String text){if(v instanceof TextView t&&text.contentEquals(t.getText()))return t;if(v instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){TextView t=find(g.getChildAt(i),text);if(t!=null)return t;}return null;}
    private UsageChart chart(View v){if(v instanceof UsageChart c)return c;if(v instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){UsageChart c=chart(g.getChildAt(i));if(c!=null)return c;}return null;}
    @Before public void setup(){RuntimeEnvironment.getApplication().getSharedPreferences("wisp_updates",0).edit().putBoolean("auto_check",false).commit();new UsageStats(RuntimeEnvironment.getApplication()).clear();}
    @Test public void settingsLinkOpensStatsScreen(){
        try(var a=Robolectric.buildActivity(MainActivity.class).setup()){
            find(a.get().getWindow().getDecorView(),"Открыть статистику").performClick();
            assertEquals(StatsActivity.class.getName(),Shadows.shadowOf(a.get()).getNextStartedActivity().getComponent().getClassName());
        }
    }
    @Test public void screenShowsTotalsAndChartSupportsPeriodAndMetricChanges(){
        UsageStats stats=new UsageStats(RuntimeEnvironment.getApplication());long now=System.currentTimeMillis();stats.recordUse(now,60000);stats.recordInsertion(now,"Ну, API готов.","API готов.","clean",true);stats.recordFailure("limit");
        try(var a=Robolectric.buildActivity(StatsActivity.class).setup()){
            View root=a.get().getWindow().getDecorView();assertNotNull(find(root,"Диктовок: 1\nВставлено слов: 2\nВремя записи: 0:01:00"));
            assertNotNull(find(root,"ну — 1"));assertNotNull(find(root,"Лимит Groq — 1"));assertTrue(chart(root).getContentDescription().toString().startsWith("Диктовки"));
            find(root,"30 дней").performClick();root=a.get().getWindow().getDecorView();find(root,"Слова").performClick();root=a.get().getWindow().getDecorView();
            String accessible=chart(root).getContentDescription().toString();assertTrue(accessible.startsWith("Слова"));assertTrue(accessible.contains(UsageStats.day(now)+": 2"));assertEquals(30,accessible.chars().filter(c->c==';').count());
        }
    }
    @Test public void resetRequiresConfirmationAndUpdatesVisibleCounters(){
        UsageStats stats=new UsageStats(RuntimeEnvironment.getApplication());stats.recordUse(System.currentTimeMillis(),1000);
        try(var a=Robolectric.buildActivity(StatsActivity.class).setup()){
            find(a.get().getWindow().getDecorView(),"Сбросить статистику").performClick();
            AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();assertEquals(1,stats.snapshot().sessions());dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertEquals(1,stats.snapshot().sessions());
            find(a.get().getWindow().getDecorView(),"Сбросить статистику").performClick();ShadowAlertDialog.getLatestAlertDialog().getButton(DialogInterface.BUTTON_POSITIVE).performClick();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertEquals(0,stats.snapshot().sessions());assertNotNull(find(a.get().getWindow().getDecorView(),"Диктовок: 0\nВставлено слов: 0\nВремя записи: 0:00:00"));
        }
    }
}
