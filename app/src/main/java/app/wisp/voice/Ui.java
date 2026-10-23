package app.wisp.voice;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    static int background(Context c){return c.getColor(R.color.wisp_background);}
    static int surface(Context c){return c.getColor(R.color.wisp_surface);}
    static int border(Context c){return c.getColor(R.color.wisp_border);}
    static int foreground(Context c){return c.getColor(R.color.wisp_foreground);}
    static int muted(Context c){return c.getColor(R.color.wisp_muted);}
    static int dp(Context c, float n) { return Math.round(n * c.getResources().getDisplayMetrics().density); }
    static GradientDrawable bg(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius); return d;
    }
    static TextView text(Context c, String value, int size, int color) {
        TextView t = new TextView(c); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setFontFeatureSettings("kern"); t.setLineSpacing(dp(c, 3), 1); return t;
    }
    static TextView bold(Context c, String value, int size, int color) {
        TextView t = text(c, value, size, color); t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); return t;
    }
    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL); return l;
    }
    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c); l.setGravity(Gravity.CENTER_VERTICAL); return l;
    }
    static Button button(Context c, String value, boolean primary) {
        Button b = new Button(c); b.setText(value); b.setTextSize(14); b.setAllCaps(false);
        int[][] states=new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}};
        b.setTextColor(new ColorStateList(states,new int[]{muted(c),primary?background(c):foreground(c)}));
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        GradientDrawable background=bg(primary?foreground(c):border(c),dp(c,16));
        background.setColor(new ColorStateList(states,new int[]{surface(c),primary?foreground(c):border(c)}));b.setBackground(background);
        b.setPadding(dp(c, 12), dp(c, 6), dp(c, 12), dp(c, 6));
        b.setMinHeight(dp(c, 48)); b.setMinimumHeight(dp(c, 48)); b.setStateListAnimator(null); return b;
    }
    static void gap(LinearLayout l, int height) {
        android.view.View v = new android.view.View(l.getContext()); l.addView(v, new LinearLayout.LayoutParams(1, dp(l.getContext(), height)));
    }
    static LinearLayout card(Context c) {
        LinearLayout l = column(c); int p=dp(c, 20); l.setPadding(p,p,p,p); l.setBackground(bg(surface(c),dp(c,24))); return l;
    }
    static LinearLayout.LayoutParams fill(Context c, int height) {
        return new LinearLayout.LayoutParams(-1, height < 0 ? height : dp(c,height));
    }
    static void disclosure(LinearLayout parent,String title,android.view.View details){
        Context c=parent.getContext();Button button=button(c,title+" ▾",false);parent.addView(button,fill(c,48));
        details.setVisibility(android.view.View.GONE);parent.addView(details,fill(c,-2));
        button.setOnClickListener(v->{boolean open=details.getVisibility()!=android.view.View.VISIBLE;details.setVisibility(open?android.view.View.VISIBLE:android.view.View.GONE);button.setText(title+(open?" ▴":" ▾"));});
    }
}
