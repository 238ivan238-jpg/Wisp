package app.wisp.voice;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Small native chart; its complete daily values are also exposed as accessibility text. */
final class UsageChart extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<LocalDate> dates=new ArrayList<>();
    private final List<Double> values=new ArrayList<>();
    private final DateTimeFormatter labels;
    private final boolean yearly;
    UsageChart(Context context,UsageStats.Snapshot snapshot,int days,int metric){
        super(context);setMinimumHeight(Ui.dp(context,200));setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        yearly=days==365;labels=DateTimeFormatter.ofPattern(yearly?"MM.yy":"dd.MM");
        StringBuilder description=new StringBuilder(switch(metric){case 1->"Слова";case 2->"Минуты записи";default->"Диктовки";});
        if(yearly)description.append(" по месяцам за 365 дней");
        LocalDate today=LocalDate.now();
        for(int i=days-1;i>=0;i--){
            LocalDate date=today.minusDays(i);UsageStats.Day day=snapshot.days().getOrDefault(date.toString(),new UsageStats.Day(0,0,0));
            double value=switch(metric){case 1->day.words();case 2->day.durationMs()/60000.0;default->day.sessions();};
            LocalDate bucket=yearly?date.withDayOfMonth(1):date;
            if(!dates.isEmpty()&&dates.get(dates.size()-1).equals(bucket))values.set(values.size()-1,values.get(values.size()-1)+value);
            else{dates.add(bucket);values.add(value);}
        }
        for(int i=0;i<dates.size();i++)description.append("; ").append(yearly?java.time.YearMonth.from(dates.get(i)):dates.get(i)).append(": ").append(number(values.get(i)));
        setContentDescription(description);
    }
    private static String number(double value){return value==Math.rint(value)?Long.toString((long)value):String.format(Locale.ROOT,"%.1f",value);}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float margin=Ui.dp(getContext(),12),top=Ui.dp(getContext(),30),base=getHeight()-Ui.dp(getContext(),28);
        float width=getWidth()-2*margin,step=width/Math.max(1,values.size());double maximum=Math.max(1,Collections.max(values));
        paint.setColor(Ui.border(getContext()));paint.setStrokeWidth(Ui.dp(getContext(),1));canvas.drawLine(margin,base,getWidth()-margin,base,paint);
        paint.setColor(Ui.foreground(getContext()));paint.setTextSize(Ui.dp(getContext(),12));paint.setTextAlign(Paint.Align.LEFT);canvas.drawText(number(maximum),margin,top-Ui.dp(getContext(),12),paint);
        for(int i=0;i<values.size();i++){
            float x=margin+i*step+step*.18f,barWidth=Math.max(1,step*.64f),height=(float)(values.get(i)/maximum)*(base-top);
            paint.setColor(Ui.foreground(getContext()));if(height>0)canvas.drawRect(x,base-height,x+barWidth,base,paint);
            if(i==0||i==values.size()-1||values.size()==7||i%(yearly?2:7)==0&&i<values.size()-2){
                paint.setColor(Ui.muted(getContext()));paint.setTextAlign(i==0?Paint.Align.LEFT:i==values.size()-1?Paint.Align.RIGHT:Paint.Align.CENTER);
                canvas.drawText(labels.format(dates.get(i)),i==0?margin:i==values.size()-1?getWidth()-margin:x+barWidth/2,base+Ui.dp(getContext(),20),paint);
            }
        }
    }
}
