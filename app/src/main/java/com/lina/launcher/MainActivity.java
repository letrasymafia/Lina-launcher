package com.lina.launcher;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private WebView web;
    private static boolean askedGuardian = false;
    private LocationManager locationManager;
    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            int kmh = 0;
            if (location != null && location.hasSpeed()) {
                kmh = Math.round(location.getSpeed() * 3.6f);
            }
            final int value = kmh;
            if (web != null) {
                web.post(() -> web.evaluateJavascript("setSpeed(" + value + ")", null));
            }
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) { }

        @Override
        public void onProviderEnabled(String provider) { }

        @Override
        public void onProviderDisabled(String provider) { }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        web.setBackgroundColor(0xFFF8C9DA);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        web.addJavascriptInterface(new Bridge(), "Android");
        web.loadUrl("file:///android_asset/index.html");
        setContentView(web);
        askLocation();
    }

    private void askLocation() {
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, 7);
        } else {
            startSpeed();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        startSpeed();
    }

    private void startSpeed() {
        try {
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, locationListener);
            }
        } catch (SecurityException | IllegalArgumentException e) {
            // Sin permiso o sin GPS: la velocidad se queda en 0
        }
    }

    @Override
    protected void onDestroy() {
        try {
            if (locationManager != null) locationManager.removeUpdates(locationListener);
        } catch (Exception ignored) { }
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!askedGuardian && !guardianEnabled()) {
            askedGuardian = true;
            new AlertDialog.Builder(this)
                    .setTitle("Dejar a Lina como inicio")
                    .setMessage("Para que la casita siempre muestre esta pantalla, activa \"Lina siempre al inicio\" en la siguiente pantalla (Accesibilidad).")
                    .setPositiveButton("Activar", (d, w) -> startSafe(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                    .setNegativeButton("Ahora no", null)
                    .show();
        }
    }

    private boolean guardianEnabled() {
        try {
            String list = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return list != null && list.contains(getPackageName() + "/");
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void onBackPressed() {
        // Es la pantalla de inicio: el botón atrás no hace nada aquí
    }

    // ---------- Abrir aplicaciones ----------

    private boolean startSafe(Intent i) {
        if (i == null) return false;
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }

    private boolean openPackage(String... packages) {
        PackageManager pm = getPackageManager();
        for (String p : packages) {
            if (startSafe(pm.getLaunchIntentForPackage(p))) return true;
        }
        return false;
    }

    private boolean openByName(String... words) {
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(main, 0);
        for (String w : words) {
            String word = w.toLowerCase(Locale.ROOT);
            for (ResolveInfo ri : apps) {
                if (ri.activityInfo.packageName.equals(getPackageName())) continue;
                String label = String.valueOf(ri.loadLabel(pm)).toLowerCase(Locale.ROOT);
                if (label.contains(word)) {
                    Intent i = new Intent(Intent.ACTION_MAIN);
                    i.addCategory(Intent.CATEGORY_LAUNCHER);
                    i.setClassName(ri.activityInfo.packageName, ri.activityInfo.name);
                    if (startSafe(i)) return true;
                }
            }
        }
        return false;
    }

    private void openApps() {
        startSafe(new Intent(this, AppsActivity.class));
    }

    private void sendMediaKey(int code) {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
        am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
    }

    private class Bridge {
        @JavascriptInterface
        public void openApps() {
            runOnUiThread(MainActivity.this::openApps);
        }

        @JavascriptInterface
        public void openMaps() {
            runOnUiThread(() -> {
                if (openPackage("com.waze", "com.google.android.apps.maps")) return;
                if (startSafe(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")))) return;
                MainActivity.this.openApps();
            });
        }

        @JavascriptInterface
        public void openRadio() {
            runOnUiThread(() -> {
                if (openByName("radio", "fm")) return;
                MainActivity.this.openApps();
            });
        }

        @JavascriptInterface
        public void openMusic() {
            runOnUiThread(() -> {
                if (openByName("música", "musica", "music", "spotify")) return;
                try {
                    Intent i = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC);
                    if (startSafe(i)) return;
                } catch (Exception ignored) { }
                MainActivity.this.openApps();
            });
        }

        @JavascriptInterface
        public void openPhone() {
            runOnUiThread(() -> {
                if (openByName("teléfono", "telefono", "phone", "bluetooth", "bt ")) return;
                if (startSafe(new Intent(Intent.ACTION_DIAL))) return;
                MainActivity.this.openApps();
            });
        }

        @JavascriptInterface
        public void openSettings() {
            runOnUiThread(() -> {
                if (startSafe(new Intent(Settings.ACTION_SETTINGS))) return;
                openByName("configuración", "ajustes", "settings");
            });
        }

        @JavascriptInterface
        public void media(String action) {
            runOnUiThread(() -> {
                if ("prev".equals(action)) sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
                else if ("next".equals(action)) sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT);
                else sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
            });
        }
    }
}
