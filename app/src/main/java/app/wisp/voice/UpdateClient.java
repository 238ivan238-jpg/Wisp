package app.wisp.voice;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.function.IntConsumer;

/** No Groq credentials or dictation data enter this client. */
final class UpdateClient {
    interface Transport {
        InputStream open(String url) throws IOException;
        default void cancel() {}
    }
    private final Transport transport;
    private volatile boolean cancelled;
    UpdateClient(){this(new HttpsTransport());}
    UpdateClient(Transport transport){this.transport=transport;}
    void cancel(){cancelled=true;transport.cancel();}
    private void checkCancelled() throws InterruptedIOException {
        if(cancelled||Thread.currentThread().isInterrupted())throw new InterruptedIOException("Обновление отменено.");
    }
    UpdateInfo check() throws IOException {
        checkCancelled();
        try(InputStream in=transport.open(UpdateInfo.FEED+"?check="+System.currentTimeMillis())){
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;
            while((n=in.read(buffer))!=-1){checkCancelled();if(bytes.size()+n>16384)throw new IOException("Слишком большой ответ сервера обновлений.");bytes.write(buffer,0,n);}
            return UpdateInfo.parse(bytes.toString(StandardCharsets.UTF_8.name()));
        }
    }
    File download(UpdateInfo info,File directory,IntConsumer progress) throws Exception {
        checkCancelled();if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Нет места для обновления.");
        File target=new File(directory,"Wisp-"+info.versionCode()+".apk");
        if(target.isFile()&&matches(target,info)){progress.accept(100);return target;}
        File part=File.createTempFile("Wisp-"+info.versionCode()+"-",".part",directory);boolean complete=false;
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");long total=0;int lastProgress=-1;
            try(InputStream in=transport.open(info.url());OutputStream out=new FileOutputStream(part)){
                byte[] buffer=new byte[16384];int n;
                while((n=in.read(buffer))!=-1){
                    checkCancelled();total+=n;if(total>info.size()||total>UpdateInfo.MAX_APK_SIZE)throw new IOException("Размер обновления не совпадает. Повторите проверку.");
                    out.write(buffer,0,n);digest.update(buffer,0,n);
                    int percent=(int)(total*100/info.size());if(percent!=lastProgress){lastProgress=percent;progress.accept(percent);}
                }
            }
            if(total!=info.size()||!hex(digest.digest()).equals(info.sha256()))throw new IOException("Файл обновления повреждён или изменён. Установка отменена.");
            checkCancelled();if(target.exists()&&!target.delete())throw new IOException("Не удалось заменить файл обновления.");
            if(!part.renameTo(target))throw new IOException("Не удалось сохранить обновление.");
            complete=true;return target;
        } finally {if(!complete)part.delete();}
    }
    static boolean matches(File file,UpdateInfo info) throws Exception {
        if(file.length()!=info.size())return false;
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(file)){byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);}
        return hex(digest.digest()).equals(info.sha256());
    }
    static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return s.toString();}
    private static final class HttpsTransport implements Transport {
        private volatile HttpURLConnection active;
        private volatile boolean cancelled;
        public void cancel(){cancelled=true;HttpURLConnection c=active;if(c!=null)c.disconnect();}
        public InputStream open(String address) throws IOException {
            for(int redirects=0;redirects<4;redirects++){
                if(cancelled)throw new InterruptedIOException();
                URL url=new URL(address);
                if(!"https".equals(url.getProtocol())||!"raw.githubusercontent.com".equals(url.getHost())
                    ||url.getUserInfo()!=null||(url.getPort()!=-1&&url.getPort()!=443))throw new IOException("Неподдерживаемый адрес обновления.");
                HttpURLConnection connection=(HttpURLConnection)url.openConnection();active=connection;
                connection.setConnectTimeout(15000);connection.setReadTimeout(30000);connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("Cache-Control","no-cache");connection.setRequestProperty("User-Agent","Wisp-Updater");
                if(cancelled){connection.disconnect();throw new InterruptedIOException();}
                int status;
                try{status=connection.getResponseCode();}catch(IOException e){connection.disconnect();active=null;throw e;}
                if(status==301||status==302||status==303||status==307||status==308){
                    String next=connection.getHeaderField("Location");connection.disconnect();active=null;
                    if(next==null)throw new IOException("Сервер обновлений вернул пустой адрес.");address=new URL(url,next).toString();continue;
                }
                if(status!=200){connection.disconnect();active=null;throw new IOException("Сервер обновлений недоступен (HTTP "+status+"). Попробуйте позже.");}
                final InputStream stream;
                try{stream=connection.getInputStream();}catch(IOException e){connection.disconnect();active=null;throw e;}
                return new FilterInputStream(stream){@Override public void close()throws IOException{try{super.close();}finally{connection.disconnect();if(active==connection)active=null;}}};
            }
            throw new IOException("Слишком много перенаправлений сервера обновлений.");
        }
    }
}
