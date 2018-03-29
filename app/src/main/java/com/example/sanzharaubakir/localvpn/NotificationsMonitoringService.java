package com.example.sanzharaubakir.localvpn;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.os.Process;

import java.util.List;

/**
 * Created by sanzharaubakir on 13.03.18.
 */

public class NotificationsMonitoringService extends NotificationListenerService {
    private static final String TAG = "NotificationMonitoring";
    private static final long NANOSEC_PER_SEC = 1000l*1000*1000;
    private static final long workTime = 15*60*NANOSEC_PER_SEC; // 15 minutes
    private PackageManager packageManager = null;
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Binded");
        return super.onBind(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId){
        Log.d(TAG, "I am here");
        packageManager = this.getPackageManager();
        this.checkNotificationListenerService();
        super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }
    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        Log.d(TAG, "Notification removed");

    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Log.d(TAG, "notification posted");
        String packageName = sbn.getPackageName();
        long postedTime = sbn.getPostTime();
        Log.d(TAG, "package - " + packageName);
        Log.d(TAG, "posted time - " + postedTime);
        Log.d(TAG, "to string - " + sbn.toString());

    }
    public void checkNotificationListenerService() {
        Log.d(TAG, "checkNotificationListenerService");
        boolean isNotificationListenerRunning = false;
        ComponentName thisComponent = new ComponentName(this, NotificationsMonitoringService.class);
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);

        List<ActivityManager.RunningServiceInfo> runningServices = manager.getRunningServices(Integer.MAX_VALUE);
        if(runningServices == null) {
            Log.d(TAG, "running services is null");
            return;
        }
        for(ActivityManager.RunningServiceInfo service : runningServices) {
            if(service.service.equals(thisComponent)) {
                Log.d(TAG, "checkNotificationListenerService service - pid: " + service.pid + ", currentPID: " + Process.myPid() + ", clientPackage: " + service.clientPackage + ", clientCount: " + service.clientCount + ", clientLabel: " + ((service.clientLabel == 0) ? "0" : "(" + getResources().getString(service.clientLabel) + ")"));
                if(service.pid == Process.myPid() /*&& service.clientCount > 0 && !TextUtils.isEmpty(service.clientPackage)*/) {
                    isNotificationListenerRunning = true;
                }
            }
        }
        if(isNotificationListenerRunning) {
            Log.d(TAG, "NotificationListenerService is running");
            return;
        }
        Log.d(TAG, "NotificationListenerService is not running, trying to start");
        this.toggleNotificationListenerService();
    }

    public void toggleNotificationListenerService() {
        Log.d(TAG, "toggleNotificationListenerService");
        // adb shell dumpsys notification
        // force start of notification service
        ComponentName thisComponent = new ComponentName(this, NotificationsMonitoringService.class);
        packageManager.setComponentEnabledSetting(thisComponent, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        packageManager.setComponentEnabledSetting(thisComponent, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
    }
}
