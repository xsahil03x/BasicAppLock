package com.example.primeos.accessibilitytest;

import android.app.Application;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;

import com.andrognito.patternlockview.PatternLockView;
import com.andrognito.patternlockview.listener.PatternLockViewListener;
import com.andrognito.patternlockview.utils.PatternLockUtils;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class AppLockService extends Service {

    public static final String ACTION_LOCK = "com.applock.intent.action_lock";
    public static final String ACTION_UNLOCK = "com.applock.intent.action_unlock";
    public static String EXTRA_PACKAGENAME = "EXTRA_PACKAGENAME";
    private WindowManager mWindowManager;
    private View mLockScreenView;
    private WindowManager.LayoutParams mLockScreenViewParams;
    private PatternLockView mPatternLockView;
    private Application application;

    private static final String TAG = "AppLockService";

    public static Intent lockIntent(Context context, String packageName) {
        Intent intent = new Intent(context, AppLockService.class);
        intent.setAction(ACTION_LOCK);
        intent.putExtra(EXTRA_PACKAGENAME, packageName);
        return intent;
    }

    public static Intent unlockIntent(Context context, String packageName) {
        Intent intent = new Intent(context, AppLockService.class);
        intent.setAction(ACTION_UNLOCK);
        intent.putExtra(EXTRA_PACKAGENAME, packageName);
        return intent;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }
//        application = new InstalledApplication(this).locked(intent.getExtras().getString(EXTRA_PACKAGENAME));
        Log.d("AppLockService", "LockService onStartCommand: " + intent.getExtras().getString(EXTRA_PACKAGENAME));
        if (intent.getAction().equals(ACTION_LOCK))
            showLock();
        else if (intent.getAction().equals(ACTION_UNLOCK))
            hideLock();

        return START_NOT_STICKY;
    }

    private void prepareView() {

        // Inflate the lockscreen layout
        if (mLockScreenView == null)
            mLockScreenView = LayoutInflater.from(this).inflate(R.layout.app_lock_view, null);

        mPatternLockView = mLockScreenView.findViewById(R.id.patter_lock_view);
        mPatternLockView.addPatternLockListener(mPatternLockViewListener);
        mLockScreenView.setFocusableInTouchMode(true);
        mLockScreenView.requestFocus();
        mLockScreenView.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK
                        && event.getAction() == KeyEvent.ACTION_UP) {
                    Intent startMain = new Intent(Intent.ACTION_MAIN);
                    startMain.addCategory(Intent.CATEGORY_HOME);
                    startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(startMain);
                    new Timer().schedule(new TimerTask() {
                        @Override
                        public void run() {
                            hideLock();
                        }
                    }, 300L);
                }
                return true;
            }
        });

        // Create lockscreen view params
        mLockScreenViewParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_FULLSCREEN |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);

        //Specify the view position of lockscreen
        mLockScreenViewParams.gravity = Gravity.TOP | Gravity.END;
        mLockScreenViewParams.x = ((getApplicationContext().getResources().getDisplayMetrics().widthPixels) / 2);
        mLockScreenViewParams.y = ((getApplicationContext().getResources().getDisplayMetrics().heightPixels) / 2);

    }

    private PatternLockViewListener mPatternLockViewListener = new PatternLockViewListener() {
        @Override
        public void onStarted() {
            Log.d(getClass().getName(), "Pattern drawing started");
        }

        @Override
        public void onProgress(List<PatternLockView.Dot> progressPattern) {
            Log.d(getClass().getName(), "Pattern progress: " +
                    PatternLockUtils.patternToString(mPatternLockView, progressPattern));
        }

        @Override
        public void onComplete(List<PatternLockView.Dot> pattern) {
            Log.d(getClass().getName(), "Pattern complete: " +
                    PatternLockUtils.patternToString(mPatternLockView, pattern));
            if (PatternLockUtils.patternToString(mPatternLockView, pattern).equals("012")) {
                mPatternLockView.setViewMode(PatternLockView.PatternViewMode.CORRECT);
                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        hideLock();
                    }
                }, 300L);
            } else {
                mPatternLockView.setViewMode(PatternLockView.PatternViewMode.WRONG);
                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        mPatternLockView.clearPattern();
                    }
                }, 300L);
            }
        }

        @Override
        public void onCleared() {
            Log.d(getClass().getName(), "Pattern has been cleared");
        }
    };

    private void showLock() {
        try {
            prepareView();
            windowManager().addView(mLockScreenView, mLockScreenViewParams);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void hideLock() {
        try {
            if (mLockScreenView != null)
                windowManager().removeView(mLockScreenView);
            mLockScreenView = null;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private WindowManager windowManager() {
        if (mWindowManager == null)
            mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        return mWindowManager;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mLockScreenView != null)
            mWindowManager.removeView(mLockScreenView);
        mLockScreenView = null;
    }
}
