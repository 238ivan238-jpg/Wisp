package app.wisp.voice;

import java.io.File;

/** Uses the same metadata/downloader code as the Android app, against the published feed. */
public final class UpdateChannelSmoke {
    public static void main(String[] args)throws Exception{
        int expected=Integer.parseInt(args[0]),installed=Integer.parseInt(args[1]);
        UpdateClient client=new UpdateClient();UpdateInfo info=client.check();
        if(info.versionCode()!=expected)throw new IllegalStateException("Published feed version differs from the release being verified");
        if(info.versionCode()<=installed){System.out.println("UP_TO_DATE "+info.versionName());return;}
        File apk=client.download(info,new File(args[2]),percent->{});
        if(!UpdateClient.matches(apk,info))throw new IllegalStateException("Downloaded APK checksum mismatch");
        if(!apk.equals(client.download(info,new File(args[2]),percent->{})))throw new IllegalStateException("Cache not reused");
        System.out.println("DOWNLOAD_VERIFIED "+info.versionName()+" "+info.size()+" bytes "+info.sha256());
    }
}
