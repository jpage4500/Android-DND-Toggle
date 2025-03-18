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
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
    }

    @Override
    public void onStartListening() {
        current = this;
        super.onStartListening();
        Log.d(TAG, "onStartListening");
        updateTile();
    }

    @Override
    public void onStopListening() {
        current = null;
        super.onStopListening();
        Log.d(TAG, "onStopListening");
    }

    @Override
    public void onClick() {
        super.onClick();
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
            long startTimeMs = getStartTime();
            long durationMs = getDuration(this);
            if (isDND && startTimeMs > 0 && durationMs > 0) {
                long ellapsedMs = System.currentTimeMillis() - startTimeMs;
                long remainingMs = durationMs - ellapsedMs;
                // TODO: when initially set, round up
                long remainingMins = TimeUnit.MILLISECONDS.toMinutes(remainingMs);
                Log.d(TAG, remainingMins + " mins " + remainingMs + "ms");
                if (remainingMs < 0) {
                    // time up - disable DND
                    setStartTime(false);
                    startTimerService(false);
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
                    tile.setSubtitle(null);
                } else {
                    tile.setSubtitle(remainingMins + " mins left");
                }
            } else {
                // remove start time if disabled from user click *or* user turning off DND via other method
                setStartTime(false);
                tile.setSubtitle(null);
            }
            tile.setState(isDND ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            tile.setIcon(Icon.createWithResource(this, isDND ? R.drawable.ic_do_not_disturb_on_fill1_24px : R.drawable.ic_do_not_disturb_on_24px));
        }
        tile.updateTile();
    }

    public static void setDuration(Context context, long durationMs) {
        SharedPreferences.Editor editor = getSharedPrefs(context).edit();
        editor.putLong(PREF_DURATION_MS, durationMs);
        editor.apply();
    }

    public static long getDuration(Context context) {
        return getSharedPrefs(context).getLong(PREF_DURATION_MS, 0);
    }

    private void startTimerService(boolean isDND) {
        Intent intent = new Intent(this, TimerService.class);
        try {
            if (isDND) startService(intent);
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

    private long getStartTime() {
        return getSharedPrefs(this).getLong(PREF_START_TIME, 0);
    }

    private static SharedPreferences getSharedPrefs(Context context) {
        return context.getSharedPreferences("tile", MODE_PRIVATE);
    }

    public static boolean isTileAdded(Context context) {
        return getSharedPrefs(context).getBoolean("added", false);
    }
}
