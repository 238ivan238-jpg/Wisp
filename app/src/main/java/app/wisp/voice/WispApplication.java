package app.wisp.voice;

import android.app.Application;

public final class WispApplication extends Application {
    private Thread.UncaughtExceptionHandler previous,installed;
    @Override public void onCreate(){
        super.onCreate();previous=Thread.getDefaultUncaughtExceptionHandler();DiagnosticLog log=new DiagnosticLog(this);
        installed=(thread,error)->{try{log.crash(error);}catch(Throwable ignored){}finally{if(previous!=null)previous.uncaughtException(thread,error);}};
        Thread.setDefaultUncaughtExceptionHandler(installed);
    }
    @Override public void onTerminate(){if(Thread.getDefaultUncaughtExceptionHandler()==installed)Thread.setDefaultUncaughtExceptionHandler(previous);super.onTerminate();}
}
