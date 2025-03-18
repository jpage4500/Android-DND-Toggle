package me.grishka.dndtoggle;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.os.IBinder;
import android.service.quicksettings.TileService;
import android.util.Log;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TimerService extends Service {
    private static final String TAG = "DNDTileService";

    private ScheduledExecutorService scheduler;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "TimerService:onCreate");
        scheduler = Executors.newScheduledThreadPool(1);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "TimerService:onDestroy");
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "TimerService:onBind");
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "TimerService:onStartCommand");

        scheduler.scheduleWithFixedDelay(() -> {
            Log.d(TAG, "TimerService:scheduleWithFixedDelay:RUN");
            try {
                TileService.requestListeningState(this, new ComponentName(this, DNDTileService.class));
            } catch (Exception e) {
                Log.e(TAG, "TimerService:scheduleWithFixedDelay:Exception", e);
            }
        }, 1, 1, TimeUnit.MINUTES);

        return super.onStartCommand(intent, flags, startId);
    }
}
