package app.wisp.voice;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

/** A monochrome microphone, stop indicator and processing spinner in one touch target. */
final class VoiceBubble extends View {
    static final int IDLE=0,RECORDING=1,WORKING=2,PAUSED=3;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private int state;
    private float progress;
    private boolean translation;
    VoiceBubble(Context context){
        super(context);setBackground(Ui.bg(Ui.foreground(context),Ui.dp(context,100)));setElevation(Ui.dp(context,8));
        setClickable(true);setFocusable(false);setState(IDLE);
    }
    void setTranslation(boolean value){translation=value;setState(state);}
    void setState(int value){state=value;setContentDescription(switch(value){case RECORDING,PAUSED->translation?"Остановить запись с переводом на английский":"Остановить запись без перевода";case WORKING->translation?"Перевод на английский":"Обработка записи";default->"Микрофон. Запись без перевода. Удерживайте для настроек. Значок можно перетаскивать.";});invalidate();}
    void setProgress(float value){progress=Math.max(0,Math.min(1,value));invalidate();}
    @Override public boolean performClick(){return super.performClick();}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);float scale=Math.min(getWidth(),getHeight())/64f;c.save();c.scale(scale,scale);
        paint.setColor(Ui.background(getContext()));paint.setStrokeWidth(2.5f);paint.setStrokeCap(Paint.Cap.ROUND);
        if(state==IDLE){
            paint.setStyle(Paint.Style.FILL);c.drawRoundRect(27,18,37,35,5,5,paint);
            paint.setStyle(Paint.Style.STROKE);c.drawArc(new RectF(22,25,42,43),0,180,false,paint);
            c.drawLine(32,43,32,49,paint);c.drawLine(26,49,38,49,paint);
        }else if(state==RECORDING||state==PAUSED){
            paint.setStyle(Paint.Style.FILL);c.drawRoundRect(24,24,40,40,3,3,paint);
            paint.setStyle(Paint.Style.STROKE);c.drawArc(5,5,59,59,-90,Math.max(5,360*(1-progress)),false,paint);
        }else{
            paint.setStyle(Paint.Style.STROKE);c.drawArc(19,19,45,45,(SystemClock.uptimeMillis()%1000)*.36f,250,false,paint);
            if(isAttachedToWindow())postInvalidateDelayed(32);
        }
        if(translation&&state!=IDLE){paint.setStyle(Paint.Style.FILL);paint.setTextSize(9);paint.setTextAlign(Paint.Align.CENTER);paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);c.drawText("EN",32,53,paint);}
        c.restore();
    }
}
