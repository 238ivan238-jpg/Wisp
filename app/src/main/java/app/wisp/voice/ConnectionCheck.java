package app.wisp.voice;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import java.io.IOException;

/** A small reachability/key check, not a promise that a later audio upload will succeed. */
final class ConnectionCheck {
    interface Probe { void check(GroqClient api)throws Exception; }
    static boolean vpn(Context context){
        var cm=context.getSystemService(ConnectivityManager.class);
        var caps=cm.getNetworkCapabilities(cm.getActiveNetwork());
        return caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
    }
    static void check(Context context,SecureSettings settings,GroqClient api)throws Exception{
        var cm=context.getSystemService(ConnectivityManager.class);
        var caps=cm.getNetworkCapabilities(cm.getActiveNetwork());
        if(caps==null||!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))throw new Offline();
        api.checkAccess(settings.readKey(),settings.turbo(),api.needsEditor());
    }
    static final class Offline extends IOException {}
    static String error(Exception e){
        if(e instanceof Offline)return "Нет интернета. Проверьте подключение.";
        return switch(GroqClient.failureCategory(e)){
            case "audio"->"Не удалось подготовить части. Аудио сохранено во вкладке «Записи».";
            case "auth"->"Groq не принял ключ (401). Проверьте его в настройках.";
            case "access"->"Groq запретил доступ (403). Проверьте сеть или VPN.";
            case "limit"->"Лимит Groq исчерпан (429). Повторите позже.";
            case "timeout"->"Groq не ответил вовремя. Проверьте сеть или VPN.";
            case "model"->"Модель недоступна. Проверьте аккаунт Groq.";
            case "tls"->"Ошибка защищённого соединения. Проверьте сеть.";
            default->"Нет ответа от Groq. Проверьте сеть или VPN.";
        };
    }
    private ConnectionCheck(){}
}
