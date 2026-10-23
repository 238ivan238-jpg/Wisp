package app.wisp.voice;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.Gravity;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private SecureSettings settings;
    private TextView readiness,connectionStatus,feedback;
    private EditText keyField,vocabulary;
    private Button check,connect;
    private UpdateController updates;
    private String current="voice";
    private Bundle initial;
    private final Map<String,ScrollView> screens=new HashMap<>();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    @Override public void onCreate(Bundle state){
        super.onCreate(state);settings=new SecureSettings(this);initial=state;
        screen("updates"); // Keep auto-check and installer resumption independent of the visible tab.
        String selected=state==null?sectionName(getIntent().getIntExtra(AppNavigation.EXTRA_SECTION,0)):state.getString("main_page","voice");
        showPage(Set.of("voice","dictionary","settings","connection","key","updates","legacy").contains(selected)?selected:"voice");
    }
    private String sectionName(int section){return section==AppNavigation.DICTIONARY?"dictionary":section==AppNavigation.SETTINGS?"settings":"voice";}
    void showSection(int section){showPage(sectionName(section));}
    private void showPage(String name){
        View focused=getCurrentFocus();if(focused!=null){getSystemService(InputMethodManager.class).hideSoftInputFromWindow(focused.getWindowToken(),0);focused.clearFocus();}
        current=name;int selected=name.equals("voice")?AppNavigation.VOICE:name.equals("dictionary")?AppNavigation.DICTIONARY:AppNavigation.SETTINGS;
        setContentView(AppNavigation.wrap(this,screen(name),selected));refresh();
    }
    private ScrollView screen(String name){
        if(screens.containsKey(name))return screens.get(name);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Ui.background(this));
        LinearLayout page=Ui.column(this);int p=Ui.dp(this,20);page.setPadding(p,Ui.dp(this,16),p,p);scroll.addView(page);screens.put(name,scroll);
        if(!Set.of("voice","dictionary","settings").contains(name)){
            Button back=Ui.button(this,"‹ Настройки",false);page.addView(back,Ui.fill(this,48));back.setOnClickListener(v->showPage("settings"));Ui.gap(page,16);
        }
        String title=switch(name){case "dictionary"->"Словарь";case "settings"->"Настройки";case "connection"->"Подключение";case "key"->"Ключ Groq";case "updates"->"Обновления";case "legacy"->"Клавиатура Wisp";default->"Голос";};
        page.addView(Ui.bold(this,title,26,Ui.foreground(this)));Ui.gap(page,16);
        switch(name){
            case "voice"->voice(page);case "dictionary"->dictionary(page);case "settings"->preferences(page);
            case "connection"->connection(page);case "key"->key(page);case "legacy"->legacy(page);
            case "updates"->updates=new UpdateController(this,page,initial);
        }
        return scroll;
    }
    private void voice(LinearLayout page){
        readiness=Ui.text(this,"",13,Ui.muted(this));page.addView(readiness);Ui.gap(page,8);
        connect=Ui.button(this,"Подключить Wisp",false);page.addView(connect,Ui.fill(this,48));connect.setOnClickListener(v->showPage("connection"));Ui.gap(page,12);
        Button language=Ui.button(this,languageLabel(),false);page.addView(language,Ui.fill(this,48));
        language.setOnClickListener(v->{String lang=settings.language();settings.setLanguage(lang.equals("auto")?"ru":lang.equals("ru")?"en":"auto");language.setText(languageLabel());});Ui.gap(page,12);
        Switch cleanup=toggle(page,"Чистый текст",!settings.mode().equals("raw"));hint(page,"Исправления, пунктуация и списки.");
        Switch emojis=toggle(page,"Добавлять эмодзи",settings.emojis());hint(page,"Больше эмодзи по смыслу, в том числе в переводе.");
        cleanup.setOnCheckedChangeListener((b,on)->{if(!on)emojis.setChecked(false);settings.setMode(on?"clean":"raw");});
        emojis.setOnCheckedChangeListener((b,on)->{if(on)cleanup.setChecked(true);settings.setEmojis(on);});
        Switch turbo=toggle(page,"Быстрый Whisper Turbo",settings.turbo());turbo.setOnCheckedChangeListener((b,on)->settings.setTurbo(on));hint(page,"По умолчанию — Whisper Large v3.");Ui.gap(page,12);
        page.addView(Ui.bold(this,"Перевод — по кнопке EN",14,Ui.foreground(this)));hint(page,"Микрофон — без перевода. EN снизу — на английский.");
    }
    private Switch toggle(LinearLayout page,String title,boolean checked){
        Switch control=new Switch(this);control.setText(title);control.setTextSize(15);control.setTextColor(Ui.foreground(this));control.setMinHeight(Ui.dp(this,48));control.setChecked(checked);page.addView(control,Ui.fill(this,48));return control;
    }
    private void hint(LinearLayout page,String text){page.addView(Ui.text(this,text,12,Ui.muted(this)));Ui.gap(page,8);}
    private void link(LinearLayout page,String title,Runnable action){
        LinearLayout row=Ui.row(this);row.setTag("link_"+title);row.setPadding(Ui.dp(this,4),0,Ui.dp(this,4),0);row.setClickable(true);row.setFocusable(true);row.setContentDescription(title);
        row.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Ui.border(this)),null,Ui.bg(Ui.foreground(this),Ui.dp(this,12))));
        TextView label=Ui.text(this,title,16,Ui.foreground(this));label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);row.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        ImageView arrow=new ImageView(this);arrow.setImageResource(R.drawable.ic_arrow_back);arrow.setRotation(180);arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);row.addView(arrow,new LinearLayout.LayoutParams(Ui.dp(this,18),Ui.dp(this,18)));
        row.setAccessibilityDelegate(new View.AccessibilityDelegate(){@Override public void onInitializeAccessibilityNodeInfo(View host,android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(host,info);info.setClassName(Button.class.getName());}});
        page.addView(row,Ui.fill(this,56));row.setOnClickListener(v->action.run());View line=new View(this);line.setBackgroundColor(Ui.border(this));page.addView(line,Ui.fill(this,1));
    }
    private void preferences(LinearLayout page){
        link(page,"Подключение",()->showPage("connection"));link(page,"Ключ Groq",()->showPage("key"));
        link(page,"Обновления",()->showPage("updates"));link(page,"Клавиатура Wisp",()->showPage("legacy"));
    }
    private void connection(LinearLayout page){
        connectionStatus=Ui.text(this,"",13,Ui.muted(this));page.addView(connectionStatus);Ui.gap(page,12);
        Button mic=Ui.button(this,"Разрешить микрофон",false);page.addView(mic,Ui.fill(this,48));mic.setOnClickListener(v->{
            if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)Toast.makeText(this,"Микрофон уже разрешён",Toast.LENGTH_SHORT).show();
            else requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},7);
        });Ui.gap(page,8);
        Button companion=Ui.button(this,"Включить микрофон поверх Gboard",false);companion.setEnabled(android.os.Build.VERSION.SDK_INT>=33);page.addView(companion,Ui.fill(this,-2));
        companion.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));Ui.gap(page,8);
        Button gboard=Ui.button(this,"Выбрать Gboard",false);page.addView(gboard,Ui.fill(this,48));gboard.setOnClickListener(v->getSystemService(InputMethodManager.class).showInputMethodPicker());Ui.gap(page,8);
        link(page,"Ключ Groq",()->showPage("key"));
        LinearLayout help=Ui.column(this);hint(help,"В специальных возможностях включите «Wisp · микрофон поверх Gboard». Требуется Android 13 или новее.");
        hint(help,"Если включение заблокировано: сведения о Wisp → ⋮ → «Разрешить ограниченные настройки». Затем включите службу ещё раз.");
        Button access=Ui.button(this,"Открыть сведения о Wisp",false);help.addView(access,Ui.fill(this,48));access.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));
        Ui.disclosure(page,"Помощь с подключением",help);
    }
    private void legacy(LinearLayout page){
        hint(page,"Запасной вариант для Android 9+. Заменяет Gboard на время диктовки.");
        Button enable=Ui.button(this,"Включить Wisp в настройках Android",false);page.addView(enable,Ui.fill(this,-2));enable.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));Ui.gap(page,8);
        Button choose=Ui.button(this,"Выбрать клавиатуру Wisp",false);page.addView(choose,Ui.fill(this,48));choose.setOnClickListener(v->getSystemService(InputMethodManager.class).showInputMethodPicker());
    }
    private void dictionary(LinearLayout page){
        hint(page,"Имена и термины, которые Wisp должен писать правильно.");vocabulary=new EditText(this);vocabulary.setTag("dictionary_input");vocabulary.setTextColor(Ui.foreground(this));vocabulary.setHintTextColor(Ui.muted(this));vocabulary.setTextSize(16);
        vocabulary.setHint("Например: Иван, GitHub, рефакторинг");vocabulary.setGravity(Gravity.TOP);vocabulary.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);vocabulary.setSaveEnabled(false);
        vocabulary.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(500)});vocabulary.setText(initial==null?settings.vocabulary():initial.getString("dictionary_draft",settings.vocabulary()));
        page.addView(vocabulary,Ui.fill(this,200));Ui.gap(page,12);Button save=Ui.button(this,"Сохранить словарь",true);page.addView(save,Ui.fill(this,48));save.setOnClickListener(v->{settings.setVocabulary(vocabulary.getText().toString());Toast.makeText(this,"Словарь сохранён",Toast.LENGTH_SHORT).show();});
    }
    private void key(LinearLayout api){
        hint(api,"Голос и текст обрабатываются в вашем аккаунте Groq.");
        Button getKey=Ui.button(this,"Создать ключ на console.groq.com ↗",false); api.addView(getKey,Ui.fill(this,-2));
        getKey.setOnClickListener(v->{
            try { startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://console.groq.com/keys"))); }
            catch(android.content.ActivityNotFoundException e) { Toast.makeText(this,"Откройте console.groq.com/keys в браузере",Toast.LENGTH_LONG).show(); }
        });
        Ui.gap(api,12);
        keyField=new EditText(this); keyField.setTextColor(Ui.foreground(this)); keyField.setHintTextColor(Ui.muted(this));
        keyField.setHint(settings.hasKey()?"Ключ сохранён · введите новый для замены":"API-ключ Groq (gsk_…)");
        keyField.setTextSize(14); keyField.setSingleLine(true);
        keyField.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyField.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        keyField.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        keyField.setSaveEnabled(false); api.addView(keyField,Ui.fill(this,56));
        LinearLayout buttons=Ui.row(this);
        Button save=Ui.button(this,"Сохранить",true); buttons.addView(save,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));
        View spacer=new View(this); buttons.addView(spacer,new LinearLayout.LayoutParams(Ui.dp(this,8),1));
        check=Ui.button(this,"Проверить",false); buttons.addView(check,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1)); api.addView(buttons);
        save.setOnClickListener(v->{
            String value=ApiKey.normalize(keyField.getText().toString());
            if(value.isEmpty()) { report("Введите новый ключ или нажмите «Удалить ключ».",false); return; }
            try { settings.saveKey(value); keyField.setText(""); keyField.setHint("Ключ сохранён · введите новый для замены"); report("Ключ зашифрован и сохранён на устройстве.",true); refresh(); }
            catch(Exception e) {new DiagnosticLog(this).failure(DiagnosticLog.session(),DiagnosticLog.Stage.KEY_STORAGE,settings.mode(),e);report("Не удалось защитить ключ в Android Keystore. Ключ не сохранён.",false); }
        });
        check.setOnClickListener(v->{
            String entered=ApiKey.normalize(keyField.getText().toString());
            boolean emojiStyle=settings.emojis();
            DiagnosticLog journal=new DiagnosticLog(this);String trace=DiagnosticLog.session(),selectedMode="clean";
            journal.event(trace,DiagnosticLog.Stage.KEY_CHECK,DiagnosticLog.Outcome.START,selectedMode,0,-1,-1);
            check.setEnabled(false); report("Проверяем доступ к Groq…",true);
            worker.submit(()->{
                String message; boolean ok;
                try {
                    String key=entered.isEmpty()?settings.readKey():entered;
                    GroqClient client=new GroqClient(journal,trace,selectedMode);client.checkKey(key);
                    client.clean("Проверка подключения.",key,"",emojiStyle);
                    message=entered.isEmpty()?"Сохранённый ключ работает, Whisper доступен. Текстовая модель ответила на тестовую фразу."
                        :"Ключ работает, текстовая модель ответила. Нажмите «Сохранить», чтобы использовать ключ для диктовки."; ok=true;
                    journal.event(trace,DiagnosticLog.Stage.KEY_CHECK,DiagnosticLog.Outcome.SUCCESS,selectedMode,0,-1,-1);
                } catch(Exception e) { journal.failure(trace,DiagnosticLog.Stage.KEY_CHECK,selectedMode,e);message=GroqClient.friendlyError(e); ok=false; }
                String result=message; boolean success=ok;
                runOnUiThread(()->{ if(!isDestroyed()) { check.setEnabled(true); report(result,success); } });
            });
        });
        feedback=Ui.text(this,"Ключ хранится зашифрованным на телефоне.",12,Ui.muted(this));
        Ui.gap(api,12); api.addView(feedback);
        Button remove=Ui.button(this,"Удалить ключ",false); Ui.gap(api,10); api.addView(remove,Ui.fill(this,48));
        remove.setOnClickListener(v->{settings.clearKey();keyField.setText("");keyField.setHint("API-ключ Groq (gsk_…)");report("Ключ удалён с устройства.",true);refresh();});
    }
    private String languageLabel(){return "Язык: "+switch(settings.language()){case "ru"->"русский";case "en"->"English";default->"русский + English (авто)";};}
    private void report(String text,boolean ok){if(feedback!=null){feedback.setText(text);feedback.setTextColor(Ui.foreground(this));}}
    private void refresh(){
        boolean mic=checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;
        boolean companion=getSystemService(android.view.accessibility.AccessibilityManager.class).getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK).stream().anyMatch(i->i.getResolveInfo()!=null&&i.getResolveInfo().serviceInfo!=null&&(getPackageName()+".GboardCompanionService").equals(i.getResolveInfo().serviceInfo.name));
        boolean ready=mic&&companion&&settings.hasKey();if(readiness!=null)readiness.setText(ready?"Готов к диктовке · до 5 минут":"Настройте подключение для диктовки");if(connect!=null)connect.setVisibility(ready?View.GONE:View.VISIBLE);
        if(connectionStatus!=null)connectionStatus.setText((mic?"✓":"○")+" Микрофон   "+(companion?"✓":"○")+" Помощник   "+(settings.hasKey()?"✓":"○")+" Ключ");
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);showSection(intent.getIntExtra(AppNavigation.EXTRA_SECTION,0));}
    @Override protected void onResume(){super.onResume();refresh();if(updates!=null)updates.onResume();}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("main_page",current);if(vocabulary!=null)state.putString("dictionary_draft",vocabulary.getText().toString());if(updates!=null)updates.save(state);}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);refresh();}
    @Override public void onBackPressed(){if(!Set.of("voice","dictionary","settings").contains(current))showPage("settings");else if(!current.equals("voice"))showPage("voice");else super.onBackPressed();}
    @Override protected void onDestroy(){if(updates!=null)updates.close();worker.shutdownNow();super.onDestroy();}
}
