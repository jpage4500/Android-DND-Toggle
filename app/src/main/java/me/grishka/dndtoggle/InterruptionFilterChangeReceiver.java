package me.grishka.dndtoggle;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.service.quicksettings.TileService;
import android.util.Log;

public class InterruptionFilterChangeReceiver extends BroadcastReceiver {
    private static final String TAG = "InterruptionFilterChangeReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (DNDTileService.current != null) {
            Log.d(TAG, "onReceive: update tile");
            DNDTileService.current.updateTile();
        } else {
            Log.d(TAG, "onReceive: not running");
            TileService.requestListeningState(context, new ComponentName(context, DNDTileService.class));
        }
    }
}
