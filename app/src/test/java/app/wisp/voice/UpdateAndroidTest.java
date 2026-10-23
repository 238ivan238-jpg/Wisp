package app.wisp.voice;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import static org.junit.Assert.*;
import org.robolectric.util.ReflectionHelpers;
import android.provider.Settings;
import android.content.pm.PackageManager;
import android.os.Looper;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class UpdateAndroidTest {
    @Before public void disableAutoCheck()throws Exception{
        RuntimeEnvironment.getApplication().getSharedPreferences("wisp_updates",0).edit().putBoolean("auto_check",false).commit();
        // Robolectric creates a new application cache directory for every method,
        // while AndroidX's process-wide provider path cache survives in this sandbox.
        java.util.Map<?,?> paths=ReflectionHelpers.getStaticField(androidx.core.content.FileProvider.class,"sCache");paths.clear();
        // These upgrade fixtures always model 1.0.3 -> 1.0.4, independent of the
        // version currently being built, so future release bumps do not invalidate them.
        var context=RuntimeEnvironment.getApplication();var pm=context.getPackageManager();
        PackageInfo installed=pm.getPackageInfo(context.getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES|PackageManager.GET_ACTIVITIES|PackageManager.GET_PROVIDERS|PackageManager.GET_META_DATA);
        installed.setLongVersionCode(4);installed.versionName="1.0.3";Shadows.shadowOf(pm).installPackage(installed);
    }
    private UpdateInfo next(){return new UpdateInfo(5,"1.0.4",28,"https://raw.githubusercontent.com/238ivan238-jpg/Wisp/codex/wisp-android-v1/downloads/Wisp-1.0.4.apk","0".repeat(64),1,"Доступно обновление");}
    private PackageInfo pkg(int version,String name,String signer){
        PackageInfo p=new PackageInfo();p.packageName=name;p.setLongVersionCode(version);p.versionName=version==4?"1.0.3":"1.0.4";
        p.signingInfo=new SigningInfo();Shadows.shadowOf(p.signingInfo).setSignatures(new Signature[]{new Signature(signer.getBytes(java.nio.charset.StandardCharsets.UTF_8))});return p;
    }
    private TextView find(View v,String text){
        if(v instanceof TextView t&&text.contentEquals(t.getText()))return t;
        if(v instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){TextView t=find(group.getChildAt(i),text);if(t!=null)return t;}
        return null;
    }
    @Test public void verifiedSameSignerAndNewerVersionIsAccepted()throws Exception{
        UpdateVerifier.verifyPackages(pkg(4,"app.wisp.voice","signer A"),pkg(5,"app.wisp.voice","signer A"),next());
    }
    @Test public void otherSignerOrOtherAppCannotBeInstalled(){
        PackageInfo current=pkg(4,"app.wisp.voice","signer A");
        assertThrows(IOException.class,()->UpdateVerifier.verifyPackages(current,pkg(5,"app.wisp.voice","signer B"),next()));
        assertThrows(IOException.class,()->UpdateVerifier.verifyPackages(current,pkg(5,"another.package","signer A"),next()));
    }
    @Test public void downgradeOrMissingSignatureIsRejected(){
        PackageInfo current=pkg(5,"app.wisp.voice","signer A"),same=pkg(5,"app.wisp.voice","signer A");
        assertThrows(IOException.class,()->UpdateVerifier.verifyPackages(current,same,next()));
        PackageInfo unsigned=pkg(5,"app.wisp.voice","signer A");unsigned.signingInfo=null;
        assertThrows(IOException.class,()->UpdateVerifier.verifyPackages(pkg(4,"app.wisp.voice","signer A"),unsigned,next()));
    }
    @Test public void installerUsesReadableContentUriAndApkMimeType()throws Exception{
        var context=RuntimeEnvironment.getApplication();File dir=new File(context.getCacheDir(),"updates");assertTrue(dir.isDirectory()||dir.mkdirs());
        File file=new File(dir,"Wisp-5.apk");Files.write(file.toPath(),new byte[]{1});
        Intent intent=UpdateController.installerIntent(context,file);
        assertEquals(Intent.ACTION_VIEW,intent.getAction());assertEquals("content",intent.getData().getScheme());
        assertEquals("app.wisp.voice.updates",intent.getData().getAuthority());assertEquals("application/vnd.android.package-archive",intent.getType());
        assertTrue((intent.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0);assertEquals(intent.getData(),intent.getClipData().getItemAt(0).getUri());
    }
    @Test public void providerCannotShareFilesOutsideUpdateDirectory()throws Exception{
        var context=RuntimeEnvironment.getApplication();File file=new File(context.getCacheDir(),"unrelated-private-file");Files.write(file.toPath(),new byte[]{1});
        assertThrows(IllegalArgumentException.class,()->UpdateController.installerIntent(context,file));
    }
    @Test public void cachedNewReleaseIsVisibleWithoutAnyDownload(){
        RuntimeEnvironment.getApplication().getSharedPreferences("wisp_updates",0).edit().putString("latest",next().json()).commit();
        try(var activity=Robolectric.buildActivity(MainActivity.class).setup()){
            View root=activity.get().getWindow().getDecorView();
            assertNotNull(find(root,"Проверить обновления"));TextView install=find(root,"Скачать и обновить до 1.0.4");
            assertNotNull(install);assertEquals(View.VISIBLE,install.getVisibility());
            Bundle state=new Bundle();activity.saveInstanceState(state);assertEquals(next(),UpdateInfo.parse(state.getString("update_latest")));
        }catch(IOException e){throw new AssertionError(e);}
    }
    @Test public void returningFromInstallPermissionReusesVerifiedCacheAndOpensInstaller()throws Exception{
        var context=RuntimeEnvironment.getApplication();byte[] bytes={1};
        UpdateInfo update=new UpdateInfo(5,"1.0.4",28,next().url(),UpdateClient.hex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)),1,"Тест обновления");
        context.getSharedPreferences("wisp_updates",0).edit().putString("latest",update.json()).commit();
        try(var a=Robolectric.buildActivity(MainActivity.class).setup()){
            var pm=context.getPackageManager();var shadow=Shadows.shadowOf(pm);
            PackageInfo installed=pm.getPackageInfo(context.getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES|PackageManager.GET_ACTIVITIES|PackageManager.GET_PROVIDERS|PackageManager.GET_META_DATA);
            installed.signingInfo=pkg(4,"app.wisp.voice","signer A").signingInfo;shadow.installPackage(installed);
            File dir=new File(context.getCacheDir(),"updates");assertTrue(dir.isDirectory()||dir.mkdirs());
            File apk=new File(dir,"Wisp-5.apk");Files.write(apk.toPath(),bytes);shadow.setPackageArchiveInfo(apk.getAbsolutePath(),pkg(5,"app.wisp.voice","signer A"));
            shadow.setCanRequestPackageInstalls(false);
            UpdateController controller=ReflectionHelpers.getField(a.get(),"updates");
            java.util.concurrent.ExecutorService worker=ReflectionHelpers.getField(controller,"worker");
            find(a.get().getWindow().getDecorView(),"Скачать и обновить до 1.0.4").performClick();
            worker.submit(()->{}).get(10,java.util.concurrent.TimeUnit.SECONDS);Shadows.shadowOf(Looper.getMainLooper()).idle();
            Intent permission=Shadows.shadowOf(a.get()).getNextStartedActivity();assertNotNull(permission);assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,permission.getAction());
            shadow.setCanRequestPackageInstalls(true);controller.onResume();
            worker.submit(()->{}).get(10,java.util.concurrent.TimeUnit.SECONDS);Shadows.shadowOf(Looper.getMainLooper()).idle();
            Intent installer=Shadows.shadowOf(a.get()).getNextStartedActivity();assertNotNull(installer);assertEquals("application/vnd.android.package-archive",installer.getType());
            assertArrayEquals(bytes,Files.readAllBytes(apk.toPath()));
        }
    }
}
