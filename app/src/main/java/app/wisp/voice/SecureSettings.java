package app.wisp.voice;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;

final class SecureSettings {
    private static final String ALIAS="wisp_groq_api_v1";
    private final SharedPreferences prefs;
    SecureSettings(Context c) { prefs=c.getSharedPreferences("wisp_private", Context.MODE_PRIVATE); }
    private SecretKey key() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if (ks.containsAlias(ALIAS)) return (SecretKey)ks.getKey(ALIAS, null);
        KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        g.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return g.generateKey();
    }
    synchronized void saveKey(String value) throws Exception {
        value=ApiKey.normalize(value);
        if (value.isEmpty()) { clearKey(); return; }
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key());
        String iv=Base64.encodeToString(c.getIV(),Base64.NO_WRAP);
        String data=Base64.encodeToString(c.doFinal(value.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
        if (!prefs.edit().putString("api_iv",iv).putString("api_data",data).commit()) throw new java.io.IOException("Не удалось сохранить ключ");
    }
    synchronized String readKey() throws Exception {
        String data=prefs.getString("api_data", ""); if (data.isEmpty()) return "";
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(prefs.getString("api_iv",""),Base64.NO_WRAP)));
        return new String(c.doFinal(Base64.decode(data,Base64.NO_WRAP)),StandardCharsets.UTF_8);
    }
    boolean hasKey() { return !prefs.getString("api_data", "").isEmpty(); }
    void clearKey() { prefs.edit().remove("api_iv").remove("api_data").apply(); }
    String language() { return prefs.getString("language","auto"); }
    void setLanguage(String v) { prefs.edit().putString("language",v).apply(); }
    String mode() { return prefs.getString("mode","clean"); }
    void setMode(String v) { prefs.edit().putString("mode",v).apply(); }
    String vocabulary() { return prefs.getString("vocab", ""); }
    void setVocabulary(String v) { prefs.edit().putString("vocab",v.trim()).apply(); }
    boolean turbo() { return prefs.getBoolean("turbo",false); }
    void setTurbo(boolean v) { prefs.edit().putBoolean("turbo",v).apply(); }
}
