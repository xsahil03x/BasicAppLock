package com.example.primeos.accessibilitytest;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class LockService extends Service {
    private static final String ACTION_START = "com.applock.intent.action.start_lock_service";
    private static final String ACTION_STOP = "com.applock.intent.action.stop_lock_service";
    private static Intent appLockServiceIntent;
    private String lastPackage = "";
    private static boolean isAlarmStarted = false;
    private static Timer timer;
    private ScreenOnOffReceiver receiver;
    UsageStatsManager sUsageStatsManager;
    private boolean destroy = false;

    @Nullable
    private ActivityManager activityManager;

    private static final String TAG = "LockService";

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            String CHANNEL_ID = "kids_mode_channel";
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "KidsMode AppLocker",
                    NotificationManager.IMPORTANCE_DEFAULT);

            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);

            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("")
                    .setContentText("").build();

            startForeground(1, notification);
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            sUsageStatsManager = (UsageStatsManager) this.getSystemService(Context.USAGE_STATS_SERVICE);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "AppLockService: onStartCommand called - ");
        if (intent == null || intent.getAction().equalsIgnoreCase(ACTION_START)) {
            if (!isAlarmStarted) {
                init();
                start(this);
            }
            checkAppChanged();
        } else if (intent.getAction().equalsIgnoreCase(ACTION_STOP)) {
            stopAlarmAndStopSelf();
        }
        return START_STICKY;
    }

    private void init() {
        Log.d(TAG, "init() called");
        receiver = new ScreenOnOffReceiver();
        registerReceiver(receiver, screenOnOffFilter());
    }

    @NonNull
    private IntentFilter screenOnOffFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        return filter;
    }

    private void checkAppChanged() {
        String currentPackage = getCurrentAppPackage(LockService.this, activityManager);
        Log.d(TAG, "Package: " + currentPackage);
        if (currentPackage != null && !currentPackage.equalsIgnoreCase(lastPackage)) {
            if (currentPackage.equalsIgnoreCase("com.android.chrome")
                    || currentPackage.equalsIgnoreCase("com.google.android.youtube") ||
                    currentPackage.equalsIgnoreCase("com.facebook.katana")) {
                lockedAppOpened(currentPackage);
            } else {
                lockedAppClosed(currentPackage);
            }
            lastPackage = currentPackage;
        }
    }

    private void lockedAppOpened(String packageName) {
        Log.d(TAG, "Lock Package: " + packageName);
        Toast.makeText(this, "Lock Package " + packageName, Toast.LENGTH_SHORT).show();
        startService(AppLockService.lockIntent(this, packageName));
    }

    private void lockedAppClosed(String packageName) {
        Log.d(TAG, "Unlock Package: " + packageName);
        Toast.makeText(this, "Unlock Package " + packageName, Toast.LENGTH_SHORT).show();
        startService(AppLockService.unlockIntent(this, packageName));
    }

    public String getCurrentAppPackage(Context context, ActivityManager activityManager) {
        //isLockTypeAccessibility = SpUtil.getInstance().getBoolean(AppConstants.LOCK_TYPE, false);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            List<ActivityManager.RunningTaskInfo> appTasks = activityManager.getRunningTasks(1);
            if (null != appTasks && !appTasks.isEmpty()) {
                return appTasks.get(0).topActivity.getPackageName();
            }
        } else {
            long endTime = System.currentTimeMillis();
            long beginTime = endTime - 10000;
            String result = "";
            UsageEvents.Event event = new UsageEvents.Event();
            UsageEvents usageEvents = sUsageStatsManager.queryEvents(beginTime, endTime);
            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event);
                if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    result = event.getPackageName();
                }
            }
            if (!android.text.TextUtils.isEmpty(result)) {
                return result;
            }
        }
        return null;
    }

    public static void start(final Context context) {
        if (timer == null)
            timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(getRunIntent(context));
                } else {
                    context.startService(getRunIntent(context));
                }
            }
        }, 0, 1000L);
        isAlarmStarted = true;
    }

    public static void stop(Context context) {
        Log.d(TAG, "stop() called with: context = [" + context + "]");
        if (timer != null) {
            timer.cancel();
            timer.purge();
            timer = null;
        }
        isAlarmStarted = false;
    }

    private static Intent getRunIntent(Context context) {
        if (appLockServiceIntent == null) {
            appLockServiceIntent = new Intent(context, LockService.class);
            appLockServiceIntent.setAction(ACTION_START);
        }
        return appLockServiceIntent;
    }

    @Override
    public void onDestroy() {
        Log.d("AppLockService", "onDestroy called - " + destroy);
        if (receiver != null)
            unregisterReceiver(receiver);
        if (!destroy)
            start(this);

        destroy = false;
        super.onDestroy();
    }

    private void stopAlarmAndStopSelf() {
        destroy = true;
        stop(LockService.this);
        stopForeground(true);
        stopSelf();
    }

    private class ScreenOnOffReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                Log.d("AppLockService", "ACTION_SCREEN_ON");
                start(LockService.this);
            } else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                Log.d("AppLockService", "ACTION_SCREEN_OFF");
                lastPackage = "";
                stop(LockService.this);
            }
        }
    }
}

