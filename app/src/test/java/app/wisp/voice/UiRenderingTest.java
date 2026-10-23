package app.wisp.voice;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import org.junit.Test;
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
    private void render(View view,String name,int height) throws Exception {
        int width=1032;
        view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height,height==2300?View.MeasureSpec.EXACTLY:View.MeasureSpec.AT_MOST));
        view.layout(0,0,width,view.getMeasuredHeight());assertTrue(view.getMeasuredHeight()>0);
        Bitmap bitmap=Bitmap.createBitmap(width,view.getMeasuredHeight(),Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        File dir=new File("build/reports/screenshots");assertTrue(dir.isDirectory()||dir.mkdirs());
        try(FileOutputStream out=new FileOutputStream(new File(dir,name))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}
        bitmap.recycle();
    }
    @Test public void renderSetupOnPixelSizedDisplay() throws Exception {
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
            render(controller.get().getWindow().getDecorView(),"setup.png",2300);
        }
    }
    @Test public void renderVoiceKeyboardOnPixelSizedDisplay() throws Exception {
        var controller=Robolectric.buildService(WispImeService.class).create();
        try{render(controller.get().onCreateInputView(),"keyboard.png",1300);}finally{controller.destroy();}
    }
}
