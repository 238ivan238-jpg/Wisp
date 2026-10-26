package app.wisp.voice;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.widget.*;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class DiagnosticsActivity extends Activity {
    private static final int SAVE_LOG=41;
    private DiagnosticLog log;
    private String session="",exportSession="";
    @Override public void onCreate(Bundle state){super.onCreate(state);log=new DiagnosticLog(this);String value=getIntent().getStringExtra("session");if(value!=null&&value.matches("[a-f0-9-]{36}"))session=value;if(state!=null){session=state.getString("session",session);exportSession=state.getString("export_session","");}}
    @Override protected void onResume(){super.onResume();render();}
    private void render(){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Ui.background(this));
        LinearLayout page=Ui.column(this);int p=Ui.dp(this,20);page.setPadding(p,p,p,p);scroll.addView(page);setContentView(AppNavigation.wrap(this,scroll,AppNavigation.DIAGNOSTICS));
        page.addView(Ui.bold(this,"Диагностика",26,Ui.foreground(this)));Ui.gap(page,12);
        page.addView(Ui.text(this,"Логи без текстов диктовок, аудио и ключей.",13,Ui.muted(this)));Ui.gap(page,12);
        Button share=Ui.button(this,session.isEmpty()?"Поделиться логами":"Поделиться логами этой записи",true);page.addView(share,Ui.fill(this,48));share.setOnClickListener(v->{try{startActivity(Intent.createChooser(DiagnosticLog.shareIntent(this,log.export(session)),"Отправить логи Wisp"));}catch(Exception e){failed(e);}});Ui.gap(page,8);
        Button save=Ui.button(this,"Сохранить файл логов",false);page.addView(save,Ui.fill(this,48));save.setOnClickListener(v->{try{exportSession=session;startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE,session.isEmpty()?"Wisp-diagnostics.json":"Wisp-session-"+session+".json"),SAVE_LOG);}catch(Exception e){failed(e);}});Ui.gap(page,12);
        page.addView(Ui.text(this,"Прикрепите сохранённый JSON к чату для разбора ошибки.",12,Ui.muted(this)));Ui.gap(page,20);
        LinearLayout recent=Ui.column(this);page.addView(recent,Ui.fill(this,-2));recent.addView(Ui.bold(this,session.isEmpty()?"Последние события":"События этой записи",18,Ui.foreground(this)));Ui.gap(recent,8);
        var events=log.events();if(events.length()==0)recent.addView(Ui.text(this,"Журнал пока пуст.",14,Ui.muted(this)));
        for(int i=events.length()-1;i>=(session.isEmpty()?Math.max(0,events.length()-12):0);i--){var entry=events.optJSONObject(i);if(entry==null||!session.isEmpty()&&!session.equals(entry.optString("session")))continue;
            String text=entry.optString("timeUtc")+"\n"+entry.optString("stage")+" · "+entry.optString("outcome");
            if(entry.has("httpStatus"))text+=" · HTTP "+entry.optInt("httpStatus");recent.addView(Ui.text(this,text,12,Ui.foreground(this)));Ui.gap(recent,10);
        }
        if(!session.isEmpty()){Button all=Ui.button(this,"Все события",false);page.addView(all,Ui.fill(this,48));all.setOnClickListener(v->{session="";render();});}
        Ui.gap(page,16);Button clear=Ui.button(this,"Очистить журнал",false);page.addView(clear,Ui.fill(this,48));
        clear.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Очистить журнал?").setMessage("Локальные события и временные файлы логов будут удалены. Статистика сохранится.").setNegativeButton("Отмена",null).setPositiveButton("Очистить",(dialog,which)->{log.clear();render();}).show());
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(request!=SAVE_LOG||result!=RESULT_OK||data==null||data.getData()==null)return;
        try(OutputStream out=getContentResolver().openOutputStream(data.getData(),"wt")){
            if(out==null)throw new java.io.IOException();out.write(log.report(exportSession).getBytes(StandardCharsets.UTF_8));Toast.makeText(this,"Логи сохранены. Прикрепите файл к чату.",Toast.LENGTH_LONG).show();
        }catch(Exception e){failed(e);}
    }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putString("session",session);out.putString("export_session",exportSession);}
    private void failed(Exception e){log.failure(DiagnosticLog.session(),DiagnosticLog.Stage.EXPORT,"unknown",e);Toast.makeText(this,"Не удалось сохранить или отправить логи. Попробуйте другой способ.",Toast.LENGTH_LONG).show();}
}
