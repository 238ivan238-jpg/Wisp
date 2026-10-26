package app.wisp.voice;

import android.animation.ValueAnimator;
import android.content.Context;
import android.provider.Settings;
import android.view.*;

final class UiMotion {
    static boolean enabled(Context c){return ValueAnimator.areAnimatorsEnabled()&&Settings.Global.getFloat(c.getContentResolver(),Settings.Global.TRANSITION_ANIMATION_SCALE,1)>0;}
    static void press(View view,boolean down){
        view.animate().cancel();view.setPressed(down);
        float scale=down?.94f:1;
        if(enabled(view.getContext()))view.animate().scaleX(scale).scaleY(scale).setDuration(down?85:130).start();
        else{view.setScaleX(1);view.setScaleY(1);}
    }
    static void enter(View view,boolean animate){
        view.animate().cancel();view.setAlpha(1);view.setTranslationY(0);
        if(!animate||!enabled(view.getContext()))return;
        view.setAlpha(0);view.setTranslationY(Ui.dp(view.getContext(),4));
        view.animate().alpha(1).translationY(0).setDuration(160).start();
    }
    static void reset(View view){view.animate().cancel();view.setScaleX(1);view.setScaleY(1);view.setPressed(false);}
    private UiMotion(){}
}
