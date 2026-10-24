package app.wisp.voice;

import android.media.MediaRecorder;

/** A visual meter must never stop or invalidate the actual recording. */
final class RecorderLevel {
    static int read(MediaRecorder recorder,boolean paused){
        if(recorder==null||paused)return 0;
        try{return recorder.getMaxAmplitude();}catch(RuntimeException unavailable){return 0;}
    }
    private RecorderLevel(){}
}
