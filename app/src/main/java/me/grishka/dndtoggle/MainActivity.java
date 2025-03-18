package me.grishka.dndtoggle;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.provider.Settings;
import android.service.quicksettings.TileService;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final String TAG = "DNDTileService";

    private Button openSettingsBtn, addTileBtn, durationBtn;
    private TextView permissionGrantedText, tileAddedText;
    private static MainActivity current;

    private final BroadcastReceiver permissionChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (NotificationManager.ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED.equals(intent.getAction())) {
                updateButtons();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        current = this;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        openSettingsBtn = findViewById(R.id.settings_btn);
        addTileBtn = findViewById(R.id.add_tile_btn);
        permissionGrantedText = findViewById(R.id.permission_granted);
        tileAddedText = findViewById(R.id.tile_added);
        durationBtn = findViewById(R.id.duration_btn);

        openSettingsBtn.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)));
        addTileBtn.setOnClickListener(v -> {
            StatusBarManager sbm = getSystemService(StatusBarManager.class);
            sbm.requestAddTileService(new ComponentName(this, DNDTileService.class), getString(R.string.do_not_disturb), Icon.createWithResource(this, R.drawable.ic_do_not_disturb), getMainExecutor(), result -> updateButtons());
        });
        durationBtn.setOnClickListener(v -> {
            showDurationDialog();
        });

        registerReceiver(permissionChangeReceiver, new IntentFilter(NotificationManager.ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED), RECEIVER_EXPORTED);
        updateButtons();
    }

    // all possible duration values (resource ID -> duration in ms)
    Integer[][] VALUE_ARR = new Integer[][]{
        {R.string.time_disabled, 0},
        {R.string.time_30_mins, 30 * 60 * 1000},
        {R.string.time_1_hour, 60 * 60 * 1000},
        {R.string.time_5_hours, 5 * 60 * 60 * 1000},
    };

    private void showDurationDialog() {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
        dialogBuilder.setTitle(R.string.duration);
        String[] options = new String[VALUE_ARR.length];
        for (int i = 0; i < VALUE_ARR.length; i++) {
            options[i] = getString(VALUE_ARR[i][0]);
        }

        dialogBuilder.setSingleChoiceItems(options, -1, null);
        dialogBuilder.setPositiveButton(R.string.ok, (dialog, which) -> {
            AlertDialog alert = (AlertDialog) dialog;
            int whichItem = alert.getListView().getCheckedItemPosition();
            if (whichItem < 0 || whichItem >= VALUE_ARR.length) return;
            Integer durationMs = VALUE_ARR[whichItem][1];
            Log.d(TAG, "BUTTON: " + whichItem + ", duration: " + durationMs);
            DNDTileService.setDuration(this, durationMs);
            updateButtons();
            TileService.requestListeningState(this, new ComponentName(this, DNDTileService.class));
        });
        AlertDialog alert = dialogBuilder.create();
        alert.show();
    }

    @Override
    protected void onDestroy() {
        current = null;
        unregisterReceiver(permissionChangeReceiver);
        super.onDestroy();
    }

    public static void updateFromService() {
        if (current != null) {
            current.runOnUiThread(current::updateButtons);
        }
    }

    private void updateButtons() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm.isNotificationPolicyAccessGranted()) {
            openSettingsBtn.setVisibility(View.INVISIBLE);
            permissionGrantedText.setVisibility(View.VISIBLE);
            if (DNDTileService.isTileAdded(this)) {
                addTileBtn.setVisibility(View.INVISIBLE);
                tileAddedText.setVisibility(View.VISIBLE);
            } else {
                addTileBtn.setVisibility(View.VISIBLE);
                addTileBtn.setEnabled(true);
                tileAddedText.setVisibility(View.INVISIBLE);
            }
            durationBtn.setEnabled(true);
            long durationMs = DNDTileService.getDuration(this);
            int durationTextId = R.string.time_disabled;
            for (Integer[] valueArr : VALUE_ARR) {
                if (durationMs == valueArr[1]) {
                    durationTextId = valueArr[0];
                    break;
                }
            }
            durationBtn.setText(durationTextId);
        } else {
            permissionGrantedText.setVisibility(View.INVISIBLE);
            tileAddedText.setVisibility(View.INVISIBLE);
            addTileBtn.setVisibility(View.VISIBLE);
            addTileBtn.setEnabled(false);
            durationBtn.setEnabled(false);
        }
    }
}
