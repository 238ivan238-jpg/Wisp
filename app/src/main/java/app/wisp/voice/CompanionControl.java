package app.wisp.voice;

import java.lang.ref.WeakReference;

/** Same-process notification only; never an exported microphone-start broadcast. */
final class CompanionControl {
    interface Listener {void changed();}
    private static WeakReference<Listener> listener=new WeakReference<>(null);
    static void attach(Listener value){listener=new WeakReference<>(value);}
    static void detach(Listener value){if(listener.get()==value)listener.clear();}
    static boolean connected(){return listener.get()!=null;}
    static void changed(){Listener target=listener.get();if(target!=null)target.changed();}
    private CompanionControl(){}
}
