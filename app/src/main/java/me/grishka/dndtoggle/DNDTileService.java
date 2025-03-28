package me.grishka.dndtoggle;

import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import java.util.concurrent.TimeUnit;

public class DNDTileService extends TileService {
    private static final String TAG = "DNDTileService";

    public static final String PREF_START_TIME = "startTimeMs";
    public static final String PREF_DURATION_MS = "durationMs";

    // all possible duration values (resource ID -> duration in ms)
    static final Integer[][] VALUE_ARR = new Integer[][]{
        {R.string.time_disabled, 0},
        {R.string.time_5_mins, 5 * 60 * 1000},
        {R.string.time_15_mins, 15 * 60 * 1000},
        {R.string.time_30_mins, 30 * 60 * 1000},
        {R.string.time_1_hour, 60 * 60 * 1000},
        {R.string.time_5_hours, 5 * 60 * 60 * 1000},
    };

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
        boolean isDND = isDND(this);
        // toggle state
        isDND = !isDND;
        // update DND state
        setDND(this, isDND);

        // if duration is set, start a timer to turn off DND
        long durationMs = getDuration(this);
        if (isDND && durationMs > 0) {
            // set start time
            setStartTime(this, true);
            // start timer service
            startTimerService(this, isDND);
        }
        updateTile();
    }

    public static boolean isDND(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        return nm.getCurrentInterruptionFilter() == NotificationManager.INTERRUPTION_FILTER_PRIORITY;
    }

    public static void setDND(Context context, boolean isDND) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        nm.setInterruptionFilter(isDND ? NotificationManager.INTERRUPTION_FILTER_PRIORITY : NotificationManager.INTERRUPTION_FILTER_ALL);
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
                setStartTime(this, false);
                startTimerService(this, false);
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
                    setStartTime(this, false);
                    startTimerService(this, false);
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

    public static void startTimerService(Context context, boolean isDND) {
        Log.d(TAG, "startTimerService: isDND: " + isDND);
        Intent intent = new Intent(context, DNDTimerService.class);
        try {
            if (isDND) context.startForegroundService(intent);
            else context.stopService(intent);
        } catch (Exception e) {
            Log.e(TAG, "startTimerService: isDND: " + isDND + " Exception: " + e.getMessage());
        }
    }

    public static void setStartTime(Context context, boolean isStart) {
        SharedPreferences.Editor editor = getSharedPrefs(context).edit();
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

    public interface DNDListener {
        void onDurationSelected(boolean isOk, long durationMs);
    }

    public static void showDurationDialog(Context context, boolean showSettings, DNDListener listener) {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(context);
        dialogBuilder.setTitle(R.string.duration);

        long currentDurationMs = getDuration(context);
        String[] options = new String[VALUE_ARR.length];
        int selectedIndex = 0;
        for (int i = 0; i < VALUE_ARR.length; i++) {
            options[i] = context.getString(VALUE_ARR[i][0]);
            if (currentDurationMs == VALUE_ARR[i][1]) {
                selectedIndex = i;
            }
        }

        dialogBuilder.setSingleChoiceItems(options, selectedIndex, null);

        if (showSettings) {
            dialogBuilder.setNegativeButton(R.string.open_settings, (dialog, which) -> {
                context.startActivity(new Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS));
            });
        }

        dialogBuilder.setPositiveButton(R.string.ok, (dialog, which) -> {
            AlertDialog alert = (AlertDialog) dialog;
            int whichItem = alert.getListView().getCheckedItemPosition();
            if (whichItem < 0 || whichItem >= VALUE_ARR.length) {
                listener.onDurationSelected(false, 0);
                return;
            }
            Integer durationMs = VALUE_ARR[whichItem][1];
            setDuration(context, durationMs);
            listener.onDurationSelected(true, durationMs);
        });
        AlertDialog alert = dialogBuilder.create();
        alert.show();
    }

}
