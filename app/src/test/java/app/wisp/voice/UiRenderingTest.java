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
}
