package com.lina.launcher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Revisa cada poco qué app está al frente. Si es el launcher de fábrica
 * (botón casita o al encender), vuelve a poner a Lina al frente.
 */
public class WatchService extends Service {

    static volatile boolean running = false;
    static volatile String lastFront = "-";
    static volatile String homesText = "-";
    static volatile String lastError = "-";
    static volatile int launches = 0;
    static volatile String lastEvents = "-";
    static volatile long lastCheck = 0;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<String> homePackages = new HashSet<>();
    private long lastLaunch = 0;

    private final Runnable loop = new Runnable() {
        @Override
        public void run() {
            check();
            handler.postDelayed(this, 700);
        }
    };

    public static void start(Context c) {
        try {
            Intent i = new Intent(c, WatchService.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
        } catch (Exception ignored) { }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel ch = new NotificationChannel("lina", "Lina", NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            if (nm != null) nm.createNotificationChannel(ch);
            b = new Notification.Builder(this, "lina");
        } else {
            b = new Notification.Builder(this);
            b.setPriority(Notification.PRIORITY_MIN);
        }
        b.setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("Lina")
                .setContentText("Pantalla de inicio activa")
                .setOngoing(true);
        try {
            startForeground(77, b.build());
        } catch (Exception ignored) { }
        running = true;
        loadHomePackages();
        handler.post(loop);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacks(loop);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void loadHomePackages() {
        homePackages.clear();
        PackageManager pm = getPackageManager();
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        List<ResolveInfo> list = pm.queryIntentActivities(home, PackageManager.MATCH_ALL);
        for (ResolveInfo ri : list) {
            String p = ri.activityInfo.packageName;
            if (p.equals(getPackageName())) continue;
            if (p.equals("android") || p.contains("resolver")) continue;
            homePackages.add(p);
        }
        homesText = homePackages.toString();
    }

    // Estado que se conserva entre revisiones
    private long homeFg = 0, homeBg = 0, meFg = 0, queryFrom = 0;
    private final java.util.ArrayDeque<String> eventLog = new java.util.ArrayDeque<>();
    static volatile int checks = 0, bgChecks = 0;

    private void check() {
        try {
            UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return;
            long now = System.currentTimeMillis();
            if (queryFrom == 0) queryFrom = now - 30000;
            UsageEvents events = usm.queryEvents(queryFrom, now);
            UsageEvents.Event e = new UsageEvents.Event();
            if (homePackages.isEmpty()) loadHomePackages();
            String me = getPackageName();
            long newest = queryFrom;
            while (events.hasNextEvent()) {
                events.getNextEvent(e);
                int type = e.getEventType();
                String pkg = e.getPackageName();
                long ts = e.getTimeStamp();
                if (ts > newest) newest = ts;
                if (type != UsageEvents.Event.MOVE_TO_FOREGROUND && type != UsageEvents.Event.MOVE_TO_BACKGROUND) continue;
                boolean fg = type == UsageEvents.Event.MOVE_TO_FOREGROUND;
                if (fg) lastFront = pkg;
                if (homePackages.contains(pkg)) {
                    if (fg) homeFg = Math.max(homeFg, ts); else homeBg = Math.max(homeBg, ts);
                } else if (pkg.equals(me) && fg) {
                    meFg = Math.max(meFg, ts);
                }
                java.util.Calendar c = java.util.Calendar.getInstance();
                c.setTimeInMillis(ts);
                eventLog.addLast(String.format(java.util.Locale.ROOT, "%02d:%02d:%02d %s%s",
                        c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE), c.get(java.util.Calendar.SECOND),
                        pkg.replace("com.", ""), fg ? "+" : "-"));
                while (eventLog.size() > 10) eventLog.removeFirst();
            }
            // la próxima vez solo pedimos lo nuevo
            queryFrom = newest + 1;
            checks++;
            if (!me.equals(lastFront)) bgChecks++;
            lastCheck = now;
            if (!eventLog.isEmpty()) lastEvents = android.text.TextUtils.join("\n   ", eventLog);
            // El launcher de fábrica está abierto (entró y no ha salido) y es más nuevo que Lina
            if (homeFg > 0 && homeFg >= homeBg && homeFg > meFg) openLina();
        } catch (Exception ex) {
            lastError = String.valueOf(ex);
        }
    }

    private void openLina() {
        long t = SystemClock.elapsedRealtime();
        if (t - lastLaunch < 1500) return;
        lastLaunch = t;
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        try {
            startActivity(i);
            launches++;
        } catch (Exception ex) {
            lastError = "launch: " + ex;
        }
    }
}
