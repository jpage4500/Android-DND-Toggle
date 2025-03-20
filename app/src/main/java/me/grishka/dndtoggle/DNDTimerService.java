package me.grishka.dndtoggle;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.service.quicksettings.TileService;
import android.util.Log;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class DNDTimerService extends Service {
    private static final String TAG = "DNDTimerService";

    private static final String DND_NOTIFICATION = "DND_NOTIFICATION";
    private static final int ID_NOTIFICATION = 1021;

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> future;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");

        // required to keep service running
        showNotification();
    }

    private void showNotification() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(DND_NOTIFICATION, "DND Toggle", NotificationManager.IMPORTANCE_HIGH);
        notificationManager.createNotificationChannel(channel);
        Notification.Builder builder = new Notification.Builder(this, DND_NOTIFICATION);
        Notification notification = builder.setOngoing(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .setSmallIcon(R.drawable.ic_do_not_disturb)
            .setContentTitle("DND Tile Active")
            .build();
        startForeground(ID_NOTIFICATION, notification);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(ID_NOTIFICATION);

        scheduler.shutdown();
        scheduler = null;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");

        if (scheduler == null) {
            scheduler = Executors.newScheduledThreadPool(1);
            scheduler.scheduleWithFixedDelay(() -> {
                Log.d(TAG, "scheduleWithFixedDelay:RUN");
                try {
                    TileService.requestListeningState(this, new ComponentName(this, DNDTileService.class));
                } catch (Exception e) {
                    Log.e(TAG, "scheduleWithFixedDelay:Exception", e);
                }
            }, 60, 60, TimeUnit.SECONDS);
        }

        return START_STICKY;
    }

}
