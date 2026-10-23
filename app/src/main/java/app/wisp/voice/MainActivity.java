package app.wisp.voice;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private SecureSettings settings;
    private TextView readiness, feedback;
    private EditText keyField;
    private Button check;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        settings=new SecureSettings(this);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(Ui.BG);
        LinearLayout page=Ui.column(this); int pad=Ui.dp(this,24); page.setPadding(pad,pad,pad,pad);
        // Android 15 enforces edge-to-edge for target 35; retain safe touch areas.
        scroll.setOnApplyWindowInsetsListener((v,insets)->{
            v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom()); return insets;
        });
        scroll.addView(page); setContentView(scroll);
        LinearLayout brand=Ui.row(this);
        ImageView logo=new ImageView(this); logo.setImageResource(app.wisp.voice.R.drawable.ic_launcher);
        logo.setContentDescription("Wisp"); brand.addView(logo,new LinearLayout.LayoutParams(Ui.dp(this,44),Ui.dp(this,44)));
        TextView name=Ui.bold(this,"  wisp",30,Ui.TEXT); brand.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        TextView badge=Ui.text(this,"ДЛЯ PIXEL",10,Ui.MINT); badge.setLetterSpacing(.14f); brand.addView(badge);page.addView(brand);
        Ui.gap(page,28);
        page.addView(Ui.bold(this,"Мысли — голосом.\nТекст — без лишнего.",34,Ui.TEXT));
        Ui.gap(page,10);
        page.addView(Ui.text(this,"Говорите по-русски, in English или вперемешку. Wisp распознает речь и поможет привести текст в порядок.",15,Ui.MUTED));
        Ui.gap(page,24);
        LinearLayout setup=Ui.card(this); page.addView(setup,Ui.fill(this,-2));
        setup.addView(Ui.bold(this,"01   Подготовьте клавиатуру",18,Ui.TEXT)); Ui.gap(setup,10);
        readiness=Ui.text(this,"",13,Ui.MUTED); setup.addView(readiness); Ui.gap(setup,16);
        Button mic=Ui.button(this,"Разрешить микрофон",true); setup.addView(mic,Ui.fill(this,50));
        mic.setOnClickListener(v->{
            if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this,"Микрофон уже разрешён",Toast.LENGTH_SHORT).show();
            } else requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},7);
        });
        Ui.gap(setup,10);
        Button enable=Ui.button(this,"Включить Wisp в настройках Android",false); setup.addView(enable,Ui.fill(this,-2));
        enable.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        Ui.gap(setup,10);
        Button choose=Ui.button(this,"Выбрать клавиатуру Wisp",false); setup.addView(choose,Ui.fill(this,50));
        choose.setOnClickListener(v->getSystemService(InputMethodManager.class).showInputMethodPicker());
        Ui.gap(setup,10);
        TextView permission=Ui.text(this,"Если Android больше не спрашивает разрешение, откройте настройки доступа Wisp.",12,Ui.MINT);
        permission.setPadding(0,Ui.dp(this,6),0,Ui.dp(this,6)); permission.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));
        setup.addView(permission);
        Ui.gap(page,16);
        LinearLayout api=Ui.card(this); page.addView(api,Ui.fill(this,-2));
        api.addView(Ui.bold(this,"02   Подключите Groq",18,Ui.TEXT)); Ui.gap(api,8);
        api.addView(Ui.text(this,"Своего сервера и подписки Wisp нет. Распознавание и очистка работают через ваш аккаунт Groq; действуют его тарифы и лимиты.",13,Ui.MUTED));
        Ui.gap(api,6);
        api.addView(Ui.text(this,"«Проверить» проверяет список моделей и очистку короткой тестовой фразы. Аудио не записывается.",12,Ui.MUTED));
        Ui.gap(api,12);
        Button getKey=Ui.button(this,"Создать ключ на console.groq.com ↗",false); api.addView(getKey,Ui.fill(this,-2));
        getKey.setOnClickListener(v->{
            try { startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://console.groq.com/keys"))); }
            catch(android.content.ActivityNotFoundException e) { Toast.makeText(this,"Откройте console.groq.com/keys в браузере",Toast.LENGTH_LONG).show(); }
        });
        Ui.gap(api,12);
        keyField=new EditText(this); keyField.setTextColor(Ui.TEXT); keyField.setHintTextColor(Ui.MUTED);
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
            catch(Exception e) { report("Не удалось защитить ключ в Android Keystore. Ключ не сохранён.",false); }
        });
        check.setOnClickListener(v->{
            String entered=ApiKey.normalize(keyField.getText().toString());
            check.setEnabled(false); report("Проверяем доступ к Groq…",true);
            worker.submit(()->{
                String message; boolean ok;
                try {
                    String key=entered.isEmpty()?settings.readKey():entered;
                    GroqClient client=new GroqClient();client.checkKey(key);client.clean("Проверка подключения.",key);
                    message=entered.isEmpty()?"Сохранённый ключ работает, Whisper доступен. ИИ-очистка тестовой фразы прошла успешно."
                        :"Ключ и ИИ-очистка работают. Нажмите «Сохранить», чтобы использовать ключ для диктовки."; ok=true;
                } catch(Exception e) { message=GroqClient.friendlyError(e); ok=false; }
                String result=message; boolean success=ok;
                runOnUiThread(()->{ if(!isDestroyed()) { check.setEnabled(true); report(result,success); } });
            });
        });
        feedback=Ui.text(this,"Ключ защищён Android Keystore. Wisp не хранит историю диктовок.",12,Ui.MUTED);
        Ui.gap(api,12); api.addView(feedback);
        Button remove=Ui.button(this,"Удалить ключ",false); Ui.gap(api,10); api.addView(remove,Ui.fill(this,48));
        remove.setOnClickListener(v->{settings.clearKey();keyField.setText("");keyField.setHint("API-ключ Groq (gsk_…)");report("Ключ удалён с устройства.",true);refresh();});
        Ui.gap(page,16);
        LinearLayout options=Ui.card(this);page.addView(options,Ui.fill(this,-2));
        options.addView(Ui.bold(this,"03   Под ваш голос",18,Ui.TEXT));Ui.gap(options,12);
        Switch turbo=new Switch(this);turbo.setText("Быстрый Whisper Turbo");turbo.setTextColor(Ui.TEXT);turbo.setTextSize(14);
        turbo.setChecked(settings.turbo());turbo.setPadding(0,Ui.dp(this,12),0,Ui.dp(this,12));options.addView(turbo,Ui.fill(this,-2));
        turbo.setOnCheckedChangeListener((b,on)->settings.setTurbo(on));
        options.addView(Ui.text(this,"По умолчанию — Whisper Large v3. Режим Turbo можно попробовать для более быстрой обработки.",12,Ui.MUTED));
        Ui.gap(options,14);
        options.addView(Ui.bold(this,"Ваши слова и имена",14,Ui.TEXT));
        EditText vocab=new EditText(this);vocab.setTextColor(Ui.TEXT);vocab.setHintTextColor(Ui.MUTED);vocab.setTextSize(14);
        vocab.setHint("Например: Иван, GitHub, рефакторинг");vocab.setText(settings.vocabulary());vocab.setMaxLines(3);
        vocab.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(500)});options.addView(vocab,Ui.fill(this,-2));
        Button vocabSave=Ui.button(this,"Сохранить словарь",false);Ui.gap(options,8);options.addView(vocabSave,Ui.fill(this,48));
        vocabSave.setOnClickListener(v->{settings.setVocabulary(vocab.getText().toString());Toast.makeText(this,"Словарь сохранён",Toast.LENGTH_SHORT).show();});
        Ui.gap(page,16);
        LinearLayout test=Ui.card(this);page.addView(test,Ui.fill(this,-2));
        test.addView(Ui.bold(this,"Попробуйте прямо здесь",18,Ui.TEXT));Ui.gap(test,8);
        test.addView(Ui.text(this,"Коснитесь поля, выберите Wisp и нажмите микрофон. После распознавания проверьте текст и нажмите «Вставить».",13,Ui.MUTED));
        EditText sandbox=new EditText(this);sandbox.setTextColor(Ui.TEXT);sandbox.setHintTextColor(Ui.MUTED);sandbox.setHint("Здесь появятся ваши слова…");
        sandbox.setMinLines(3);sandbox.setGravity(android.view.Gravity.TOP);sandbox.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        sandbox.setSaveEnabled(false);test.addView(sandbox,Ui.fill(this,-2));
        Ui.gap(page,20);
        page.addView(Ui.text(this,"Только по вашему нажатию: аудио → Groq Whisper, текст → Groq для очистки. Записи удаляются после обработки или отмены. Политика хранения Groq действует отдельно. В полях паролей микрофон отключён.",12,Ui.MUTED));
        Ui.gap(page,12);page.addView(Ui.text(this,"WISP  /  1.0.2     •     СДЕЛАНО ДЛЯ ВАШЕГО ГОЛОСА",10,Ui.MINT));
    }
    private void report(String text,boolean ok) { feedback.setText(text); feedback.setTextColor(ok?Ui.MINT:Ui.ERROR); }
    private void refresh() {
        if(readiness==null)return;
        boolean mic=checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;
        boolean ime=getSystemService(InputMethodManager.class).getEnabledInputMethodList().stream().anyMatch(i->i.getPackageName().equals(getPackageName()));
        readiness.setText((mic?"✓":"○")+" Микрофон     "+(ime?"✓":"○")+" Клавиатура     "+(settings.hasKey()?"✓":"○")+" API-ключ");
    }
    @Override protected void onResume(){super.onResume();refresh();}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);refresh();}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
