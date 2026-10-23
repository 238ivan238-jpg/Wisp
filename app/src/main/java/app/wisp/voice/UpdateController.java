package app.wisp.voice;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import androidx.core.content.FileProvider;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class UpdateController implements AutoCloseable {
    private final Activity activity;
    private final SharedPreferences preferences;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final int currentCode;
    private final String currentName;
    private final TextView status;
    private final Button check,install;
    private UpdateInfo latest;
    private UpdateClient client;
    private boolean busy,closed,awaitingPermission;

    UpdateController(Activity activity,LinearLayout page,Bundle state){
        this.activity=activity;preferences=activity.getSharedPreferences("wisp_updates",Context.MODE_PRIVATE);
        PackageInfo current;
        try{current=UpdateVerifier.installed(activity);}catch(Exception e){throw new IllegalStateException("Installed Wisp metadata unavailable",e);}
        currentCode=(int)current.getLongVersionCode();
        currentName=current.versionName;
        LinearLayout card=Ui.card(activity);page.addView(card,Ui.fill(activity,-2));
        card.addView(Ui.bold(activity,"Обновления · Wisp "+current.versionName,18,Ui.foreground(activity)));Ui.gap(card,8);
        status=Ui.text(activity,"Проверяйте новые версии здесь — без поиска ссылок в чате.",13,Ui.muted(activity));card.addView(status);Ui.gap(card,12);
        check=Ui.button(activity,"Проверить обновления",false);card.addView(check,Ui.fill(activity,48));
        check.setOnClickListener(v->checkNow());
        install=Ui.button(activity,"Скачать и обновить",true);Ui.gap(card,8);card.addView(install,Ui.fill(activity,48));
        install.setVisibility(android.view.View.GONE);install.setOnClickListener(v->beginUpdate());
        Ui.gap(card,10);
        Switch auto=new Switch(activity);auto.setText("Проверять при открытии");auto.setTextColor(Ui.foreground(activity));auto.setTextSize(13);
        auto.setChecked(preferences.getBoolean("auto_check",true));auto.setPadding(0,Ui.dp(activity,10),0,Ui.dp(activity,10));card.addView(auto);
        auto.setOnCheckedChangeListener((button,on)->{preferences.edit().putBoolean("auto_check",on).apply();if(on)checkNow();});
        Ui.gap(card,4);card.addView(Ui.text(activity,"Автопроверка — при открытии Wisp, не чаще раза в 6 часов. Скачивание начинается по кнопке. Установку подтверждает Android.",12,Ui.muted(activity)));
        Ui.gap(page,16);
        String stored=state==null?preferences.getString("latest",""):state.getString("update_latest",preferences.getString("latest",""));
        if(!stored.isEmpty())try{latest=UpdateInfo.parse(stored);showLatest();}catch(Exception ignored){latest=null;}
        awaitingPermission=state!=null&&state.getBoolean("update_permission",false);
        // The installed update no longer needs its own installer cache.
        File[] files=directory().listFiles();if(files!=null)for(File file:files){
            String name=file.getName();
            try{if(name.matches("Wisp-[0-9]+\\.apk")&&Integer.parseInt(name.substring(5,name.length()-4))<=currentCode)file.delete();}
            catch(NumberFormatException ignored){}
            if(name.endsWith(".part")&&System.currentTimeMillis()-file.lastModified()>86400000L)file.delete();
        }
    }
    void onResume(){
        if(awaitingPermission){
            awaitingPermission=false;
            if(activity.getPackageManager().canRequestPackageInstalls()){beginUpdate();return;}
            report("Установка пока не разрешена. Нажмите «Скачать и обновить», чтобы открыть разрешение ещё раз.",false);
        }
        if(preferences.getBoolean("auto_check",true)&&!busy
            &&(preferences.getInt("checked_version",0)!=currentCode||System.currentTimeMillis()-preferences.getLong("checked_at",0)>21600000L))checkNow();
    }
    void save(Bundle out){if(latest!=null)out.putString("update_latest",latest.json());out.putBoolean("update_permission",awaitingPermission);}
    String versionName(){return currentName;}
    private File directory(){return new File(activity.getCacheDir(),"updates");}
    private void post(Runnable action){activity.runOnUiThread(()->{if(!closed&&!activity.isDestroyed())action.run();});}
    private void controls(boolean working){busy=working;check.setEnabled(!working);install.setEnabled(!working);}
    private void report(String value,boolean success){status.setText(value);status.setTextColor(Ui.foreground(activity));}
    private void showLatest(){
        if(latest==null)return;
        if(latest.versionCode()<=currentCode){report("Установлена последняя опубликованная версия.",true);install.setVisibility(android.view.View.GONE);}
        else if(latest.minSdk()>Build.VERSION.SDK_INT){report("Новая версия требует Android API "+latest.minSdk()+" или новее.",false);install.setVisibility(android.view.View.GONE);}
        else{
            report("Доступна версия "+latest.versionName()+(latest.notes().isEmpty()?"":"\n"+latest.notes()),true);
            install.setText("Скачать и обновить до "+latest.versionName());install.setVisibility(android.view.View.VISIBLE);
        }
    }
    private void checkNow(){
        if(busy||closed)return;controls(true);report("Проверяем обновления…",true);client=new UpdateClient();final UpdateClient operation=client;
        worker.submit(()->{
            try{
                UpdateInfo info=operation.check();
                post(()->{latest=info;preferences.edit().putString("latest",info.json()).putLong("checked_at",System.currentTimeMillis())
                    .putInt("checked_version",currentCode).apply();controls(false);showLatest();});
            }catch(Exception e){post(()->{controls(false);report(error(e),false);});}
        });
    }
    private void beginUpdate(){
        if(busy||closed||latest==null||latest.versionCode()<=currentCode||latest.minSdk()>Build.VERSION.SDK_INT)return;
        final UpdateInfo info=latest;controls(true);report("Скачиваем обновление…",true);client=new UpdateClient();final UpdateClient operation=client;
        worker.submit(()->{
            try{
                File file=operation.download(info,directory(),percent->post(()->status.setText("Скачиваем обновление: "+percent+"%")));
                post(()->status.setText("Проверяем файл и подпись…"));
                try{UpdateVerifier.verify(activity,file,info);}catch(Exception e){file.delete();throw e;}
                post(()->{controls(false);openInstaller(file);});
            }catch(Exception e){post(()->{controls(false);report(error(e),false);});}
        });
    }
    private void openInstaller(File file){
        try{
            if(!activity.getPackageManager().canRequestPackageInstalls()){
                awaitingPermission=true;
                report("Разрешите Wisp устанавливать обновления на следующем экране Android. После возврата установка продолжится.",true);
                activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName())));
                return;
            }
            report("Файл проверен. Подтвердите обновление в окне Android.",true);install.setText("Открыть установку ещё раз");
            activity.startActivity(installerIntent(activity,file));
        }catch(android.content.ActivityNotFoundException e){awaitingPermission=false;report("На устройстве не найден системный установщик или экран разрешения.",false);}
        catch(SecurityException e){awaitingPermission=false;report("Android запретил установку обновления. Проверьте разрешение установки для Wisp.",false);}
    }
    static Intent installerIntent(Context context,File apk){
        Uri uri=FileProvider.getUriForFile(context,context.getPackageName()+".updates",apk);
        Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClipData(ClipData.newRawUri("Wisp update",uri));return intent;
    }
    private static String error(Exception e){
        if(e instanceof javax.net.ssl.SSLException)return "Не удалось установить защищённое соединение с GitHub. Попробуйте позже.";
        if(e instanceof java.net.SocketTimeoutException)return "Сервер обновлений не ответил вовремя. Проверьте интернет.";
        String message=e.getMessage();
        return message!=null&&message.matches("(?s).*[А-Яа-яЁё].*")?message:"Не удалось получить обновление. Проверьте интернет и повторите проверку.";
    }
    @Override public void close(){closed=true;if(client!=null)client.cancel();worker.shutdownNow();}
}
