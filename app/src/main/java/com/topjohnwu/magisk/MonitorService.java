package com.topjohnwu.magisk;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageStatsManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.topjohnwu.magisk.utils.Shell;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class MonitorService extends Service

{

    private UsageStatsManager mUsageStatsManager;
    private SharedPreferences prefs;
    private static final String TAG = "Magisk";
    private Boolean disableroot;
    private Boolean disablerootprev;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        timer.start();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG,"Destroyah!");
        android.os.Process.killProcess(android.os.Process.myPid());
        super.onDestroy();
    }

    private CountDownTimer timer = new CountDownTimer(1000, 20) {

        @Override
        public void onTick(long millisUntilFinished) {

        }

        @Override
        public void onFinish() {
            CheckProcesses();

        }
    }.start();

    private void CheckProcesses() {

        Set<String> set = prefs.getStringSet("autoapps", null);

        ArrayList<String> arrayList = null;
        if (set != null) {
            arrayList = new ArrayList<>(set);


            disableroot = false;
            for (int i = 0; i < arrayList.size(); i++) {
                arrayList.get(i);
                PackageManager pm = getApplication().getPackageManager();
                List<String> stdout = Shell.su("ps");
                List<String> packages = new ArrayList<>();
                try {
                    for (String line : stdout) {
                        // Get the process-name. It is the last column.
                        String[] arr = line.split("\\s+");
                        String processName = arr[arr.length - 1].split(":")[0];
                        packages.add(processName);
                    }
                } catch (NullPointerException | ArrayIndexOutOfBoundsException e) {
                    Log.e(TAG, "Error, nullpointerexception: " + e);
                }

// Get a list of all installed apps on the device.
                List<ApplicationInfo> apps = pm.getInstalledApplications(0);

// Remove apps which are not running.
                for (Iterator<ApplicationInfo> it = apps.iterator(); it.hasNext(); ) {
                    if (!packages.contains(it.next().packageName)) {
                        it.remove();
                    }

                }

                if (packages.contains(arrayList.get(i))) {
                    disableroot = true;
                }
            }
        }
        if (disableroot != disablerootprev) {
            String rootstatus = (disableroot ? "disabled" : "enabled");
            Shell.su((disableroot ? "setprop magisk.root 0" : "setprop magisk.root 1"));
            NotificationManager mNotifyMgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationCompat.Builder mBuilder;
            mNotifyMgr.cancelAll();
            if (disableroot) {
                Intent intent = new Intent(this, WelcomeActivity.class);
                PendingIntent pendingIntent = PendingIntent.getActivity(
                        this,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT);

                mBuilder =
                        new NotificationCompat.Builder(this)
                                .setSmallIcon(disableroot ? R.drawable.ic_stat_notification_autoroot_off : R.drawable.ic_stat_notification_autoroot_on)
                                .setContentIntent(pendingIntent)
                                .setContentTitle("Auto-root status changed")
                                .setContentText("Auto root has been " + rootstatus + "!  Tap to re-enable when done.");
                timer.cancel();
                this.stopSelf();
            } else {
                mBuilder =
                        new NotificationCompat.Builder(this)
                                .setAutoCancel(true)
                                .setSmallIcon(disableroot ? R.drawable.ic_stat_notification_autoroot_off : R.drawable.ic_stat_notification_autoroot_on)
                                .setContentTitle("Auto-root status changed")
                                .setContentText("Auto root has been " + rootstatus + "!");
            }
// Builds the notification and issues it.
            int mNotificationId = 001;
            mNotifyMgr.notify(mNotificationId, mBuilder.build());

        }
        disablerootprev = disableroot;


        Log.d(TAG, "Check Processes finished, result is " + disableroot);
        timer.start();
    }


}

