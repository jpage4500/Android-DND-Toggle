package me.grishka.dndtoggle;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import java.util.concurrent.TimeUnit;

public class DNDTileService extends TileService {
    private static final String TAG = "DNDTileService";

    public static final String PREF_START_TIME = "startTimeMs";
    public static final String PREF_DURATION_MS = "durationMs";

    public static DNDTileService current;

    @Override
    public void onStartListening() {
        current = this;
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onStopListening() {
        current = null;
        super.onStopListening();
    }

    @Override
    public void onClick() {
        super.onClick();
        Log.d(TAG, "onClick");
        NotificationManager nm = getSystemService(NotificationManager.class);
        boolean isDND = nm.getCurrentInterruptionFilter() == NotificationManager.INTERRUPTION_FILTER_PRIORITY;
        // toggle state
        isDND = !isDND;
        nm.setInterruptionFilter(isDND ? NotificationManager.INTERRUPTION_FILTER_PRIORITY : NotificationManager.INTERRUPTION_FILTER_ALL);
        startTimerService(isDND);
        if (isDND) {
            // set start time
            setStartTime(true);
        }
        updateTile();
    }

    @Override
    public void onTileAdded() {
        super.onTileAdded();
        Log.d(TAG, "onTileAdded");
        getSharedPrefs(this).edit().putBoolean("added", true).apply();
        MainActivity.updateFromService();
    }

    @Override
    public void onTileRemoved() {
        super.onTileRemoved();
        Log.d(TAG, "onTileRemoved");
        getSharedPrefs(this).edit().remove("added").apply();
        MainActivity.updateFromService();
    }

    public void updateTile() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        Tile tile = getQsTile();
        if (!nm.isNotificationPolicyAccessGranted()) {
            tile.setState(Tile.STATE_UNAVAILABLE);
            tile.setSubtitle(getString(R.string.tile_no_permission));
            tile.setIcon(Icon.createWithResource(this, R.drawable.ic_do_not_disturb));
        } else {
            boolean isDND = nm.getCurrentInterruptionFilter() == NotificationManager.INTERRUPTION_FILTER_PRIORITY;
            if (!isDND) {
                // remove start time if disabled from user click *or* user turning off DND via other method
                setStartTime(false);
                startTimerService(false);
            }
            long startTimeMs = getStartTime(this);
            long durationMs = getDuration(this);
            if (isDND && startTimeMs > 0 && durationMs > 0) {
                long ellapsedMs = System.currentTimeMillis() - startTimeMs;
                long remainingMs = durationMs - ellapsedMs;
                // add 1 minute so "59 mins, 55 secs" = "60 mins"
                long remainingMins = TimeUnit.MILLISECONDS.toMinutes(remainingMs) + 1;
                if (remainingMs < 0) {
                    // time up - disable DND
                    Log.d(TAG, "updateTile: times up!");
                    setStartTime(false);
                    startTimerService(false);
                    // this should cause InterruptionFilterChangeReceiver to get notified updating the UI
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
                    tile.setSubtitle(null);
                } else {
                    Log.d(TAG, "updateTile: ms: " + remainingMs + ", mins: " + remainingMins);
                    tile.setSubtitle(remainingMins + " mins left");
                }
            } else {
                tile.setSubtitle(null);
            }
            tile.setState(isDND ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            tile.setIcon(Icon.createWithResource(this, isDND ? R.drawable.ic_do_not_disturb_on_fill1_24px : R.drawable.ic_do_not_disturb_on_24px));
        }
        tile.updateTile();
    }

    public static void setDuration(Context context, long durationMs) {
        Log.d(TAG, "setDuration: durationMs: " + durationMs);
        SharedPreferences.Editor editor = getSharedPrefs(context).edit();
        editor.putLong(PREF_DURATION_MS, durationMs);
        editor.apply();
    }

    public static long getDuration(Context context) {
        return getSharedPrefs(context).getLong(PREF_DURATION_MS, 0);
    }

    private void startTimerService(boolean isDND) {
        Log.d(TAG, "startTimerService: isDND: " + isDND);

        Intent intent = new Intent(this, DNDTimerService.class);
        try {
            if (isDND) startForegroundService(intent);
            else stopService(intent);
        } catch (Exception e) {
            Log.e(TAG, "startTimerService: isDND: " + isDND + " Exception: " + e.getMessage());
        }
    }

    private void setStartTime(boolean isStart) {
        SharedPreferences.Editor editor = getSharedPrefs(this).edit();
        if (isStart) {
            editor.putLong(PREF_START_TIME, System.currentTimeMillis());
        } else {
            editor.remove(PREF_START_TIME);
        }
        editor.apply();
    }

    public static long getStartTime(Context context) {
        return getSharedPrefs(context).getLong(PREF_START_TIME, 0);
    }

    private static SharedPreferences getSharedPrefs(Context context) {
        return context.getSharedPreferences("tile", MODE_PRIVATE);
    }

    public static boolean isTileAdded(Context context) {
        return getSharedPrefs(context).getBoolean("added", false);
    }
}
