package app.wisp.voice;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.drawable.ColorDrawable;
import android.inputmethodservice.InputMethodService;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.*;
import java.io.File;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class WispImeService extends InputMethodService {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private SecureSettings settings;
    private RecoveryStore recovery;
    private RecoveryStore.Entry draft;
    private String detachedRecovery="";
    private ConnectionCheck.Probe preflight;
    private final RecordingClock clock=new RecordingClock();
    private UsageStats stats;
    private DiagnosticLog journal;
    private String trace="";
    private boolean tracePending;
    private MediaRecorder recorder;
    private File audio;
    private Future<?> request;
    private GroqClient activeApi;
    private long started,touchDown;
    private long sessionTime;
    private String activeMode="clean";
    private int generation;
    private boolean busy,sensitive,dragging,longPressed;
    private float downX,downY,startX,startY;
    private int safeTop,safeBottom,safeLeft,safeRight;
    private FrameLayout root;
    private TextView status;
    private VoiceControls mic;
    @Override public void onCreate(){
        super.onCreate();settings=new SecureSettings(this);stats=new UsageStats(this);journal=new DiagnosticLog(this);recovery=new RecoveryStore(this);preflight=client->ConnectionCheck.check(this,settings,client);
        File[] stale=getCacheDir().listFiles((dir,name)->name.startsWith("wisp-audio-")&&name.endsWith(".m4a"));
        if(stale!=null)for(File f:stale)f.delete();
    }
    @Override public boolean onEvaluateFullscreenMode(){return false;}
    @Override public void onConfigureWindow(Window window,boolean fullscreen,boolean candidatesOnly){
        window.setLayout(-1,-1);window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setDimAmount(0);window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setNavigationBarColor(Ui.background(this));
        int flags=window.getDecorView().getSystemUiVisibility();
        boolean dark=(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        window.getDecorView().setSystemUiVisibility(dark?flags&~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:flags|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }
    @Override public View onCreateInputView(){
        root=new FrameLayout(this);root.setBackgroundColor(Color.TRANSPARENT);
        root.setMinimumHeight(getResources().getDisplayMetrics().heightPixels);
        root.setClipChildren(false);root.setClipToPadding(false);
        safeBottom=Ui.dp(this,32);
        mic=new VoiceControls(this);root.addView(mic,new FrameLayout.LayoutParams(Ui.dp(this,VoiceControls.WIDTH_DP),Ui.dp(this,VoiceControls.HEIGHT_DP)));
        status=Ui.text(this,"",13,Ui.foreground(this));status.setMaxLines(3);status.setGravity(Gravity.CENTER);
        status.setPadding(Ui.dp(this,12),Ui.dp(this,8),Ui.dp(this,12),Ui.dp(this,8));
        status.setBackground(Ui.bg(Ui.surface(this),Ui.dp(this,14)));
        FrameLayout.LayoutParams label=new FrameLayout.LayoutParams(Ui.dp(this,250),-2);root.addView(status,label);
        status.setVisibility(View.GONE);status.setOnClickListener(v->openSettings());
        root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->positionViews());
        root.setOnApplyWindowInsetsListener((v,insets)->{
            if(Build.VERSION.SDK_INT>=30){
                android.graphics.Insets system=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                safeTop=system.top;safeBottom=Math.max(Ui.dp(this,24),system.bottom);safeLeft=system.left;safeRight=system.right;
            }else{safeTop=insets.getSystemWindowInsetTop();safeBottom=Math.max(Ui.dp(this,24),insets.getSystemWindowInsetBottom());safeLeft=insets.getSystemWindowInsetLeft();safeRight=insets.getSystemWindowInsetRight();}
            positionViews();return insets;
        });
        mic.setActions(()->{if(recorder!=null)stopAndProcess();else startRecording(false);},()->{if(recorder!=null)pauseRecording();else if(draft!=null&&!draft.handed)retry();else if(!detachedRecovery.isEmpty())openRecovery();else startRecording(true);});
        mic.setGestureListener((v,e)->{
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){
                downX=e.getRawX();downY=e.getRawY();startX=mic.getX();startY=mic.getY();dragging=false;longPressed=false;
                handler.postDelayed(openSettingsHold,ViewConfiguration.getLongPressTimeout());return true;
            }else if(e.getActionMasked()==MotionEvent.ACTION_MOVE){
                float dx=e.getRawX()-downX,dy=e.getRawY()-downY;
                if(Math.hypot(dx,dy)>ViewConfiguration.get(this).getScaledTouchSlop()){dragging=true;handler.removeCallbacks(openSettingsHold);}
                if(dragging){moveBubble(startX+dx,startY+dy);positionLabel();root.requestLayout();}return true;
            }else if(e.getActionMasked()==MotionEvent.ACTION_UP){
                handler.removeCallbacks(openSettingsHold);
                if(dragging)savePosition();else if(!longPressed)v.performClick();return true;
            }else if(e.getActionMasked()==MotionEvent.ACTION_CANCEL){handler.removeCallbacks(openSettingsHold);return true;}
            return true;
        });
        if(getWindow()!=null&&getWindow().getWindow()!=null)onConfigureWindow(getWindow().getWindow(),false,false);
        resetUi();return root;
    }
    private final Runnable openSettingsHold=()->{longPressed=true;openSettings();};
    private void openSettings(){cancelSession();startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
    private float left(){return safeLeft+Ui.dp(this,10);}
    private float top(){return safeTop+Ui.dp(this,10);}
    private float right(){return Math.max(left(),root.getWidth()-safeRight-Ui.dp(this,74));}
    private float bottom(){return Math.max(top(),root.getHeight()-safeBottom-Ui.dp(this,VoiceControls.HEIGHT_DP+10));}
    private void moveBubble(float x,float y){mic.setX(Math.max(left(),Math.min(right(),x)));mic.setY(Math.max(top(),Math.min(bottom(),y)));}
    private void positionViews(){
        if(root==null||root.getWidth()==0||mic==null)return;
        if(!dragging){
            android.content.SharedPreferences p=getSharedPreferences("wisp_bubble",MODE_PRIVATE);
            float x=p.getFloat("x",1),y=p.getFloat("y",1);moveBubble(left()+x*(right()-left()),top()+y*(bottom()-top()));
        }
        positionLabel();
    }
    private void positionLabel(){
        if(status==null||root==null)return;float x=mic.getX()+mic.getWidth()/2f-status.getMeasuredWidth()/2f;
        status.setX(Math.max(safeLeft,Math.min(root.getWidth()-safeRight-status.getMeasuredWidth(),x)));
        float above=mic.getY()-status.getMeasuredHeight()-Ui.dp(this,10);
        status.setY(above>=top()?above:mic.getY()+mic.getHeight()+Ui.dp(this,8));
    }
    private void savePosition(){
        float x=right()>left()?(mic.getX()-left())/(right()-left()):0;
        float y=bottom()>top()?(mic.getY()-top())/(bottom()-top()):0;
        getSharedPreferences("wisp_bubble",MODE_PRIVATE).edit().putFloat("x",x).putFloat("y",y).apply();dragging=false;
    }
    private void setStatus(String text){
        if(status==null)return;status.setText(text);status.setVisibility(text.isEmpty()?View.GONE:View.VISIBLE);
        if(root!=null)root.post(this::positionLabel);
    }
    @Override public void onComputeInsets(Insets out){
        int height=getWindow()!=null&&getWindow().getWindow()!=null?getWindow().getWindow().getDecorView().getHeight():0;
        if(height<=0&&root!=null)height=root.getHeight();
        out.contentTopInsets=height;out.visibleTopInsets=height;out.touchableInsets=Insets.TOUCHABLE_INSETS_REGION;out.touchableRegion.setEmpty();
        addTouchable(out,mic);addTouchable(out,status);
    }
    private void addTouchable(Insets out,View view){
        if(view==null||view.getVisibility()!=View.VISIBLE||view.getWidth()==0)return;
        int[] xy=new int[2];root.getLocationInWindow(xy);int x=xy[0]+Math.round(view.getX()),y=xy[1]+Math.round(view.getY());
        out.touchableRegion.op(new Rect(x,y,x+view.getWidth(),y+view.getHeight()),Region.Op.UNION);
    }
    @Override public void onStartInput(EditorInfo info,boolean restarting){super.onStartInput(info,restarting);cancelSession();sensitive=info==null||DictationText.sensitive(info.inputType);resetUi();}
    @Override public void onStartInputView(EditorInfo info,boolean restarting){super.onStartInputView(info,restarting);resetUi();}
    private void resetUi(){
        if(status==null)return;setStatus(sensitive?"В полях паролей диктовка отключена":"");
        mic.setRetry(!detachedRecovery.isEmpty());mic.setEnabled(!sensitive);mic.setTranslation(false);mic.setState(VoiceBubble.IDLE);mic.setProgress(0);
    }
    @SuppressWarnings("deprecation")
    private void startRecording(boolean english){
        if(recorder!=null||busy||sensitive)return;
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){journal.event(DiagnosticLog.session(),DiagnosticLog.Stage.RECORDING,DiagnosticLog.Outcome.PERMISSION_REQUIRED,settings.mode(),0,-1,-1);error("Удерживайте микрофон и разрешите запись в настройках.");return;}
        if(!settings.hasKey()){journal.event(DiagnosticLog.session(),DiagnosticLog.Stage.KEY_STORAGE,DiagnosticLog.Outcome.FAILURE,settings.mode(),0,-1,-1);error("Удерживайте микрофон и сохраните API-ключ Groq в настройках.");return;}
        cancelSession();detachedRecovery="";
        trace=DiagnosticLog.session();tracePending=true;activeMode=settings.modeForRecording(english);mic.setTranslation(english);
        busy=true;mic.setEnabled(false);mic.setState(VoiceBubble.WORKING);setStatus("Проверяю подключение…");
        int token=generation;String checkTrace=trace,checkMode=activeMode;GroqClient client=GroqClient.preflight(journal,trace,activeMode);activeApi=client;
        journal.event(trace,DiagnosticLog.Stage.PREFLIGHT,ConnectionCheck.vpn(this)?DiagnosticLog.Outcome.VPN_ACTIVE:DiagnosticLog.Outcome.VPN_INACTIVE,activeMode,0,-1,-1);
        request=worker.submit(()->{try{preflight.check(client);journal.event(checkTrace,DiagnosticLog.Stage.PREFLIGHT,DiagnosticLog.Outcome.SUCCESS,checkMode,0,-1,-1);post(token,()->{busy=false;activeApi=null;beginRecording();});}
            catch(Exception e){journal.failure(checkTrace,DiagnosticLog.Stage.PREFLIGHT,checkMode,e);post(token,()->{busy=false;tracePending=false;error(ConnectionCheck.error(e));});}});
    }
    @SuppressWarnings("deprecation")
    private void beginRecording(){
        if(sensitive){cancelSession();return;}journal.event(trace,DiagnosticLog.Stage.RECORDING,DiagnosticLog.Outcome.START,activeMode,0,-1,-1);
        try{
            audio=File.createTempFile("wisp-audio-",".m4a",getCacheDir());
            recorder=Build.VERSION.SDK_INT>=31?new MediaRecorder(this):new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);recorder.setAudioChannels(1);recorder.setAudioSamplingRate(16000);
            recorder.setAudioEncodingBitRate(64000);recorder.setOutputFile(audio.getAbsolutePath());recorder.setMaxDuration(RecordingLimits.MAX_DURATION_MS);
            recorder.setOnInfoListener((r,what,extra)->{if(what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED)stopAndProcess();});
            recorder.setOnErrorListener((r,what,extra)->{journal.event(trace,DiagnosticLog.Stage.RECORDING,DiagnosticLog.Outcome.FAILURE,activeMode,0,-1,-1);stats.recordFailure("microphone");cancelSession();error("Микрофон прервал запись. Попробуйте ещё раз.");});
            recorder.prepare();recorder.start();started=SystemClock.elapsedRealtime();clock.start(started);sessionTime=System.currentTimeMillis();
            mic.setEnabled(true);mic.setState(VoiceBubble.RECORDING);handler.post(tick);
        }catch(Exception e){journal.failure(trace,DiagnosticLog.Stage.RECORDING,activeMode,e);stats.recordFailure("microphone");cancelSession();error("Не удалось включить микрофон. Проверьте разрешение и другие приложения записи.");}
    }
    private final Runnable tick=new Runnable(){public void run(){
        if(getSystemService(android.app.KeyguardManager.class).isKeyguardLocked()){cancelSession();resetUi();return;}
        if(recorder==null)return;long elapsed=clock.elapsed(SystemClock.elapsedRealtime());if(elapsed>=RecordingLimits.MAX_DURATION_MS){stopAndProcess();return;}long seconds=elapsed/1000;
        setStatus((clock.paused()?"Пауза · ":"")+("translate".equals(activeMode)?"EN · ":"")+String.format(Locale.ROOT,"%02d:%02d / %02d:%02d",seconds/60,seconds%60,RecordingLimits.MAX_SECONDS/60,RecordingLimits.MAX_SECONDS%60));mic.setProgress(seconds/(float)RecordingLimits.MAX_SECONDS);
        handler.postDelayed(this,90);
    }};
    private void stopAndProcess(){
        if(recorder==null)return;long duration=clock.elapsed(SystemClock.elapsedRealtime());
        MediaRecorder r=recorder;recorder=null;handler.removeCallbacks(tick);
        try{r.stop();}catch(RuntimeException e){journal.failure(trace,DiagnosticLog.Stage.RECORDING,activeMode,e);stats.recordFailure("microphone");r.release();cancelSession();error("Запись слишком короткая. Говорите хотя бы секунду.");return;}
        r.release();
        if(duration<650){cancelSession();error("Запись слишком короткая. Говорите хотя бы секунду.");return;}
        stats.recordUse(sessionTime,duration);
        journal.event(trace,DiagnosticLog.Stage.RECORDING,DiagnosticLog.Outcome.SUCCESS,activeMode,duration,-1,-1);
        try{draft=recovery.create(audio,sessionTime,duration,activeMode,settings,trace);audio.delete();audio=null;}
        catch(Exception e){journal.failure(trace,DiagnosticLog.Stage.RECOVERY,activeMode,e);error("Не удалось сохранить запись для восстановления.");return;}
        submitDraft();
    }
    private void pauseRecording(){
        if(recorder==null||busy)return;
        try{if(clock.paused()){recorder.resume();clock.resume(SystemClock.elapsedRealtime());mic.setState(VoiceBubble.RECORDING);journal.event(trace,DiagnosticLog.Stage.PAUSE,DiagnosticLog.Outcome.RESUMED,activeMode,0,-1,-1);}
            else{recorder.pause();clock.pause(SystemClock.elapsedRealtime());mic.setState(VoiceBubble.PAUSED);journal.event(trace,DiagnosticLog.Stage.PAUSE,DiagnosticLog.Outcome.SUCCESS,activeMode,0,-1,-1);}}
        catch(RuntimeException e){journal.failure(trace,DiagnosticLog.Stage.RECORDING,activeMode,e);cancelSession();error("Запись прервалась. Проверьте вкладку «Записи».");}
    }
    private void openRecovery(){startActivity(new Intent(this,RecoveryActivity.class).putExtra("recovery_id",detachedRecovery).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
    private void retry(){if(busy||recorder!=null||draft==null||draft.handed||sensitive)return;submitDraft();}
    private void submitDraft(){
        busy=true;mic.setRetry(false);mic.setEnabled(false);mic.setTranslation("translate".equals(draft.mode));mic.setState(VoiceBubble.WORKING);setStatus("Обрабатываю запись…");
        int token=generation;RecoveryStore.Entry entry=draft;String operationTrace=trace,mode=entry.mode;
        GroqClient api=new GroqClient(journal,operationTrace,mode);activeApi=api;
        request=worker.submit(()->{try{
            String key=entry.text.isEmpty()||!entry.error.isEmpty()?settings.readKey():"";
            var result=RecoveryJob.run(recovery,entry,getCacheDir(),api,key,original->post(token,()->setStatus("translate".equals(mode)?"Перевожу на английский…":"Обрабатываю текст…")));
            post(token,()->deliver(token,result));
        }catch(Exception e){journal.failure(operationTrace,DiagnosticLog.Stage.RECOVERY,mode,e);post(token,()->{tracePending=false;stats.recordFailure(GroqClient.failureCategory(e));busy=false;error(ConnectionCheck.error(e)+" Нажмите ↻.");mic.setRetry(draft!=null&&!draft.handed);});}});
    }
    private boolean handoff(){
        if(draft==null)return true;
        try{draft.handed=true;recovery.save(draft);return true;}catch(Exception e){draft.handed=false;journal.failure(trace,DiagnosticLog.Stage.RECOVERY,activeMode,e);error("Текст сохранён. Повторите вставку кнопкой ↻.");mic.setRetry(true);return false;}
    }
    private void insertionFailure(){
        error("Проверьте поле. Копия текста — Wisp → Записи.");mic.setRetry(draft!=null&&!draft.handed);
    }
    private void deliver(int token,DictationPipeline.Result result){
        if(token!=generation||sensitive)return;busy=false;tracePending=false;resetUi();
        journal.event(trace,DiagnosticLog.Stage.INSERTION,DiagnosticLog.Outcome.START,activeMode,0,-1,-1);
        if(!result.errorCategory().isEmpty())stats.recordFailure(result.errorCategory());
        InputConnection ic=getCurrentInputConnection();
        if(ic==null){journal.event(trace,DiagnosticLog.Stage.INSERTION,DiagnosticLog.Outcome.FAILURE,activeMode,0,-1,-1);stats.recordFailure("input");insertionFailure();return;}
        if(!handoff())return;
        try{
        CharSequence selected=ic.getSelectedText(0);
        String insertion=DictationText.insertion(result.text(),ic.getTextBeforeCursor(2,0),selected!=null&&selected.length()>0);
        if(insertion.isEmpty()||!ic.commitText(insertion,1)){journal.event(trace,DiagnosticLog.Stage.INSERTION,DiagnosticLog.Outcome.FAILURE,activeMode,0,-1,-1);stats.recordFailure("input");insertionFailure();return;}
        stats.recordInsertion(sessionTime>0?sessionTime:System.currentTimeMillis(),result.original(),result.text(),activeMode,result.warning().isEmpty());
        journal.event(trace,DiagnosticLog.Stage.INSERTION,DiagnosticLog.Outcome.SUCCESS,activeMode,0,UsageStats.words(result.original()),UsageStats.words(result.text()));
        if(draft!=null){if(result.warning().isEmpty())recovery.delete(draft.id);draft=null;}
        if(!result.warning().isEmpty())setStatus("Вставлен исходный текст. Обработка — во вкладке «Записи».");
        }catch(RuntimeException e){journal.failure(trace,DiagnosticLog.Stage.INSERTION,activeMode,e);stats.recordFailure("input");insertionFailure();}
    }
    private void post(int token,Runnable action){handler.post(()->{if(token!=generation)return;if(getSystemService(android.app.KeyguardManager.class).isKeyguardLocked()){cancelSession();resetUi();return;}action.run();});}
    private void error(String message){if(status==null)return;resetUi();setStatus(message);}
    private void cancelSession(){
        if(tracePending&&journal!=null){journal.event(trace,DiagnosticLog.Stage.LIFECYCLE,DiagnosticLog.Outcome.CANCELLED,activeMode,0,-1,-1);tracePending=false;}
        if(draft!=null&&!draft.handed)detachedRecovery=draft.id;
        generation++;handler.removeCallbacksAndMessages(null);
        if(recorder!=null){boolean valid=false;try{recorder.stop();valid=true;}catch(RuntimeException ignored){}recorder.release();recorder=null;
            long duration=clock.elapsed(SystemClock.elapsedRealtime());if(valid&&duration>=650&&audio!=null)try{detachedRecovery=recovery.create(audio,sessionTime,duration,activeMode,settings,trace).id;}catch(Exception e){journal.failure(trace,DiagnosticLog.Stage.RECOVERY,activeMode,e);}}
        if(request!=null){request.cancel(true);request=null;}
        if(activeApi!=null){activeApi.cancel();activeApi=null;}
        if(audio!=null){audio.delete();audio=null;}
        draft=null;busy=false;
    }
    @Override public void onConfigurationChanged(Configuration config){cancelSession();super.onConfigurationChanged(config);if(isInputViewShown())setInputView(onCreateInputView());}
    @Override public void onFinishInputView(boolean finishingInput){cancelSession();resetUi();super.onFinishInputView(finishingInput);}
    @Override public void onFinishInput(){cancelSession();resetUi();super.onFinishInput();}
    @Override public void onWindowHidden(){cancelSession();resetUi();super.onWindowHidden();}
    @Override public void onDestroy(){cancelSession();worker.shutdownNow();super.onDestroy();}
}
