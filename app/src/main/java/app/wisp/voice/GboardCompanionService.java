package app.wisp.voice;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.InputMethod;
import android.annotation.TargetApi;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.media.MediaRecorder;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.view.accessibility.AccessibilityEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;

/** Enabled only on API 33+: uses accessibility's separate input connection alongside Gboard. */
@TargetApi(33)
public final class GboardCompanionService extends AccessibilityService {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private SecureSettings settings;
    private UsageStats stats;
    private DiagnosticLog journal;
    private WindowManager windows;
    private VoiceBubble bubble;
    private TextView label;
    private WindowManager.LayoutParams bubbleParams,labelParams;
    private EditorInfo editor;
    private MediaRecorder recorder;
    private File audio;
    private GroqClient api;
    private Future<?> request;
    private int generation;
    private boolean busy,foreground,dragging,held,bubbleShown,labelShown;
    private long started,sessionTime;
    private String trace="",mode="clean";
    private float downX,downY;
    private int initialX,initialY;
    @Override public void onCreate(){super.onCreate();settings=new SecureSettings(this);stats=new UsageStats(this);journal=new DiagnosticLog(this);windows=getSystemService(WindowManager.class);
        File[] stale=getCacheDir().listFiles((d,n)->n.startsWith("wisp-floating-")&&n.endsWith(".m4a"));if(stale!=null)for(File f:stale)f.delete();}
    @Override public InputMethod onCreateInputMethod(){return new InputMethod(this){
        @Override public void onStartInput(EditorInfo info,boolean restarting){
            // Apps can reuse the same view/field id for a different conversation.
            // A restarted connection invalidates pending audio/results as well.
            cancel();editor=info;refresh();
        }
        @Override public void onFinishInput(){editor=null;cancel();hide();}
    };}
    @Override public void onAccessibilityEvent(AccessibilityEvent event){refresh();}
    @Override public void onServiceConnected(){journal.event(DiagnosticLog.session(),DiagnosticLog.Stage.OVERLAY,DiagnosticLog.Outcome.START,settings.mode(),0,-1,-1);createBubble();refresh();}
    @Override public void onInterrupt(){cancel();hide();}
    private boolean allowed(){
        String selected=Settings.Secure.getString(getContentResolver(),Settings.Secure.DEFAULT_INPUT_METHOD);
        return editor!=null&&!DictationText.sensitive(editor.inputType)&&editor.inputType!=0
            &&!getSystemService(KeyguardManager.class).isKeyguardLocked()
            &&(selected==null||!selected.startsWith(getPackageName()+"/"));
    }
    private void createBubble(){
        bubble=new VoiceBubble(this);label=Ui.text(this,"",13,Ui.foreground(this));label.setGravity(Gravity.CENTER);label.setMaxLines(3);
        label.setPadding(Ui.dp(this,12),Ui.dp(this,8),Ui.dp(this,12),Ui.dp(this,8));label.setBackground(Ui.bg(Ui.surface(this),Ui.dp(this,14)));
        int flags=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        bubbleParams=new WindowManager.LayoutParams(Ui.dp(this,64),Ui.dp(this,64),WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,flags,PixelFormat.TRANSLUCENT);
        bubbleParams.gravity=Gravity.TOP|Gravity.LEFT;
        labelParams=new WindowManager.LayoutParams(Ui.dp(this,250),-2,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,flags|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,PixelFormat.TRANSLUCENT);labelParams.gravity=Gravity.TOP|Gravity.LEFT;
        restorePosition();
        bubble.setOnClickListener(v->{if(recorder!=null)process();else start();});
        bubble.setOnTouchListener((v,e)->{
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){downX=e.getRawX();downY=e.getRawY();initialX=bubbleParams.x;initialY=bubbleParams.y;dragging=false;held=false;handler.postDelayed(openSettings,ViewConfiguration.getLongPressTimeout());}
            else if(e.getActionMasked()==MotionEvent.ACTION_MOVE){float dx=e.getRawX()-downX,dy=e.getRawY()-downY;if(Math.hypot(dx,dy)>ViewConfiguration.get(this).getScaledTouchSlop()){dragging=true;handler.removeCallbacks(openSettings);}if(dragging){position(initialX+(int)dx,initialY+(int)dy);updatePosition();}}
            else if(e.getActionMasked()==MotionEvent.ACTION_UP){handler.removeCallbacks(openSettings);if(dragging)savePosition();else if(!held)v.performClick();}
            else if(e.getActionMasked()==MotionEvent.ACTION_CANCEL)handler.removeCallbacks(openSettings);return true;
        });
    }
    private final Runnable openSettings=()->{held=true;cancel();startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));};
    private android.graphics.Rect bounds(){try{return windows.getCurrentWindowMetrics().getBounds();}catch(RuntimeException e){var m=getResources().getDisplayMetrics();return new android.graphics.Rect(0,0,m.widthPixels,m.heightPixels);}}
    private android.graphics.Insets safe(){try{return windows.getCurrentWindowMetrics().getWindowInsets().getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());}catch(RuntimeException e){return android.graphics.Insets.of(0,Ui.dp(this,32),0,Ui.dp(this,48));}}
    private void position(int x,int y){var r=bounds();var in=safe();int gap=Ui.dp(this,12),size=Ui.dp(this,64);
        bubbleParams.x=Math.max(in.left+gap,Math.min(r.width()-in.right-size-gap,x));bubbleParams.y=Math.max(in.top+gap,Math.min(r.height()-in.bottom-size-gap,y));}
    private void restorePosition(){var p=getSharedPreferences("wisp_companion_position",0);var b=bounds();position((int)(p.getFloat("x",.85f)*b.width()),(int)(p.getFloat("y",.43f)*b.height()));}
    private void savePosition(){var b=bounds();getSharedPreferences("wisp_companion_position",0).edit().putFloat("x",bubbleParams.x/(float)b.width()).putFloat("y",bubbleParams.y/(float)b.height()).apply();}
    private void updatePosition(){
        if(bubbleShown)windows.updateViewLayout(bubble,bubbleParams);if(labelShown){labelParams.x=Math.max(Ui.dp(this,8),Math.min(bounds().width()-labelParams.width-Ui.dp(this,8),bubbleParams.x+Ui.dp(this,32)-labelParams.width/2));labelParams.y=Math.max(safe().top+Ui.dp(this,8),bubbleParams.y-Ui.dp(this,76));windows.updateViewLayout(label,labelParams);}
    }
    private void refresh(){
        if(bubble==null)return;
        if(!allowed()){cancel();hide();return;}
        if(!bubbleShown)try{windows.addView(bubble,bubbleParams);bubbleShown=true;}catch(RuntimeException e){journal.failure(trace,DiagnosticLog.Stage.OVERLAY,mode,e);}
    }
    private void remove(View view){try{windows.removeView(view);}catch(IllegalArgumentException ignored){}}
    private void hide(){if(bubbleShown){remove(bubble);bubbleShown=false;}status("");}
    private void status(String text){
        if(label==null)return;label.setText(text);
        if(text.isEmpty()){if(labelShown){remove(label);labelShown=false;}}
        else if(bubbleShown){if(!labelShown){windows.addView(label,labelParams);labelShown=true;}updatePosition();}
    }
    private void start(){
        if(busy||recorder!=null||!allowed())return;
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){journal.event(DiagnosticLog.session(),DiagnosticLog.Stage.RECORDING,DiagnosticLog.Outcome.PERMISSION_REQUIRED,settings.mode(),0,-1,-1);status("Удерживайте микрофон и разрешите запись в настройках Wisp.");return;}
        if(!settings.hasKey()){journal.event(DiagnosticLog.session(),DiagnosticLog.Stage.KEY_STORAGE,DiagnosticLog.Outcome.FAILURE,settings.mode(),0,-1,-1);status("Удерживайте микрофон и сохраните API-ключ Groq.");return;}
        try{
            if(getInputMethod()==null||getInputMethod().getCurrentInputConnection()==null){status("Поле не готово. Коснитесь его и попробуйте ещё раз.");return;}
            if(getInputMethod().getCurrentInputConnection().getSurroundingText(0,0,0)==null){journal.event(DiagnosticLog.session(),DiagnosticLog.Stage.INSERTION,DiagnosticLog.Outcome.FAILURE,settings.mode(),0,-1,-1);status("Это поле не поддерживает вставку рядом с Gboard. Используйте прежний режим Wisp.");return;}
        }catch(RuntimeException e){journal.failure(DiagnosticLog.session(),DiagnosticLog.Stage.INSERTION,settings.mode(),e);status("Поле не готово к вставке. Коснитесь его и попробуйте ещё раз.");return;}
        cancel();trace=DiagnosticLog.session();mode=settings.mode();sessionTime=System.currentTimeMillis();journal.event(trace,DiagnosticLog.Stage.RECORDING,DiagnosticLog.Outcome.START,mode,0,-1,-1);
        try{
            notification("Запись микрофона");
            audio=File.createTempFile("wisp-floating-",".m4a",getCacheDir());recorder=new MediaRecorder(this);
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioChannels(1);recorder.setAudioSamplingRate(16000);recorder.setAudioEncodingBitRate(64000);recorder.setOutputFile(audio.getAbsolutePath());recorder.setMaxDuration(RecordingLimits.MAX_DURATION_MS);
            recorder.setOnInfoListener((r,what,extra)->{if(what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED)process();});
            recorder.setOnErrorListener((r,what,extra)->{stats.recordFailure("microphone");journal.event(trace,DiagnosticLog.Stage.RECORDING,DiagnosticLog.Outcome.FAILURE,mode,0,-1,-1);cancel();status("Запись прервалась. Попробуйте ещё раз.");});
            recorder.prepare();recorder.start();started=SystemClock.elapsedRealtime();bubble.setState(VoiceBubble.RECORDING);bubble.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);handler.post(tick);
        }catch(Exception e){journal.failure(trace,DiagnosticLog.Stage.RECORDING,mode,e);stats.recordFailure("microphone");cancel();status("Не удалось включить микрофон. Проверьте разрешение записи и службу Wisp.");}
    }
    private void notification(String text){
        NotificationManager manager=getSystemService(NotificationManager.class);manager.createNotificationChannel(new NotificationChannel("wisp_dictation","Диктовка Wisp",NotificationManager.IMPORTANCE_LOW));
        PendingIntent intent=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n=new Notification.Builder(this,"wisp_dictation").setSmallIcon(R.drawable.ic_mic_notification).setContentTitle("Wisp").setContentText(text).setContentIntent(intent).setOngoing(true).setCategory(Notification.CATEGORY_SERVICE).build();
        startForeground(31,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);foreground=true;
    }
    private final Runnable tick=new Runnable(){public void run(){if(!allowed()){cancel();hide();return;}if(recorder==null)return;long seconds=(SystemClock.elapsedRealtime()-started)/1000;status(String.format(Locale.ROOT,"%02d:%02d / %02d:%02d",seconds/60,seconds%60,RecordingLimits.MAX_SECONDS/60,RecordingLimits.MAX_SECONDS%60));bubble.setProgress(seconds/(float)RecordingLimits.MAX_SECONDS);handler.postDelayed(this,100);}};
    private void process(){
        if(recorder==null)return;long duration=SystemClock.elapsedRealtime()-started;MediaRecorder r=recorder;recorder=null;handler.removeCallbacks(tick);
        try{r.stop();}catch(RuntimeException e){r.release();journal.failure(trace,DiagnosticLog.Stage.RECORDING,mode,e);cancel();status("Запись слишком короткая. Говорите хотя бы секунду.");return;}r.release();
        if(duration<650){cancel();status("Запись слишком короткая. Говорите хотя бы секунду.");return;}
        stats.recordUse(sessionTime,duration);journal.event(trace,DiagnosticLog.Stage.RECORDING,DiagnosticLog.Outcome.SUCCESS,mode,duration,-1,-1);
        busy=true;bubble.setEnabled(false);bubble.setState(VoiceBubble.WORKING);status("Распознаю…");
        try{notification("Обработка диктовки");}catch(RuntimeException e){journal.failure(trace,DiagnosticLog.Stage.RECORDING,mode,e);cancel();status("Android прервал доступ к микрофону. Проверьте разрешение записи.");return;}
        int token=generation;File recorded=audio;String taskMode=mode,taskTrace=trace;long when=sessionTime;String language=settings.language(),vocabulary=settings.vocabulary();boolean turbo=settings.turbo(),emojis=settings.emojis();
        GroqClient client=new GroqClient(journal,taskTrace,taskMode);api=client;
        request=worker.submit(()->{try{
            var result=DictationPipeline.run(client,recorded,settings.readKey(),language,turbo,vocabulary,taskMode,emojis,s->post(token,()->status("translate".equals(taskMode)?"Перевожу на английский…":"Обрабатываю текст…")));
            post(token,()->deliver(result,when,taskMode,taskTrace));
        }catch(Exception e){post(token,()->{journal.failure(taskTrace,DiagnosticLog.Stage.TRANSCRIPTION,taskMode,e);stats.recordFailure(GroqClient.failureCategory(e));done();status(GroqClient.friendlyError(e));});}finally{recorded.delete();}});
    }
    private void post(int token,Runnable action){handler.post(()->{if(token!=generation)return;if(!allowed()){cancel();hide();return;}action.run();});}
    private void deliver(DictationPipeline.Result result,long when,String taskMode,String taskTrace){
        if(!allowed()){cancel();return;}done();if(!result.errorCategory().isEmpty())stats.recordFailure(result.errorCategory());
        var input=getInputMethod();var connection=input==null?null:input.getCurrentInputConnection();
        if(connection==null){insertionFailure(taskTrace,taskMode,null);return;}
        try{
            CompanionInsertion.Result outcome=CompanionInsertion.insert(new CompanionInsertion.Connection(){
                public CompanionInsertion.Surrounding read(int before){var s=connection.getSurroundingText(before,0,0);return s==null?null:new CompanionInsertion.Surrounding(s.getText().toString(),s.getSelectionStart(),s.getSelectionEnd(),s.getOffset());}
                public void commit(String text){connection.commitText(text,1,null);}
            },result.text());
            if(outcome==CompanionInsertion.Result.REJECTED){insertionFailure(taskTrace,taskMode,null);return;}
            if(outcome==CompanionInsertion.Result.UNCONFIRMED){journal.event(taskTrace,DiagnosticLog.Stage.INSERTION,DiagnosticLog.Outcome.UNCONFIRMED,taskMode,0,-1,-1);status("Текст отправлен в поле. Подтверждение вставки не получено.");handler.postDelayed(()->status(""),2500);return;}
            stats.recordInsertion(when,result.original(),result.text(),taskMode,result.warning().isEmpty());journal.event(taskTrace,DiagnosticLog.Stage.INSERTION,DiagnosticLog.Outcome.SUCCESS,taskMode,0,UsageStats.words(result.original()),UsageStats.words(result.text()));
            status(result.warning().replace("Сохранён исходный текст.","Вставлен исходный текст."));
        }catch(RuntimeException e){insertionFailure(taskTrace,taskMode,e);}
    }
    private void insertionFailure(String taskTrace,String taskMode,Exception e){stats.recordFailure("input");if(e!=null)journal.failure(taskTrace,DiagnosticLog.Stage.INSERTION,taskMode,e);else journal.event(taskTrace,DiagnosticLog.Stage.INSERTION,DiagnosticLog.Outcome.FAILURE,taskMode,0,-1,-1);status("Поле не подтвердило готовность к вставке. Коснитесь его и повторите диктовку.");}
    private void done(){busy=false;if(foreground){stopForeground(STOP_FOREGROUND_REMOVE);foreground=false;}if(bubble!=null){bubble.setEnabled(true);bubble.setState(VoiceBubble.IDLE);bubble.setProgress(0);}status("");}
    private void cancel(){
        if(recorder!=null||busy)journal.event(trace,DiagnosticLog.Stage.LIFECYCLE,DiagnosticLog.Outcome.CANCELLED,mode,0,-1,-1);
        generation++;handler.removeCallbacksAndMessages(null);
        if(recorder!=null){try{recorder.stop();}catch(RuntimeException ignored){}recorder.release();recorder=null;}
        if(request!=null){request.cancel(true);request=null;}if(api!=null){api.cancel();api=null;}if(audio!=null){audio.delete();audio=null;}done();
    }
    @Override public void onConfigurationChanged(Configuration c){cancel();hide();super.onConfigurationChanged(c);createBubble();refresh();}
    @Override public boolean onUnbind(Intent intent){editor=null;cancel();hide();return super.onUnbind(intent);}
    @Override public void onDestroy(){editor=null;cancel();hide();worker.shutdownNow();super.onDestroy();}
}
