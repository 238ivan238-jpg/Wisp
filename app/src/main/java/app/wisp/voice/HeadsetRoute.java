package app.wisp.voice;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.*;
import android.os.Build;

/** Scoped input preference. Never takes over an existing call's communication mode. */
final class HeadsetRoute implements AutoCloseable {
    private final AudioManager manager;
    private AudioDeviceInfo input,previous;
    private int oldMode;
    private boolean communication,closed;
    HeadsetRoute(Context context,boolean preferred){
        manager=context.getSystemService(AudioManager.class);if(!preferred)return;
        try{for(AudioDeviceInfo device:manager.getDevices(AudioManager.GET_DEVICES_INPUTS))if(wired(device.getType())){input=device;break;}
        if(input!=null||Build.VERSION.SDK_INT<31||context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED||manager.getMode()!=AudioManager.MODE_NORMAL)return;
        Api31.select(this);
        }catch(RuntimeException unavailable){input=null;}
    }
    private static boolean wired(int type){return type==AudioDeviceInfo.TYPE_WIRED_HEADSET||type==AudioDeviceInfo.TYPE_USB_HEADSET||type==AudioDeviceInfo.TYPE_USB_DEVICE;}
    void whenReady(android.os.Handler handler,java.util.function.Consumer<Boolean> callback){
        long deadline=android.os.SystemClock.uptimeMillis()+3000;
        Runnable wait=new Runnable(){public void run(){
            if(closed)return;
            boolean ready=!communication||android.os.Build.VERSION.SDK_INT<31;
            if(!ready)try{ready=Api31.ready(HeadsetRoute.this);}catch(RuntimeException unavailable){close();callback.accept(false);return;}
            if(ready){callback.accept(true);return;}
            if(android.os.SystemClock.uptimeMillis()>=deadline){close();callback.accept(false);return;}
            handler.postDelayed(this,80);
        }};wait.run();
    }
    int source(){return communication?MediaRecorder.AudioSource.VOICE_COMMUNICATION:MediaRecorder.AudioSource.MIC;}
    void apply(MediaRecorder recorder){if(input!=null)try{recorder.setPreferredDevice(input);}catch(RuntimeException ignored){/* Android's default input remains available. */}}
    @Override public void close(){closed=true;if(communication&&Build.VERSION.SDK_INT>=31)Api31.release(this);communication=false;input=null;}
    @android.annotation.TargetApi(31) private static final class Api31 {
        static boolean ready(HeadsetRoute route){AudioDeviceInfo device=route.manager.getCommunicationDevice();return device!=null&&route.input!=null&&device.getId()==route.input.getId();}
        static void select(HeadsetRoute route){
            try{for(AudioDeviceInfo device:route.manager.getAvailableCommunicationDevices())if(device.getType()==AudioDeviceInfo.TYPE_BLUETOOTH_SCO||device.getType()==AudioDeviceInfo.TYPE_BLE_HEADSET){
                route.oldMode=route.manager.getMode();route.previous=route.manager.getCommunicationDevice();route.manager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                if(route.manager.setCommunicationDevice(device)){route.input=device;route.communication=true;return;}
                route.manager.setMode(route.oldMode);
            }}catch(RuntimeException ignored){if(route.manager.getMode()==AudioManager.MODE_IN_COMMUNICATION)route.manager.setMode(AudioManager.MODE_NORMAL);}
        }
        static void release(HeadsetRoute route){try{
            AudioDeviceInfo active=route.manager.getCommunicationDevice();
            if(active==null||route.input!=null&&active.getId()==route.input.getId()){
                if(route.previous!=null)route.manager.setCommunicationDevice(route.previous);else route.manager.clearCommunicationDevice();
                if(route.manager.getMode()==AudioManager.MODE_IN_COMMUNICATION)route.manager.setMode(route.oldMode);
            }
        }catch(RuntimeException ignored){/* Another app/system may already own the audio route. */}}
    }
}
