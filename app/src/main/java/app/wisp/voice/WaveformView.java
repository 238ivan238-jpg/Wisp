package app.wisp.voice;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

final class WaveformView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] levels=new float[35];
    WaveformView(Context c){super(c);setContentDescription("Уровень микрофона");}
    void add(float level){System.arraycopy(levels,1,levels,0,levels.length-1);levels[levels.length-1]=Math.max(0,Math.min(1,level));invalidate();}
    void clear(){java.util.Arrays.fill(levels,0);invalidate();}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);float step=getWidth()/(float)levels.length;
        paint.setColor(Ui.MINT);paint.setStrokeWidth(Ui.dp(getContext(),3));paint.setStrokeCap(Paint.Cap.ROUND);
        for(int i=0;i<levels.length;i++){
            float h=Ui.dp(getContext(),2)+levels[i]*(getHeight()*.8f);float x=step*(i+.5f);
            paint.setAlpha(levels[i]>.02f?255:80);c.drawLine(x,(getHeight()-h)/2,x,(getHeight()+h)/2,paint);
        }
    }
}
