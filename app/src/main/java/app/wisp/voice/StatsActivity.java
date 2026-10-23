package app.wisp.voice;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.*;

public final class StatsActivity extends Activity {
    private int days=7,metric;
    private UsageStats stats;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);stats=new UsageStats(this);
        if(state!=null){int stored=state.getInt("days",7);days=stored==365?365:stored==30?30:7;metric=Math.max(0,Math.min(2,state.getInt("metric",0)));}
    }
    @Override protected void onResume(){super.onResume();render();}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putInt("days",days);out.putInt("metric",metric);}
    private void render(){
        UsageStats.Snapshot snapshot=stats.snapshot();ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Ui.background(this));
        LinearLayout page=Ui.column(this);int pad=Ui.dp(this,20);page.setPadding(pad,Ui.dp(this,16),pad,pad);scroll.addView(page);setContentView(AppNavigation.wrap(this,scroll,AppNavigation.STATS));
        page.addView(Ui.bold(this,"Статистика",26,Ui.foreground(this)));Ui.gap(page,16);
        LinearLayout totals=Ui.row(this);page.addView(totals,Ui.fill(this,-2));
        total(totals,Long.toString(snapshot.sessions()),"Всего диктовок");total(totals,Long.toString(snapshot.words()),"Вставлено слов");total(totals,duration(snapshot.durationMs()),"Время");Ui.gap(page,16);
        LinearLayout graph=Ui.column(this);page.addView(graph,Ui.fill(this,-2));graph.addView(Ui.bold(this,days==365?"По месяцам за 365 дней":"По дням",14,Ui.foreground(this)));Ui.gap(graph,8);
        LinearLayout periods=Ui.row(this);graph.addView(periods);
        for(int period:new int[]{7,30,365}){Button button=Ui.button(this,period+" дней",days==period);periods.addView(button,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));button.setOnClickListener(v->{days=period;render();});}
        Ui.gap(graph,8);LinearLayout metrics=Ui.row(this);graph.addView(metrics);
        String[] titles={"Диктовки","Слова","Минуты"};
        for(int i=0;i<titles.length;i++){final int choice=i;Button b=Ui.button(this,titles[i],metric==i);metrics.addView(b,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));b.setOnClickListener(v->{metric=choice;render();});}
        graph.addView(new UsageChart(this,snapshot,days,metric),Ui.fill(this,200));
        LinearLayout fillers=Ui.column(this);addRanked(fillers,snapshot.fillers(),false,"Пока нет данных.");
        Ui.disclosure(page,"Слова-паразиты",fillers);Ui.gap(page,8);
        LinearLayout errors=Ui.column(this);addRanked(errors,snapshot.errors(),true,"Сбоев пока нет.");
        Ui.disclosure(page,"Ошибки обработки",errors);Ui.gap(page,12);
        Button clear=Ui.button(this,"Сбросить статистику",false);page.addView(clear,Ui.fill(this,48));
        clear.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Сбросить статистику?").setMessage("Локальные счётчики и графики будут удалены.")
            .setNegativeButton("Отмена",null).setPositiveButton("Сбросить",(dialog,which)->{stats.clear();render();}).show());
    }
    private void total(LinearLayout row,String value,String title){
        LinearLayout item=Ui.column(this);item.setPadding(0,0,Ui.dp(this,4),0);item.addView(Ui.bold(this,value,18,Ui.foreground(this)));item.addView(Ui.text(this,title,12,Ui.muted(this)));item.setContentDescription(title+": "+value);row.addView(item,new LinearLayout.LayoutParams(0,-2,1));
    }
    private void addRanked(LinearLayout parent,Map<String,Long> values,boolean errors,String empty){
        if(values.isEmpty()){parent.addView(Ui.text(this,empty,14,Ui.muted(this)));return;}
        values.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed().thenComparing(Map.Entry::getKey)).limit(8)
            .forEach(e->parent.addView(Ui.text(this,(errors?UsageStats.ERRORS.get(e.getKey()):e.getKey())+" — "+e.getValue(),15,Ui.foreground(this))));
    }
    static String duration(long ms){long seconds=ms/1000;return String.format(Locale.ROOT,"%d:%02d:%02d",seconds/3600,(seconds/60)%60,seconds%60);}
}
