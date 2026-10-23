package app.wisp.voice;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;

final class UpdateVerifier {
    static PackageInfo installed(Context context) throws PackageManager.NameNotFoundException {
        return context.getPackageManager().getPackageInfo(context.getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES);
    }
    static void verify(Context context,File apk,UpdateInfo info)throws Exception{
        if(!UpdateClient.matches(apk,info))throw new IOException("Контрольная сумма обновления не совпадает.");
        PackageInfo archive=context.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(),PackageManager.GET_SIGNING_CERTIFICATES);
        verifyPackages(installed(context),archive,info);
    }
    static void verifyPackages(PackageInfo current,PackageInfo candidate,UpdateInfo info)throws IOException{
        if(candidate==null||!"app.wisp.voice".equals(candidate.packageName)||!candidate.packageName.equals(current.packageName)
            ||candidate.getLongVersionCode()!=info.versionCode()||!info.versionName().equals(candidate.versionName)
            ||candidate.getLongVersionCode()<=current.getLongVersionCode())throw new IOException("APK не является подходящим обновлением Wisp.");
        Set<String> expected=signers(current.signingInfo),actual=signers(candidate.signingInfo);
        if(expected.isEmpty()||!expected.equals(actual))throw new IOException("Подпись обновления отличается от установленного Wisp. Установка отменена.");
    }
    private static Set<String> signers(SigningInfo info)throws IOException{
        Set<String> out=new HashSet<>();if(info==null)return out;
        Signature[] signatures=info.getApkContentsSigners();if(signatures==null)return out;
        try{for(Signature s:signatures)out.add(UpdateClient.hex(MessageDigest.getInstance("SHA-256").digest(s.toByteArray())));}
        catch(Exception e){throw new IOException("Не удалось проверить подпись обновления.");}return out;
    }
}
