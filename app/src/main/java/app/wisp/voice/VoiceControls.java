package app.wisp.voice;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;

/** One movable microphone with a smaller, attached one-shot English action. */
final class VoiceControls extends FrameLayout {
    static final int WIDTH_DP=64,HEIGHT_DP=112;
    private final VoiceBubble microphone;
    private final Button english;
    private final Paint connector=new Paint(Paint.ANTI_ALIAS_FLAG);
    private int state=VoiceBubble.IDLE;
    private boolean translation,retry;
    VoiceControls(Context context){
        super(context);setWillNotDraw(false);setClipChildren(false);setClipToPadding(false);
        microphone=new VoiceBubble(context);addView(microphone,new LayoutParams(Ui.dp(context,64),Ui.dp(context,64)));
        english=Ui.button(context,"EN",true);english.setTextSize(12);english.setPadding(0,0,0,0);
        english.setIncludeFontPadding(false);english.setFocusable(false);english.setGravity(Gravity.CENTER);
        english.setBackground(Ui.bg(Ui.foreground(context),Ui.dp(context,24)));
        LayoutParams lower=new LayoutParams(Ui.dp(context,48),Ui.dp(context,48),Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        lower.topMargin=Ui.dp(context,64);addView(english,lower);setState(VoiceBubble.IDLE);
    }
    void setActions(Runnable normal,Runnable translated){microphone.setOnClickListener(v->normal.run());english.setOnClickListener(v->translated.run());}
    void setGestureListener(View.OnTouchListener listener){microphone.setOnTouchListener(listener);english.setOnTouchListener(listener);}
    void setTranslation(boolean value){translation=value;microphone.setTranslation(value);setState(state);}
    void setState(int value){
        state=value;microphone.setState(value);
        english.setText(value==VoiceBubble.RECORDING?"❚❚":value==VoiceBubble.PAUSED?"▶":retry&&value==VoiceBubble.IDLE?"↻":"EN");
        english.setTextSize(value==VoiceBubble.IDLE&&!retry?12:23);
        english.setContentDescription(value==VoiceBubble.RECORDING?"Приостановить запись":value==VoiceBubble.PAUSED?"Продолжить запись":value==VoiceBubble.WORKING?"Обработка записи":retry?"Повторить обработку сохранённой записи":"Записать с переводом на английский. Удерживайте для настроек. Можно перетаскивать.");
        english.setSelected(translation&&value!=VoiceBubble.IDLE);
    }
    void setRetry(boolean value){retry=value;setState(state);}
    void setAmplitude(int value){microphone.setAmplitude(value);}
    void setProgress(float value){microphone.setProgress(value);}
    @Override public void setEnabled(boolean enabled){super.setEnabled(enabled);if(microphone!=null)microphone.setEnabled(enabled);if(english!=null)english.setEnabled(enabled);}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);connector.setColor(Ui.foreground(getContext()));canvas.drawRect(Ui.dp(getContext(),25),Ui.dp(getContext(),52),Ui.dp(getContext(),39),Ui.dp(getContext(),80),connector);}
}
