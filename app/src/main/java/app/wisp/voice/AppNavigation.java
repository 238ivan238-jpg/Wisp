package app.wisp.voice;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.view.*;
import android.widget.*;

/** Shared compact navigation; scrollable content never contains the bottom bar. */
final class AppNavigation {
    static final int VOICE=0,RECOVERY=1,STATS=2,DIAGNOSTICS=3,SETTINGS=4,DICTIONARY=5;
    static final String EXTRA_SECTION="wisp_section",EXTRA_MOTION="wisp_motion";
    private static final String[] LABELS={"Голос","Записи","Статистика","Диагностика","Настройки"};
    private static final int[] ICONS={R.drawable.nav_voice,R.drawable.nav_recovery,R.drawable.nav_stats,R.drawable.nav_diagnostics,R.drawable.nav_settings};
    static View wrap(Activity activity,View content,int selected){
        boolean animate=activity.getIntent().getBooleanExtra(EXTRA_MOTION,false);activity.getIntent().removeExtra(EXTRA_MOTION);
        ViewGroup old=activity.getWindow().getDecorView().findViewWithTag("bottom_navigation");
        if(old!=null)for(int i=0;i<old.getChildCount();i++)if(old.getChildAt(i).isSelected()&&i!=selected)animate=true;
        if(content.getParent() instanceof ViewGroup parent)parent.removeView(content);
        LinearLayout shell=Ui.column(activity);shell.setTag("app_shell");shell.setBackgroundColor(Ui.background(activity));
        shell.setOnApplyWindowInsetsListener((view,insets)->{
            if(Build.VERSION.SDK_INT>=30){var safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());view.setPadding(safe.left,safe.top,safe.right,safe.bottom);}
            else view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        shell.addView(content,new LinearLayout.LayoutParams(-1,0,1));UiMotion.enter(content,animate);
        View line=new View(activity);line.setBackgroundColor(Ui.border(activity));shell.addView(line,new LinearLayout.LayoutParams(-1,Ui.dp(activity,1)));
        LinearLayout bar=Ui.row(activity);bar.setTag("bottom_navigation");bar.setPadding(Ui.dp(activity,4),Ui.dp(activity,6),Ui.dp(activity,4),Ui.dp(activity,6));
        for(int i=0;i<LABELS.length;i++){
            final int section=i;LinearLayout item=Ui.column(activity);item.setGravity(Gravity.CENTER);item.setTag("nav_"+i);item.setSelected(i==selected);
            item.setBackground(Ui.bg(i==selected?Ui.border(activity):Ui.background(activity),Ui.dp(activity,12)));
            item.setClickable(true);item.setFocusable(true);item.setContentDescription(LABELS[i]);
            item.setAccessibilityDelegate(new View.AccessibilityDelegate(){@Override public void onInitializeAccessibilityNodeInfo(View host,android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(host,info);info.setClassName(Button.class.getName());info.setSelected(host.isSelected());}});
            ImageView icon=new ImageView(activity);icon.setImageResource(ICONS[i]);icon.setColorFilter(Ui.foreground(activity));icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);item.addView(icon,new LinearLayout.LayoutParams(Ui.dp(activity,22),Ui.dp(activity,22)));
            TextView label=Ui.text(activity,LABELS[i],10,Ui.foreground(activity));label.setSingleLine(true);label.setGravity(Gravity.CENTER);label.setIncludeFontPadding(false);label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);item.addView(label,new LinearLayout.LayoutParams(-1,Ui.dp(activity,24)));
            item.setOnClickListener(v->{if(section!=selected)open(activity,section);});bar.addView(item,new LinearLayout.LayoutParams(0,Ui.dp(activity,56),1));
        }
        shell.addView(bar,new LinearLayout.LayoutParams(-1,-2));return shell;
    }
    static void open(Activity activity,int section){
        if(section<VOICE||section>DICTIONARY)section=VOICE;
        if(activity instanceof MainActivity main&&section!=STATS&&section!=DIAGNOSTICS&&section!=RECOVERY){main.showSection(section);return;}
        Class<? extends Activity> target=section==RECOVERY?RecoveryActivity.class:section==STATS?StatsActivity.class:section==DIAGNOSTICS?DiagnosticsActivity.class:MainActivity.class;
        activity.startActivity(new Intent(activity,target).putExtra(EXTRA_SECTION,section).putExtra(EXTRA_MOTION,true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_NO_ANIMATION));
    }
    private AppNavigation(){}
}
