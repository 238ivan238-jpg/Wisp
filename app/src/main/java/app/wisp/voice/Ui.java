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
    static final int BG = 0xff101a19, CARD = 0xff1b2927, BORDER = 0xff344640;
    static final int MINT = 0xffb6f5cd, TEXT = 0xfff0f4ed, MUTED = 0xffa5b8b0, ERROR = 0xffffb4a8;
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
        b.setTextColor(new ColorStateList(states,new int[]{MUTED,primary?BG:TEXT}));
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        GradientDrawable background=bg(primary?MINT:BORDER,dp(c,16));
        background.setColor(new ColorStateList(states,new int[]{0xff273832,primary?MINT:BORDER}));b.setBackground(background);
        b.setPadding(dp(c, 12), dp(c, 6), dp(c, 12), dp(c, 6));
        b.setMinHeight(dp(c, 48)); b.setMinimumHeight(dp(c, 48)); b.setStateListAnimator(null); return b;
    }
    static void gap(LinearLayout l, int height) {
        android.view.View v = new android.view.View(l.getContext()); l.addView(v, new LinearLayout.LayoutParams(1, dp(l.getContext(), height)));
    }
    static LinearLayout card(Context c) {
        LinearLayout l = column(c); int p=dp(c, 20); l.setPadding(p,p,p,p); l.setBackground(bg(CARD,dp(c,24))); return l;
    }
    static LinearLayout.LayoutParams fill(Context c, int height) {
        return new LinearLayout.LayoutParams(-1, height < 0 ? height : dp(c,height));
    }
}
