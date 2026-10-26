package app.wisp.voice;

import android.app.Application;

public final class WispApplication extends Application {
    private Thread.UncaughtExceptionHandler previous,installed;
    @Override public void onCreate(){
        super.onCreate();
        java.io.File[] temporary=getCacheDir().listFiles((dir,name)->name.startsWith("recovery-")&&name.endsWith(".m4a"));
        if(temporary!=null)for(java.io.File file:temporary)file.delete();
        AudioChunks.delete(new java.io.File(getCacheDir(),"audio-chunks"));
        previous=Thread.getDefaultUncaughtExceptionHandler();DiagnosticLog log=new DiagnosticLog(this);
        installed=(thread,error)->{try{log.crash(error);}catch(Throwable ignored){}finally{if(previous!=null)previous.uncaughtException(thread,error);}};
        Thread.setDefaultUncaughtExceptionHandler(installed);
    }
    @Override public void onTerminate(){if(Thread.getDefaultUncaughtExceptionHandler()==installed)Thread.setDefaultUncaughtExceptionHandler(previous);super.onTerminate();}
}
