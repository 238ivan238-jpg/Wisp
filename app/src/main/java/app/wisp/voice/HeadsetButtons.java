package app.wisp.voice;

import android.content.*;
import android.media.session.*;
import android.view.KeyEvent;

/** Opt-in transport controls only while an eligible text field is visible. */
final class HeadsetButtons implements AutoCloseable {
    interface Actions {void toggle();void pause();void stop();default void play(){toggle();}}
    private MediaSession session;
    private final Context context;
    private final Actions actions;
    private boolean available;
    HeadsetButtons(Context context,Actions actions){this.context=context;this.actions=actions;}
    private void create(){
        session=new MediaSession(context,"Wisp dictation");session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS|MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setCallback(new MediaSession.Callback(){
            @Override public boolean onMediaButtonEvent(Intent intent){
                KeyEvent event=intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                return handle(event);
            }
            @Override public void onPlay(){if(available)actions.play();}
            @Override public void onPause(){if(available)actions.pause();}
            @Override public void onStop(){if(available)actions.stop();}
        });
    }
    boolean handle(KeyEvent event){
        if(!available||event==null||event.getAction()!=KeyEvent.ACTION_DOWN||event.getRepeatCount()!=0)return false;
        return switch(event.getKeyCode()){
            case KeyEvent.KEYCODE_HEADSETHOOK,KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE->{actions.toggle();yield true;}
            case KeyEvent.KEYCODE_MEDIA_PLAY->{actions.play();yield true;}
            case KeyEvent.KEYCODE_MEDIA_PAUSE->{actions.pause();yield true;}
            case KeyEvent.KEYCODE_MEDIA_STOP->{actions.stop();yield true;}
            default->false;
        };
    }
    void update(boolean value,boolean recording,boolean paused){available=value;if(session==null){if(!value)return;try{create();}catch(RuntimeException unavailable){available=false;return;}}session.setActive(value);
        session.setPlaybackState(new PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE|PlaybackState.ACTION_STOP)
            .setState(recording&&!paused?PlaybackState.STATE_PLAYING:PlaybackState.STATE_PAUSED,PlaybackState.PLAYBACK_POSITION_UNKNOWN,1).build());}
    @Override public void close(){available=false;if(session!=null)session.release();session=null;}
}
