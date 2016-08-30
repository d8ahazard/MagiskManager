package com.topjohnwu.magisk.utils;

import android.app.Service;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import com.jaredrummler.android.processes.AndroidProcesses;
import com.jaredrummler.android.processes.models.AndroidAppProcess;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MonitorService extends Service

{
    private UsageStatsManager mUsageStatsManager;
    private SharedPreferences prefs;
    private static final String TAG = "MyService";
    private Boolean disableroot;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public void onDestroy() {

    }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = PreferenceManager.getDefaultSharedPreferences(getApplication());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("Magisk","Monitor Service start command received");
        new Thread()
        {
            public void run() {
                CheckProcesses();
            }
        }.start();

        return START_NOT_STICKY;
    }

    private void CheckProcesses() {
        mUsageStatsManager = (UsageStatsManager) getApplication().getSystemService(Context.USAGE_STATS_SERVICE);
        Set<String> set = prefs.getStringSet("autoapps", null);

        List<String> arrayList = new ArrayList<>(set);

        disableroot = false;
        for (int i = 0; i < arrayList.size(); i++) {
            int currentapiVersion = Build.VERSION.SDK_INT;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (mUsageStatsManager.isAppInactive(arrayList.get(i).toString())) {
                    disableroot = true;
                }
            } else {
                List<AndroidAppProcess> processes = AndroidProcesses.getRunningAppProcesses();
                for (int f = 0; i < processes.size(); f++) {
                    AndroidAppProcess process = processes.get(f);
                    String processName = process.name;
                    if (processName.equals(arrayList.get(f).toString())) {
                        disableroot = true;
                    }
                }
            }
        }
        Log.d("Magisk","Check Processes called, result is " + disableroot);

    }
}

