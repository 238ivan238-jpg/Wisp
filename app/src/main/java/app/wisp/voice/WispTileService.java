package app.wisp.voice;

import android.app.PendingIntent;
import android.content.*;
import android.os.Build;
import android.service.quicksettings.*;

public final class WispTileService extends TileService {
    @Override public void onStartListening(){super.onStartListening();refresh();}
    @Override public void onClick(){super.onClick();if(isLocked()){unlockAndRun(this::toggle);return;}toggle();}
    private void toggle(){
        if(!CompanionControl.connected()){
            Intent intent=new Intent(this,MainActivity.class).putExtra(AppNavigation.EXTRA_SECTION,AppNavigation.SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if(Build.VERSION.SDK_INT>=34)startActivityAndCollapse(PendingIntent.getActivity(this,82,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
            else openLegacy(intent);
            return;
        }
        SecureSettings settings=new SecureSettings(this);settings.setCompanionVisible(!settings.companionVisible());CompanionControl.changed();refresh();
    }
    // The PendingIntent overload does not exist below API 34; this helper is called only there.
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    private void openLegacy(Intent intent){startActivityAndCollapse(intent);}
    private void refresh(){Tile tile=getQsTile();if(tile==null)return;boolean connected=CompanionControl.connected(),visible=new SecureSettings(this).companionVisible();
        tile.setLabel("Wisp");tile.setState(connected&&visible?Tile.STATE_ACTIVE:Tile.STATE_INACTIVE);
        if(Build.VERSION.SDK_INT>=29)tile.setSubtitle(!connected?"Подключить":visible?"Микрофон включён":"Микрофон скрыт");tile.updateTile();}
    static void update(Context context){try{requestListeningState(context,new ComponentName(context,WispTileService.class));}catch(RuntimeException unavailable){/* Status bar policy must not interrupt dictation. */}}
}
