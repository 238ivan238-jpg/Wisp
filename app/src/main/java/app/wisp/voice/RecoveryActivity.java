package app.wisp.voice;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.widget.*;
import java.io.*;
import java.text.DateFormat;
import java.util.Date;
import java.util.concurrent.*;

/** User-visible private recovery, never a diagnostics payload or an automatic clipboard fallback. */
public final class RecoveryActivity extends Activity {
    private RecoveryStore store;
    private SecureSettings settings;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private GroqClient api;
    private Future<?> request;
    private boolean busy;
    private String selected="",exportId="";
    private boolean exportAudio;
    private TextView notice;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        store=new RecoveryStore(this);settings=new SecureSettings(this);
        selected=getIntent().getStringExtra("recovery_id");if(selected==null)selected="";
        if(state!=null){selected=state.getString("selected","");exportId=state.getString("export_id","");exportAudio=state.getBoolean("export_audio");}
    }
    @Override protected void onResume(){super.onResume();render();}
    private void render(){
        ScrollView scroll=new ScrollView(this);LinearLayout page=Ui.column(this);int p=Ui.dp(this,20);page.setPadding(p,p,p,p);scroll.addView(page);
        setContentView(AppNavigation.wrap(this,scroll,AppNavigation.RECOVERY));
        page.addView(Ui.bold(this,"Восстановление",26,Ui.foreground(this)));Ui.gap(page,8);
        notice=Ui.text(this,busy?"Обрабатываю сохранённую запись…":"До 10 записей · 7 дней · только на телефоне",12,Ui.muted(this));page.addView(notice);Ui.gap(page,16);
        try{
            if(!selected.isEmpty())detail(page,store.get(selected));
            else{
                var entries=store.list();if(entries.isEmpty())page.addView(Ui.text(this,"Здесь появятся записи, которые не удалось обработать или вставить.",15,Ui.muted(this)));
                for(var e:entries){
                    String date=DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(e.time));
                    String state=e.handed?"Передано в поле — проверьте текст":!e.text.isEmpty()&&e.error.isEmpty()?"Текст готов":!e.availableText().isEmpty()?e.partCount>1&&e.original.isEmpty()?"Распознано частей: "+e.parts.size()+" из "+e.partCount:"Есть исходный текст":"Есть аудиозапись";
                    Button row=Ui.button(this,date+" · "+e.duration/1000+" с\n"+state,false);page.addView(row,Ui.fill(this,-2));Ui.gap(page,8);
                    row.setEnabled(!busy);row.setOnClickListener(v->{selected=e.id;render();});
                }
            }
        }catch(Exception e){selected="";notice.setText("Не удалось открыть запись. Остальные данные не удалены.");}
    }
    private void detail(LinearLayout page,RecoveryStore.Entry e){
        button(page,"‹ Все записи",()->{selected="";render();});
        if(e.handed){page.addView(Ui.text(this,"Текст уже передавался в приложение. Проверьте поле перед копированием: повторная обработка здесь ничего не вставляет.",13,Ui.muted(this)));Ui.gap(page,12);}
        if(e.original.isEmpty()&&!e.parts.isEmpty()){page.addView(Ui.text(this,"Распознано частей: "+e.parts.size()+" из "+e.partCount+". Ниже — неполная расшифровка.",13,Ui.muted(this)));Ui.gap(page,8);}
        String text=e.text.isEmpty()?e.availableText():e.text;
        if(!text.isEmpty()){
            TextView body=Ui.text(this,text,16,Ui.foreground(this));body.setTextIsSelectable(true);page.addView(body);Ui.gap(page,16);
            button(page,"Скопировать текст",()->copy(text));
            if(!e.original.isEmpty()&&!e.original.equals(text))button(page,"Скопировать исходный текст",()->copy(e.original));
            button(page,"Сохранить текст в файл",()->export(e.id,false));
        }else{page.addView(Ui.text(this,"Распознавание не завершилось. Аудиозапись сохранена.",14,Ui.muted(this)));Ui.gap(page,12);}
        if(e.text.isEmpty()||!e.error.isEmpty())button(page,"↻ Повторить обработку",()->retry(e.id));
        button(page,"Диагностика этой записи",()->startActivity(new Intent(this,DiagnosticsActivity.class).putExtra("session",e.trace)));
        button(page,"Сохранить аудио",()->export(e.id,true));
        button(page,"Удалить запись",()->new AlertDialog.Builder(this).setTitle("Удалить запись?").setMessage("Аудио и сохранённый текст будут удалены.").setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->{store.delete(e.id);selected="";render();}).show());
    }
    private void button(LinearLayout page,String text,Runnable action){Button b=Ui.button(this,text,false);b.setEnabled(!busy);page.addView(b,Ui.fill(this,48));Ui.gap(page,8);b.setOnClickListener(v->action.run());}
    private void copy(String text){getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("Wisp",text));Toast.makeText(this,"Текст скопирован",Toast.LENGTH_SHORT).show();}
    private void retry(String id){
        if(busy)return;busy=true;render();String trace,mode;try{var saved=store.get(id);trace=saved.trace;mode=saved.mode;}catch(Exception e){busy=false;notice.setText("Не удалось открыть запись.");return;}DiagnosticLog log=new DiagnosticLog(this);
        api=new GroqClient(log,trace,mode);GroqClient client=api;
        request=worker.submit(()->{String error="";try{
            var entry=store.get(id);
            String key=entry.original.isEmpty()||!"raw".equals(entry.mode)?settings.readKey():"";
            var result=RecoveryJob.run(store,entry,getCacheDir(),client,key,new RecoveryJob.Observer(){public void progress(JobProgress stage){runOnUiThread(()->{if(!isDestroyed()&&busy)notice.setText(stage.label());});} public void speech(RecoveryStore.Entry e){new UsageStats(RecoveryActivity.this).recordSpeech(e.time,e.original,e.duration);} public void timing(RecoveryStore.Entry e,UsageStats.WaitStage stage,long ms){new UsageStats(RecoveryActivity.this).recordWait(e.time,stage,ms);}});
            if(!result.errorCategory().isEmpty())error="Исходный текст сохранён. Обработка пока недоступна.";
        }catch(Exception e){log.failure(trace,DiagnosticLog.Stage.RECOVERY,mode,e);error=ConnectionCheck.error(e);}
            String message=error;runOnUiThread(()->{if(isDestroyed())return;busy=false;render();if(!message.isEmpty())notice.setText(message);});
        });
    }
    private void export(String id,boolean audio){
        exportId=id;exportAudio=audio;
        startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(audio?"audio/mp4":"text/plain").putExtra(Intent.EXTRA_TITLE,audio?"Wisp-recording.m4a":"Wisp-text.txt"),71);
    }
    @Override protected void onActivityResult(int requestCode,int result,Intent data){
        super.onActivityResult(requestCode,result,data);if(requestCode!=71||result!=RESULT_OK||data==null||data.getData()==null)return;
        File temporary=null;try(OutputStream out=getContentResolver().openOutputStream(data.getData(),"wt")){
            if(out==null)throw new IOException();var entry=store.get(exportId);
            if(exportAudio){temporary=store.audio(entry,getCacheDir());try(InputStream in=new FileInputStream(temporary)){byte[] block=new byte[8192];int count;while((count=in.read(block))!=-1)out.write(block,0,count);}}
            else out.write((entry.text.isEmpty()?entry.availableText():entry.text).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Toast.makeText(this,"Файл сохранён",Toast.LENGTH_SHORT).show();
        }catch(Exception e){Toast.makeText(this,"Не удалось сохранить файл",Toast.LENGTH_SHORT).show();}finally{if(temporary!=null)temporary.delete();}
    }
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("selected",selected);state.putString("export_id",exportId);state.putBoolean("export_audio",exportAudio);}
    @Override public void onBackPressed(){if(!selected.isEmpty()&&!busy){selected="";render();}else if(!busy)AppNavigation.open(this,AppNavigation.VOICE);}
    @Override protected void onDestroy(){if(request!=null)request.cancel(true);if(api!=null)api.cancel();worker.shutdownNow();super.onDestroy();}
}
