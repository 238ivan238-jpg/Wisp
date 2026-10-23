package app.wisp.voice;

import org.json.JSONObject;
import java.io.IOException;
import java.net.URI;

record UpdateInfo(int versionCode,String versionName,int minSdk,String url,String sha256,long size,String notes) {
    static final String REPOSITORY="238ivan238-jpg/Wisp";
    static final String BRANCH="codex/wisp-android-v1";
    static final String FEED="https://raw.githubusercontent.com/"+REPOSITORY+"/"+BRANCH+"/downloads/latest.json";
    static final long MAX_APK_SIZE=100L*1024*1024;
    static UpdateInfo parse(String json) throws IOException {
        try {
            JSONObject o=new JSONObject(json);
            long code=o.getLong("versionCode");long bytes=o.getLong("size");int sdk=o.getInt("minSdk");
            String version=o.getString("versionName"),hash=o.getString("sha256");
            if(o.getInt("schemaVersion")!=1||!"app.wisp.voice".equals(o.getString("packageName"))
                ||code<1||code>Integer.MAX_VALUE||bytes<1||bytes>MAX_APK_SIZE||sdk<28||sdk>100
                ||!version.matches("[0-9]+(?:\\.[0-9]+){1,3}(?:[-+][a-zA-Z0-9.-]+)?")||!hash.matches("[0-9a-fA-F]{64}"))
                throw new IOException("Некорректные сведения об обновлении.");
            String url=o.getString("apkUrl");URI u=new URI(url);
            String expected="/"+REPOSITORY+"/"+BRANCH+"/downloads/Wisp-"+version+".apk";
            if(!"https".equals(u.getScheme())||!"raw.githubusercontent.com".equals(u.getHost())
                ||!expected.equals(u.getRawPath())||u.getRawQuery()!=null||u.getFragment()!=null
                ||u.getUserInfo()!=null||u.getPort()!=-1)throw new IOException("Неизвестный источник обновления.");
            String notes=o.optString("notes","");if(notes.length()>2000)notes=notes.substring(0,2000);
            return new UpdateInfo((int)code,version,sdk,url,hash.toLowerCase(java.util.Locale.ROOT),bytes,notes);
        } catch(IOException e){throw e;}
        catch(Exception e){throw new IOException("Не удалось прочитать сведения об обновлении.");}
    }
    String json(){
        try{return new JSONObject().put("schemaVersion",1).put("packageName","app.wisp.voice")
            .put("versionCode",versionCode).put("versionName",versionName).put("minSdk",minSdk)
            .put("apkUrl",url).put("sha256",sha256).put("size",size).put("notes",notes).toString();}
        catch(Exception e){throw new IllegalStateException(e);}
    }
}
