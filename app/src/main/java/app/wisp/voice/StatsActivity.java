package app.wisp.voice;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.*;
import java.util.*;

public final class StatsActivity extends Activity {
    private int days=7,metric;
    private UsageStats stats;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);stats=new UsageStats(this);
        if(state!=null){days=state.getInt("days",7)==30?30:7;metric=Math.max(0,Math.min(2,state.getInt("metric",0)));}
    }
    @Override protected void onResume(){super.onResume();render();}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putInt("days",days);out.putInt("metric",metric);}
    private void render(){
        UsageStats.Snapshot snapshot=stats.snapshot();ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Ui.background(this));
        scroll.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});
        LinearLayout page=Ui.column(this);int pad=Ui.dp(this,20);page.setPadding(pad,pad,pad,pad);scroll.addView(page);setContentView(scroll);
        Button back=Ui.button(this,"← Настройки",false);page.addView(back,Ui.fill(this,48));back.setOnClickListener(v->finish());Ui.gap(page,20);
        page.addView(Ui.bold(this,"Статистика",28,Ui.foreground(this)));Ui.gap(page,12);
        LinearLayout totals=Ui.card(this);page.addView(totals,Ui.fill(this,-2));
        totals.addView(Ui.bold(this,"За всё время",18,Ui.foreground(this)));Ui.gap(totals,8);
        totals.addView(Ui.text(this,"Диктовок: "+snapshot.sessions()+"\nВставлено слов: "+snapshot.words()+"\nВремя записи: "+duration(snapshot.durationMs()),17,Ui.foreground(this)));Ui.gap(page,16);
        LinearLayout graph=Ui.card(this);page.addView(graph,Ui.fill(this,-2));graph.addView(Ui.bold(this,"По дням",18,Ui.foreground(this)));Ui.gap(graph,10);
        LinearLayout periods=Ui.row(this);graph.addView(periods);
        for(int period:new int[]{7,30}){Button button=Ui.button(this,period+" дней",days==period);periods.addView(button,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));button.setOnClickListener(v->{days=period;render();});}
        Ui.gap(graph,8);LinearLayout metrics=Ui.row(this);graph.addView(metrics);
        String[] titles={"Диктовки","Слова","Минуты"};
        for(int i=0;i<titles.length;i++){final int choice=i;Button b=Ui.button(this,titles[i],metric==i);metrics.addView(b,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));b.setOnClickListener(v->{metric=choice;render();});}
        graph.addView(new UsageChart(this,snapshot,days,metric),Ui.fill(this,220));
        graph.addView(Ui.text(this,"Диктовка считается после остановки записи. Время — длительность записи, слова — только успешно вставленный текст. Дни определяются часовым поясом телефона. Графики хранят последние 90 дней.",12,Ui.muted(this)));Ui.gap(page,16);
        LinearLayout fillers=Ui.card(this);page.addView(fillers,Ui.fill(this,-2));fillers.addView(Ui.bold(this,"Удалённые слова-паразиты",18,Ui.foreground(this)));Ui.gap(fillers,8);
        addRanked(fillers,snapshot.fillers(),false,"Пока нет данных об удалённых словах-паразитах.");Ui.gap(fillers,8);
        fillers.addView(Ui.text(this,"Считаются знакомые слова и выражения, удалённые при успешной обработке «Чистый текст». При переводе и без обработки они не учитываются.",12,Ui.muted(this)));Ui.gap(page,16);
        LinearLayout errors=Ui.card(this);page.addView(errors,Ui.fill(this,-2));errors.addView(Ui.bold(this,"Ошибки обработки",18,Ui.foreground(this)));Ui.gap(errors,8);
        addRanked(errors,snapshot.errors(),true,"Сбоев пока не зафиксировано.");Ui.gap(errors,8);
        errors.addView(Ui.text(this,"Причины технических сбоев: сеть, лимиты, доступ к Groq, обработка и вставка. Грамматические ошибки автоматически не оцениваются.",12,Ui.muted(this)));Ui.gap(page,16);
        page.addView(Ui.text(this,"Статистика хранится только на телефоне. Тексты, аудио и API-ключ в неё не записываются. Данные до установки этой версии недоступны.",12,Ui.muted(this)));Ui.gap(page,12);
        Button clear=Ui.button(this,"Сбросить статистику",false);page.addView(clear,Ui.fill(this,48));
        clear.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Сбросить статистику?").setMessage("Локальные счётчики и графики будут удалены.")
            .setNegativeButton("Отмена",null).setPositiveButton("Сбросить",(dialog,which)->{stats.clear();render();}).show());
    }
    private void addRanked(LinearLayout parent,Map<String,Long> values,boolean errors,String empty){
        if(values.isEmpty()){parent.addView(Ui.text(this,empty,14,Ui.muted(this)));return;}
        values.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed().thenComparing(Map.Entry::getKey)).limit(8)
            .forEach(e->parent.addView(Ui.text(this,(errors?UsageStats.ERRORS.get(e.getKey()):e.getKey())+" — "+e.getValue(),15,Ui.foreground(this))));
    }
    static String duration(long ms){long seconds=ms/1000;return String.format(Locale.ROOT,"%d:%02d:%02d",seconds/3600,(seconds/60)%60,seconds%60);}
}
