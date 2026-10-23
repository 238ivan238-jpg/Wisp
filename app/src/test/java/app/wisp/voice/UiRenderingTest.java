package app.wisp.voice;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import org.junit.Test;
import org.junit.Before;
import org.robolectric.RuntimeEnvironment;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import java.io.File;
import java.io.FileOutputStream;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w393dp-h852dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class UiRenderingTest {
    @Before public void disableAutomaticNetworkChecks(){RuntimeEnvironment.getApplication().getSharedPreferences("wisp_updates",0).edit().putBoolean("auto_check",false).commit();}
    private void render(View view,String name,int height) throws Exception {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        int width=1032;
        view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height,height==2300?View.MeasureSpec.EXACTLY:View.MeasureSpec.AT_MOST));
        view.layout(0,0,width,view.getMeasuredHeight());assertTrue(view.getMeasuredHeight()>0);
        Bitmap bitmap=Bitmap.createBitmap(width,view.getMeasuredHeight(),Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        if(name.equals("keyboard.png")||name.equals("bubble-dark.png"))assertEquals(0,android.graphics.Color.alpha(bitmap.getPixel(0,0)));
        File dir=new File("build/reports/screenshots");assertTrue(dir.isDirectory()||dir.mkdirs());
        try(FileOutputStream out=new FileOutputStream(new File(dir,name))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}
        bitmap.recycle();
    }
    @Test public void renderSetupOnPixelSizedDisplay() throws Exception {
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
            render(controller.get().getWindow().getDecorView(),"setup.png",2300);
        }
    }
    @Test public void renderDictionarySettingsAndUpdates()throws Exception{
        try(var a=Robolectric.buildActivity(MainActivity.class).setup()){
            a.get().showSection(AppNavigation.DICTIONARY);render(a.get().getWindow().getDecorView(),"dictionary.png",2300);
            a.get().showSection(AppNavigation.SETTINGS);render(a.get().getWindow().getDecorView(),"settings.png",2300);
        }
    }
    @Test @Config(qualifiers="w393dp-h852dp-night-420dpi") public void renderDarkVoiceTab()throws Exception{
        try(var a=Robolectric.buildActivity(MainActivity.class).setup()){render(a.get().getWindow().getDecorView(),"voice-dark.png",2300);}
    }
    @Test public void renderDiagnosticsTab()throws Exception{
        try(var a=Robolectric.buildActivity(DiagnosticsActivity.class).setup()){render(a.get().getWindow().getDecorView(),"diagnostics.png",2300);}
    }
    @Test @Config(qualifiers="w393dp-h852dp-notnight-420dpi") public void renderVoiceKeyboardOnPixelSizedDisplay() throws Exception {
        var controller=Robolectric.buildService(WispImeService.class).create();
        try{render(controller.get().onCreateInputView(),"keyboard.png",2300);}finally{controller.destroy();}
    }
    @Test @Config(qualifiers="w393dp-h852dp-night-420dpi") public void renderDarkBubble() throws Exception {
        var controller=Robolectric.buildService(WispImeService.class).create();
        try{render(controller.get().onCreateInputView(),"bubble-dark.png",2300);}finally{controller.destroy();}
    }
    @Test public void renderStatisticsWithActualChartData()throws Exception{
        UsageStats stats=new UsageStats(RuntimeEnvironment.getApplication());
        for(int i=0;i<7;i++)for(int j=0;j<=i;j++)stats.recordUse(java.time.LocalDate.now().minusDays(i).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),10000);
        try(var controller=Robolectric.buildActivity(StatsActivity.class).setup()){render(controller.get().getWindow().getDecorView(),"statistics.png",2300);}
    }
    @Test public void renderYearlyStatistics()throws Exception{
        UsageStats stats=new UsageStats(RuntimeEnvironment.getApplication());for(int i=0;i<365;i++)stats.recordUse(java.time.LocalDate.now().minusDays(i).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),1000);
        android.os.Bundle state=new android.os.Bundle();state.putInt("days",365);
        try(var controller=Robolectric.buildActivity(StatsActivity.class).create(state).start().resume().visible()){render(controller.get().getWindow().getDecorView(),"statistics-year.png",2300);}
    }
    @Test @Config(qualifiers="w393dp-h852dp-notnight-420dpi") public void renderNormalAndTranslatedRecordingControls()throws Exception{
        var app=RuntimeEnvironment.getApplication();var row=new android.widget.FrameLayout(app);row.setPadding(20,20,20,20);row.setBackgroundColor(Ui.surface(app));
        for(int mode=0;mode<3;mode++){
            VoiceControls controls=new VoiceControls(app);controls.setTranslation(mode==2);controls.setState(mode==0?VoiceBubble.IDLE:VoiceBubble.RECORDING);controls.setProgress(.4f);
            var params=new android.widget.FrameLayout.LayoutParams(Ui.dp(app,64),Ui.dp(app,112));params.leftMargin=mode*Ui.dp(app,88);row.addView(controls,params);
        }
        render(row,"quick-translation.png",400);
    }
    @Test public void renderRecoveryAndPauseRetryControls()throws Exception{
        try(var activity=Robolectric.buildActivity(RecoveryActivity.class).setup()){render(activity.get().getWindow().getDecorView(),"recovery.png",2300);}
        var app=RuntimeEnvironment.getApplication();var row=new android.widget.FrameLayout(app);row.setPadding(20,20,20,20);row.setBackgroundColor(Ui.surface(app));
        for(int i=0;i<3;i++){VoiceControls controls=new VoiceControls(app);controls.setTranslation(i==0);controls.setState(i==0?VoiceBubble.RECORDING:i==1?VoiceBubble.PAUSED:VoiceBubble.IDLE);controls.setRetry(i==2);controls.setProgress(.4f);
            var params=new android.widget.FrameLayout.LayoutParams(Ui.dp(app,64),Ui.dp(app,112));params.leftMargin=i*Ui.dp(app,88);row.addView(controls,params);}
        render(row,"pause-retry.png",400);
    }

}
