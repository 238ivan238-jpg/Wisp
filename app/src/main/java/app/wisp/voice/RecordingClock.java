package app.wisp.voice;

/** Monotonic active audio time; pauses never consume the five minute allowance. */
final class RecordingClock {
    private long accumulated,from;
    private boolean paused;
    void start(long now){accumulated=0;from=now;paused=false;}
    void pause(long now){if(!paused){accumulated+=Math.max(0,now-from);paused=true;}}
    void resume(long now){if(paused){from=now;paused=false;}}
    long elapsed(long now){return accumulated+(paused?0:Math.max(0,now-from));}
    boolean paused(){return paused;}
}
