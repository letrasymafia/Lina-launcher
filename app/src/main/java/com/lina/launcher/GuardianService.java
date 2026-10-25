package com.lina.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Cuando la pantalla del carro abre su launcher de fábrica (botón casita o al encender),
 * este servicio vuelve a poner a Lina al frente.
 */
public class GuardianService extends AccessibilityService {

    private final Set<String> homePackages = new HashSet<>();
    private long lastLaunch = 0;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        loadHomePackages();
        openLina();
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
            // El "selector" de Android no es un launcher real
            if (p.equals("android") || p.contains("resolver")) continue;
            homePackages.add(p);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = event.getPackageName();
        if (pkg == null) return;
        if (homePackages.isEmpty()) loadHomePackages();
        if (homePackages.contains(pkg.toString())) {
            openLina();
        }
    }

    private void openLina() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastLaunch < 700) return;
        lastLaunch = now;
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        try {
            startActivity(i);
        } catch (Exception ignored) { }
    }

    @Override
    public void onInterrupt() { }
}
