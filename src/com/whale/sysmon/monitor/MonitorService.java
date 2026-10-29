package com.whale.sysmon.monitor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import com.whale.sysmon.MainActivity;
import com.whale.sysmon.data.Bat;
import com.whale.sysmon.data.Cpu;
import com.whale.sysmon.data.Mem;
import com.whale.sysmon.data.SysLoad;
import com.whale.sysmon.data.Thermal;
import com.whale.sysmon.page.SettingsPage;

import java.util.Locale;

/**
 * 后台常驻监控：前台服务 + 通知栏实时指标 + 超阈值告警。
 * 同一指标 10 分钟内只提醒一次，避免刷屏。
 */
public class MonitorService extends Service {

    public static final String CH_RUN = "sysmon_run";
    public static final String CH_ALERT = "sysmon_alert";
    private static final int ID_RUN = 1001;
    private static final int ID_ALERT = 1002;

    private static final long ALERT_GAP_MS = 10 * 60 * 1000L;

    private final Handler h = new Handler(Looper.getMainLooper());
    private final SysLoad load = new SysLoad();
    private Cpu cpu;

    private long lastAlertCpu = 0, lastAlertTemp = 0, lastAlertMem = 0;
    private long intervalMs = 3000L;
    private volatile boolean running = false;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            try { sampleOnce(); } catch (Throwable ignored) {}
            h.postDelayed(this, intervalMs);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        cpu = Cpu.read();
        createChannels();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        SharedPreferences sp = getSharedPreferences(SettingsPage.PREFS, Context.MODE_PRIVATE);
        intervalMs = Math.max(1000, sp.getInt(SettingsPage.K_INTERVAL, 1000) * 3L);

        startForeground(ID_RUN, buildRunNotification("--", "--", "--", "--"));
        running = true;
        h.removeCallbacks(tick);
        h.post(tick);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        h.removeCallbacks(tick);
        try { stopForeground(true); } catch (Throwable ignored) {}
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    /* ---------------------------- 采样与告警 ---------------------------- */

    private void sampleOnce() {
        SharedPreferences sp = getSharedPreferences(SettingsPage.PREFS, Context.MODE_PRIVATE);
        int thCpu = sp.getInt(SettingsPage.K_ALERT_CPU, 70);
        int thTemp = sp.getInt(SettingsPage.K_ALERT_TEMP, 45);
        int thMem = sp.getInt(SettingsPage.K_ALERT_MEM, 75);

        load.sample();
        float cpuU;
        if (load.blocked) {
            cpu.refreshCur();
            cpuU = cpu.avgFreqRatio();
        } else {
            cpuU = load.last();
        }

        Mem mem = Mem.read();
        float memU = mem.usedRatio();
        Thermal th = Thermal.read();
        Bat bat = Bat.read(this);
        float temp = !Float.isNaN(th.max) ? th.max : bat.tempC();
        int pct = bat.percent();

        String cpuS = Math.round(cpuU * 100) + "%";
        String memS = Math.round(memU * 100) + "%";
        String tempS = Float.isNaN(temp) ? "--" : String.format(Locale.US, "%.1f℃", temp);
        String batS = pct >= 0 ? pct + "%" : "--";

        NotificationManager nm = (NotificationManager)
                getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(ID_RUN, buildRunNotification(cpuS, memS, tempS, batS));
        }

        long now = System.currentTimeMillis();
        if (thCpu > 0 && cpuU * 100 >= thCpu && now - lastAlertCpu > ALERT_GAP_MS) {
            lastAlertCpu = now;
            alert("CPU 偏高", "当前 " + cpuS + "，阈值 " + thCpu + "%", nm);
        }
        if (thTemp > 0 && !Float.isNaN(temp) && temp >= thTemp && now - lastAlertTemp > ALERT_GAP_MS) {
            lastAlertTemp = now;
            alert("温度偏高", "当前 " + tempS + "，阈值 " + thTemp + "℃", nm);
        }
        if (thMem > 0 && memU * 100 >= thMem && now - lastAlertMem > ALERT_GAP_MS) {
            lastAlertMem = now;
            alert("内存偏高", "当前 " + memS + "，阈值 " + thMem + "%", nm);
        }
    }

    private void alert(String title, String body, NotificationManager nm) {
        if (nm == null) return;
        Notification n = new Notification.Builder(this, CH_ALERT)
                .setContentTitle(title)
                .setContentText(body)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setAutoCancel(true)
                .setContentIntent(openIntent())
                .build();
        nm.notify(ID_ALERT, n);
    }

    private Notification buildRunNotification(String cpuS, String memS,
                                              String tempS, String batS) {
        String text = "CPU " + cpuS + "   内存 " + memS + "   " + tempS + "   " + batS;
        return new Notification.Builder(this, CH_RUN)
                .setContentTitle("观测台 · 后台监控中")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openIntent())
                .build();
    }

    private PendingIntent openIntent() {
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = Build.VERSION.SDK_INT >= 23
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;
        return PendingIntent.getActivity(this, 0, i, flags);
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager)
                getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        NotificationChannel run = new NotificationChannel(CH_RUN, "后台监控",
                NotificationManager.IMPORTANCE_DEFAULT);
        run.setDescription("通知栏常驻的实时指标");
        run.setShowBadge(false);
        run.setSound(null, null);          // DEFAULT 但静默，避免被系统归入「更多通知」
        run.enableVibration(false);
        run.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(run);

        NotificationChannel alert = new NotificationChannel(CH_ALERT, "性能告警",
                NotificationManager.IMPORTANCE_DEFAULT);
        alert.setDescription("超过阈值时的提醒");
        nm.createNotificationChannel(alert);
    }
}
