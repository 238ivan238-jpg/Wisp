package app.wisp.voice;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.inputmethodservice.InputMethodService;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
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
    private MediaRecorder recorder;
    private File audio;
    private Future<?> request;
    private GroqClient activeApi;
    private long started;
    private int generation;
    private boolean busy, sensitive, shifted, lettersShown, rawShown;
    private String raw="",edited="",lastInserted="";
    private TextView status,preview;
    private Button mic,insert,language,mode,rawButton,lettersButton;
    private LinearLayout letters;
    private WaveformView waveform;
    private boolean touchStartedRecording, suppressMicClick, suppressDeleteClick;
    private long touchDown;
    @Override public void onCreate(){
        super.onCreate();settings=new SecureSettings(this);
        File[] stale=getCacheDir().listFiles((dir,name)->name.startsWith("wisp-audio-")&&name.endsWith(".m4a"));
        if(stale!=null)for(File f:stale)f.delete();
    }
    @Override public boolean onEvaluateFullscreenMode(){return false;}
    @Override public View onCreateInputView(){
        LinearLayout root=Ui.column(this);root.setBackground(Ui.bg(Ui.BG,Ui.dp(this,24)));
        int p=Ui.dp(this,12);root.setPadding(p,p,p,Ui.dp(this,8));
        LinearLayout header=Ui.row(this);
        TextView brand=Ui.bold(this,"wisp",22,Ui.MINT);header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        language=small("RU + EN");header.addView(language);
        language.setOnClickListener(v->{if(active())return;String l=settings.language();settings.setLanguage(l.equals("auto")?"ru":l.equals("ru")?"en":"auto");labels();buildLetters();});
        mode=small("Чистый");header.addView(mode);
        mode.setOnClickListener(v->{if(active())return;String m=settings.mode();settings.setMode(switch(m){case "clean"->"message";case "message"->"notes";case "notes"->"raw";default->"clean";});labels();});
        Button setup=small("⚙");setup.setContentDescription("Настройки Wisp");header.addView(setup);setup.setOnClickListener(v->openSettings());root.addView(header);
        Ui.gap(root,8);
        status=Ui.text(this,"Готов к диктовке",12,Ui.MINT);root.addView(status);
        Ui.gap(root,6);
        ScrollView previewScroll=new ScrollView(this);previewScroll.setFillViewport(true);
        preview=Ui.text(this,"Нажмите микрофон и говорите.\nИли удерживайте его как рацию.",16,Ui.TEXT);
        preview.setPadding(Ui.dp(this,12),Ui.dp(this,10),Ui.dp(this,12),Ui.dp(this,10));
        preview.setBackground(Ui.bg(Ui.CARD,Ui.dp(this,16)));
        preview.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        previewScroll.addView(preview);root.addView(previewScroll,Ui.fill(this,82));
        waveform=new WaveformView(this);root.addView(waveform,Ui.fill(this,28));
        LinearLayout voice=Ui.row(this);
        Button cancel=Ui.button(this,"Отмена",false);voice.addView(cancel,new LinearLayout.LayoutParams(0,Ui.dp(this,54),1));
        cancel.setOnClickListener(v->{cancelSession();resetUi();});
        View gap=new View(this);voice.addView(gap,new LinearLayout.LayoutParams(Ui.dp(this,8),1));
        mic=Ui.button(this,"● Запись",true);mic.setContentDescription("Записать голос. Нажмите для начала и остановки или удерживайте.");
        voice.addView(mic,new LinearLayout.LayoutParams(0,Ui.dp(this,60),1.4f));
        mic.setOnTouchListener((v,e)->{
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){
                touchDown=SystemClock.elapsedRealtime();boolean startNew=recorder==null&&!busy;
                if(startNew)startRecording();touchStartedRecording=startNew&&recorder!=null;v.setPressed(true);return true;
            }
            else if(e.getActionMasked()==MotionEvent.ACTION_UP){
                v.setPressed(false);
                if(!touchStartedRecording||SystemClock.elapsedRealtime()-touchDown>=350)stopAndProcess();
                touchStartedRecording=false;suppressMicClick=true;v.performClick();return true;
            } else if(e.getActionMasked()==MotionEvent.ACTION_CANCEL){v.setPressed(false);if(touchStartedRecording){cancelSession();resetUi();}return true;}
            return true;
        });
        // A short tap starts recording on ACTION_DOWN and leaves it running.
        mic.setOnClickListener(v->{
            if(suppressMicClick){suppressMicClick=false;return;}
            if(recorder!=null)stopAndProcess();else startRecording();
        });
        View gap2=new View(this);voice.addView(gap2,new LinearLayout.LayoutParams(Ui.dp(this,8),1));
        insert=Ui.button(this,"Вставить",true);voice.addView(insert,new LinearLayout.LayoutParams(0,Ui.dp(this,54),1));
        insert.setOnClickListener(v->insertPreview());root.addView(voice);
        LinearLayout transcript=Ui.row(this);rawButton=Ui.button(this,"Показать исходный",false);rawButton.setTextSize(12);
        rawButton.setOnClickListener(v->{rawShown=!rawShown;showPreview();});transcript.addView(rawButton,new LinearLayout.LayoutParams(0,Ui.dp(this,40),1));
        lettersButton=Ui.button(this,"ABC",false);lettersButton.setContentDescription("Открыть или скрыть буквенную клавиатуру");
        lettersButton.setOnClickListener(v->{lettersShown=!lettersShown;buildLetters();});transcript.addView(lettersButton,new LinearLayout.LayoutParams(Ui.dp(this,64),Ui.dp(this,40)));
        Ui.gap(root,8);root.addView(transcript);Ui.gap(root,8);
        letters=Ui.column(this);root.addView(letters);buildLetters();
        LinearLayout bottom=Ui.row(this);
        Button globe=bottomKey(bottom,"◎",1,()->{cancelSession();if(!switchToPreviousInputMethod())getSystemService(InputMethodManager.class).showInputMethodPicker();});
        globe.setContentDescription("Вернуться к предыдущей клавиатуре. Удерживайте для выбора клавиатуры.");
        globe.setOnLongClickListener(v->{getSystemService(InputMethodManager.class).showInputMethodPicker();return true;});
        Button undo=bottomKey(bottom,"↶",1,this::undo);undo.setContentDescription("Отменить последнюю вставку Wisp");
        bottomKey(bottom,"Пробел",2.4f,()->type(" "));
        Button delete=bottomKey(bottom,"⌫",1,this::delete);delete.setContentDescription("Удалить символ или выделение");
        final Runnable repeat=new Runnable(){public void run(){delete();handler.postDelayed(this,65);}};
        delete.setOnTouchListener((v,e)->{
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){delete();handler.postDelayed(repeat,400);return true;}
            if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){handler.removeCallbacks(repeat);if(e.getActionMasked()==MotionEvent.ACTION_UP){suppressDeleteClick=true;v.performClick();}return true;}
            return false;
        });
        // Touch repeat already deletes on down; performClick preserves accessibility without a second deletion.
        delete.setOnClickListener(v->{if(suppressDeleteClick){suppressDeleteClick=false;return;}delete();});
        Button enter=bottomKey(bottom,"↵",1,this::enter);enter.setContentDescription("Ввод или действие поля");root.addView(bottom);
        labels();resetUi();return root;
    }
    private Button small(String text){Button b=Ui.button(this,text,false);b.setTextSize(12);b.setMinWidth(0);b.setMinimumWidth(0);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,Ui.dp(this,42));p.leftMargin=Ui.dp(this,5);b.setLayoutParams(p);return b;}
    private Button bottomKey(LinearLayout row,String text,float weight,Runnable action){
        Button b=Ui.button(this,text,false);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(0,0,0,0);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,Ui.dp(this,48),weight);p.setMargins(Ui.dp(this,2),0,Ui.dp(this,2),0);row.addView(b,p);
        b.setOnClickListener(v->action.run());return b;
    }
    private void labels(){
        if(language==null)return;
        language.setText(switch(settings.language()){case "ru"->"RU";case "en"->"EN";default->"RU + EN";});
        mode.setText(switch(settings.mode()){case "message"->"Письмо";case "notes"->"Заметка";case "raw"->"Без ИИ";default->"Чистый";});
    }
    private void buildLetters(){
        if(letters==null)return;letters.removeAllViews();letters.setVisibility(lettersShown?View.VISIBLE:View.GONE);if(!lettersShown)return;
        String[] rows="en".equals(settings.language())?new String[]{"1234567890","qwertyuiop","asdfghjkl","zxcvbnm"}:new String[]{"1234567890","йцукенгшщзхъ","фывапролджэ","ячсмитьбюё"};
        for(String text:rows){LinearLayout row=Ui.row(this);for(int i=0;i<text.length();i++){
            String c=String.valueOf(text.charAt(i));String label=shifted?c.toUpperCase(Locale.ROOT):c;
            Button b=bottomKey(row,label,1,()->type(label));b.setTextSize(15);b.setBackground(Ui.bg(Ui.CARD,Ui.dp(this,9)));
        } letters.addView(row);Ui.gap(letters,4);}
        LinearLayout punctuation=Ui.row(this);bottomKey(punctuation,"⇧",1,()->{shifted=!shifted;buildLetters();});
        for(String c:new String[]{",",".","?","!","-","@"})bottomKey(punctuation,c,1,()->type(c));letters.addView(punctuation);Ui.gap(letters,6);
    }
    @Override public void onStartInput(EditorInfo info,boolean restarting){
        super.onStartInput(info,restarting);cancelSession();sensitive=info==null||DictationText.sensitive(info.inputType);lastInserted="";resetUi();
    }
    @Override public void onStartInputView(EditorInfo info,boolean restarting){super.onStartInputView(info,restarting);labels();resetUi();}
    private boolean active(){return recorder!=null||busy;}
    private void resetUi(){
        if(status==null)return;status.setText(sensitive?"Защищённое поле · диктовка отключена":"Готов · до 2 минут на запись");
        status.setTextColor(Ui.MINT);preview.setText(sensitive?"Для пароля используйте обычную клавиатуру. Кнопка ◎ вернёт вас обратно.":"Нажмите микрофон и говорите.\nИли удерживайте его как рацию.");
        mic.setEnabled(!sensitive);mic.setText("● Запись");mic.setBackground(Ui.bg(Ui.MINT,Ui.dp(this,16)));
        insert.setEnabled(false);rawButton.setVisibility(View.GONE);waveform.clear();
    }
    @SuppressWarnings("deprecation")
    private void startRecording(){
        if(active()||sensitive)return;
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){error("Разрешите микрофон в приложении Wisp (⚙).");return;}
        if(!settings.hasKey()){error("Сначала сохраните API-ключ Groq в приложении Wisp (⚙).");return;}
        cancelSession();
        try{
            audio=File.createTempFile("wisp-audio-",".m4a",getCacheDir());
            recorder=Build.VERSION.SDK_INT>=31?new MediaRecorder(this):new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);recorder.setAudioChannels(1);recorder.setAudioSamplingRate(16000);
            recorder.setAudioEncodingBitRate(64000);recorder.setOutputFile(audio.getAbsolutePath());recorder.setMaxDuration(120000);
            recorder.setOnInfoListener((r,what,extra)->{if(what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED)stopAndProcess();});
            recorder.setOnErrorListener((r,what,extra)->{cancelSession();error("Микрофон прервал запись. Попробуйте ещё раз.");});
            recorder.prepare();recorder.start();started=SystemClock.elapsedRealtime();
            mic.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);mic.setText("■ Стоп");
            mic.setBackground(Ui.bg(Ui.ERROR,Ui.dp(this,16)));insert.setEnabled(false);rawButton.setVisibility(View.GONE);
            preview.setText("Слушаю…\nНажмите ещё раз, чтобы закончить.");handler.post(tick);
        }catch(Exception e){cancelSession();error("Не удалось включить микрофон. Проверьте разрешение и другие приложения записи.");}
    }
    private final Runnable tick=new Runnable(){public void run(){
        if(recorder==null)return;long seconds=(SystemClock.elapsedRealtime()-started)/1000;
        status.setText(String.format(Locale.ROOT,"● Запись  %02d:%02d / 02:00",seconds/60,seconds%60));
        try{waveform.add((float)Math.min(1,Math.log1p(recorder.getMaxAmplitude())/11));}catch(RuntimeException ignored){}
        handler.postDelayed(this,90);
    }};
    private void stopAndProcess(){
        if(recorder==null)return;long duration=SystemClock.elapsedRealtime()-started;
        MediaRecorder r=recorder;recorder=null;handler.removeCallbacks(tick);
        try{r.stop();}catch(RuntimeException e){r.release();cancelSession();error("Запись слишком короткая. Говорите хотя бы секунду.");return;}
        r.release();waveform.clear();
        if(duration<650){cancelSession();error("Запись слишком короткая. Говорите хотя бы секунду.");return;}
        busy=true;mic.setEnabled(false);mic.setText("···");status.setText("Распознаю через Whisper…");preview.setText("Аудио отправляется в Groq. Можно отменить.");
        final int token=generation;final File recorded=audio;
        final String lang=settings.language(),modeValue=settings.mode(),vocabulary=settings.vocabulary();final boolean turbo=settings.turbo();
        final GroqClient api=new GroqClient();activeApi=api;
        request=worker.submit(()->{
            try{
                DictationPipeline.Result result=DictationPipeline.run(api,recorded,settings.readKey(),lang,turbo,vocabulary,modeValue,
                    original->post(token,()->{status.setText("ИИ очищает текст…");preview.setText(original);}));
                String original=result.original(),finalText=result.text(),notice=result.warning();
                post(token,()->{busy=false;raw=original;edited=finalText;rawShown=false;showPreview();
                    status.setText(notice.isEmpty()?"Готово · проверьте текст перед вставкой":notice);status.setTextColor(notice.isEmpty()?Ui.MINT:Ui.ERROR);
                    mic.setEnabled(!sensitive);mic.setText("● Ещё раз");mic.setBackground(Ui.bg(Ui.MINT,Ui.dp(this,16)));insert.setEnabled(true);
                    rawButton.setVisibility(raw.equals(edited)?View.GONE:View.VISIBLE);
                });
            }catch(Exception e){post(token,()->{busy=false;error(GroqClient.friendlyError(e));});}
            finally{recorded.delete();}
        });
    }
    private void post(int token,Runnable action){handler.post(()->{if(token==generation)action.run();});}
    private void showPreview(){preview.setText(rawShown?raw:edited);rawButton.setText(rawShown?"Показать очищенный":"Показать исходный");}
    private void error(String message){if(status==null)return;status.setText("Не получилось");status.setTextColor(Ui.ERROR);preview.setText(message);mic.setEnabled(!sensitive);mic.setText("● Запись");mic.setBackground(Ui.bg(Ui.MINT,Ui.dp(this,16)));insert.setEnabled(false);}
    private void insertPreview(){
        InputConnection ic=getCurrentInputConnection();String value=rawShown?raw:edited;
        if(ic==null||sensitive||value.isBlank())return;
        CharSequence selected=ic.getSelectedText(0);
        String insertion=DictationText.insertion(value,ic.getTextBeforeCursor(2,0),selected!=null&&selected.length()>0);
        if(ic.commitText(insertion,1)){lastInserted=selected!=null&&selected.length()>0?"":insertion;raw="";edited="";insert.setEnabled(false);rawButton.setVisibility(View.GONE);status.setText("Вставлено ✓");}
        else error("Поле не приняло текст. Откройте его заново и повторите запись.");
    }
    private void undo(){InputConnection ic=getCurrentInputConnection();if(ic==null||lastInserted.isEmpty())return;
        CharSequence selection=ic.getSelectedText(0),before=ic.getTextBeforeCursor(lastInserted.length(),0);
        if((selection==null||selection.length()==0)&&lastInserted.contentEquals(before==null?"":before)){ic.deleteSurroundingText(lastInserted.length(),0);status.setText("Последняя вставка отменена");}
        else status.setText("Курсор или текст изменён — отмена недоступна");lastInserted="";
    }
    private void type(String text){InputConnection ic=getCurrentInputConnection();if(ic!=null){ic.commitText(text,1);lastInserted="";}}
    private void delete(){InputConnection ic=getCurrentInputConnection();if(ic==null)return;lastInserted="";
        CharSequence selected=ic.getSelectedText(0);if(selected!=null&&selected.length()>0)ic.commitText("",1);else ic.deleteSurroundingTextInCodePoints(1,0);
    }
    private void enter(){InputConnection ic=getCurrentInputConnection();EditorInfo info=getCurrentInputEditorInfo();if(ic==null||info==null)return;lastInserted="";
        int action=info.imeOptions&EditorInfo.IME_MASK_ACTION;
        if((info.imeOptions&EditorInfo.IME_FLAG_NO_ENTER_ACTION)==0&&action!=EditorInfo.IME_ACTION_NONE&&action!=EditorInfo.IME_ACTION_UNSPECIFIED)ic.performEditorAction(action);
        else ic.commitText("\n",1);
    }
    private void openSettings(){cancelSession();startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
    private void cancelSession(){
        generation++;handler.removeCallbacksAndMessages(null);
        if(recorder!=null){try{recorder.stop();}catch(RuntimeException ignored){}recorder.release();recorder=null;}
        if(request!=null){request.cancel(true);request=null;}
        if(activeApi!=null){activeApi.cancel();activeApi=null;}
        if(audio!=null){audio.delete();audio=null;}
        busy=false;raw="";edited="";rawShown=false;touchStartedRecording=false;
    }
    @Override public void onFinishInputView(boolean finishingInput){cancelSession();lastInserted="";resetUi();super.onFinishInputView(finishingInput);}
    @Override public void onFinishInput(){cancelSession();lastInserted="";super.onFinishInput();}
    @Override public void onWindowHidden(){cancelSession();lastInserted="";resetUi();super.onWindowHidden();}
    @Override public void onDestroy(){cancelSession();worker.shutdownNow();super.onDestroy();}
}
