package me.grishka.dndtoggle;

import android.app.Activity;
import android.os.Bundle;

public class SettingsRedirectActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // long-press on toggle
        DNDTileService.showDurationDialog(this, true, (isOk, durationMs) -> {
            if (isOk) {
                if (durationMs > 0) {
                    // set start time
                    DNDTileService.setStartTime(this, true);
                    // start timer service
                    DNDTileService.startTimerService(this, true);
                }
                DNDTileService.setDND(this, true);
            }
            finish();
        });
        //startActivity(new Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS));
    }
}
