package app.wisp.voice;

import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class UpdateClientTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private final byte[] data="sample APK bytes for transport tests".getBytes(StandardCharsets.UTF_8);
    private UpdateInfo info()throws Exception{return new UpdateInfo(5,"1.0.4",28,
        "https://raw.githubusercontent.com/238ivan238-jpg/Wisp/codex/wisp-android-v1/downloads/Wisp-1.0.4.apk",
        UpdateClient.hex(MessageDigest.getInstance("SHA-256").digest(data)),data.length,"Изменения");}
    @Test public void validManifestRoundTripsAndChecksLatestFeed()throws Exception{
        UpdateInfo expected=info();
        UpdateClient client=new UpdateClient(url->{assertTrue(url.startsWith(UpdateInfo.FEED+"?check="));return new ByteArrayInputStream(expected.json().getBytes(StandardCharsets.UTF_8));});
        assertEquals(expected,client.check());
    }
    @Test public void unrelatedPackagesAndExternalSourcesAreRejected()throws Exception{
        JSONObject good=new JSONObject(info().json());
        for(String key:new String[]{"packageName","apkUrl","sha256"}){
            JSONObject altered=new JSONObject(good.toString()).put(key,key.equals("packageName")?"other.app":key.equals("apkUrl")?"https://example.com/app.apk":"bad");
            assertThrows(IOException.class,()->UpdateInfo.parse(altered.toString()));
        }
    }
    @Test public void insecureAndConfusingDownloadUrlsAreRejected()throws Exception{
        String url=info().url();
        for(String bad:new String[]{url.replace("https:","http:"),url.replace("https://","https://attacker@"),url+"?redirect=bad",url+"#x",url.replace("/Wisp/","/Other/"),url.replace("raw.githubusercontent.com","raw.githubusercontent.com.evil.example")}){
            JSONObject json=new JSONObject(info().json()).put("apkUrl",bad);
            assertThrows(IOException.class,()->UpdateInfo.parse(json.toString()));
        }
    }
    @Test public void invalidVersionsAndOversizedDownloadsAreRejected()throws Exception{
        for(long value:new long[]{0,-1,2147483648L}){
            String json=new JSONObject(info().json()).put("versionCode",value).toString();assertThrows(IOException.class,()->UpdateInfo.parse(json));
        }
        for(long size:new long[]{0,-1,UpdateInfo.MAX_APK_SIZE+1}){
            String json=new JSONObject(info().json()).put("size",size).toString();assertThrows(IOException.class,()->UpdateInfo.parse(json));
        }
    }
    @Test public void successfulDownloadIsVerifiedAndReusedWithoutAnotherRequest()throws Exception{
        AtomicInteger requests=new AtomicInteger();UpdateClient c=new UpdateClient(url->{requests.incrementAndGet();assertEquals(infoUrl(),url);return new ByteArrayInputStream(data);});
        File dir=temp.newFolder();AtomicInteger progress=new AtomicInteger();File first=c.download(info(),dir,progress::set);
        assertArrayEquals(data,Files.readAllBytes(first.toPath()));assertEquals(100,progress.get());
        assertEquals(first,c.download(info(),dir,p->{}));assertEquals(1,requests.get());assertEquals(1,dir.list().length);
    }
    private String infoUrl(){return "https://raw.githubusercontent.com/238ivan238-jpg/Wisp/codex/wisp-android-v1/downloads/Wisp-1.0.4.apk";}
    @Test public void wrongChecksumNeverLeavesAnInstallableApk()throws Exception{
        byte[] wrong=data.clone();wrong[0]^=1;UpdateClient c=new UpdateClient(url->new ByteArrayInputStream(wrong));File dir=temp.newFolder();
        assertThrows(IOException.class,()->c.download(info(),dir,p->{}));assertEquals(0,dir.list().length);
    }
    @Test public void truncatedAndOversizedStreamsAreDiscarded()throws Exception{
        for(int length:new int[]{data.length-1,data.length+1}){
            UpdateClient c=new UpdateClient(url->new ByteArrayInputStream(Arrays.copyOf(data,length)));File dir=temp.newFolder();
            assertThrows(IOException.class,()->c.download(info(),dir,p->{}));assertEquals(0,dir.list().length);
        }
    }
    @Test public void interruptedDownloadDeletesItsPartialFile()throws Exception{
        AtomicReference<UpdateClient> ref=new AtomicReference<>();
        UpdateClient c=new UpdateClient(url->new ByteArrayInputStream(data){@Override public synchronized int read(byte[] b,int off,int len){int n=super.read(b,off,len);ref.get().cancel();return n;}});
        ref.set(c);File dir=temp.newFolder();assertThrows(InterruptedIOException.class,()->c.download(info(),dir,p->{}));assertEquals(0,dir.list().length);
    }
    @Test public void corruptedCachedApkIsDownloadedAgain()throws Exception{
        File dir=temp.newFolder();Files.write(new File(dir,"Wisp-5.apk").toPath(),new byte[]{1});
        UpdateClient c=new UpdateClient(url->new ByteArrayInputStream(data));assertTrue(UpdateClient.matches(c.download(info(),dir,p->{}),info()));
    }
    @Test public void unboundedManifestResponsesAreRejected(){
        UpdateClient c=new UpdateClient(url->new ByteArrayInputStream(new byte[17000]));assertThrows(IOException.class,c::check);
    }
}
