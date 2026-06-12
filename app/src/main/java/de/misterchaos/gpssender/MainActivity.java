package de.misterchaos.gpssender;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int REQ_LOCATION = 1001;
    private static final int REQ_NOTIFY = 1002;

    private TextView status;
    private TextView debug;
    private EditText urlInput;
    private EditText tokenInput;
    private EditText intervalInput;
    private Handler handler = new Handler();

    private final String defaultUrl = "https://misterchaos.de/wp-admin/admin-ajax.php";
    private final String defaultToken = "gps_NFGVC9OUXJGZif1C12akGfpf67dz";

    private final Runnable refreshUi = new Runnable() {
        @Override public void run() {
            updateDebugText();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        loadPrefs();
        requestNotificationPermission();
        handler.post(refreshUi);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(refreshUi);
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);
        root.setBackgroundColor(Color.rgb(2, 7, 17));
        scroll.addView(root);

        TextView title = text("Mister Chaos GPS v1.6", 30, true);
        root.addView(title);

        TextView sub = text("Sendet deinen exakten Standort direkt an misterchaos.de. Alte unaux.com URLs werden automatisch ersetzt.", 15, false);
        sub.setTextColor(Color.rgb(185, 201, 223));
        root.addView(sub);

        root.addView(label("WordPress AJAX URL"));
        urlInput = edit("URL");
        root.addView(urlInput);

        root.addView(label("GPS Token"));
        tokenInput = edit("Token");
        root.addView(tokenInput);

        root.addView(label("Intervall in Sekunden"));
        intervalInput = edit("2");
        root.addView(intervalInput);

        Button start = button("GPS AN");
        Button stop = button("GPS AUS");
        Button mapOn = button("Kartenmodus AN");
        Button mapOff = button("Kartenmodus AUS");
        Button testLast = button("Letzten bekannten Standort senden");
        Button close = button("App schließen");
        Button battery = button("Akku-Optimierung öffnen");
        Button resetServer = button("Server auf misterchaos.de setzen");

        start.setOnClickListener(v -> startGps());
        stop.setOnClickListener(v -> stopGps());
        mapOn.setOnClickListener(v -> setMapVisibility(true));
        mapOff.setOnClickListener(v -> setMapVisibility(false));
        testLast.setOnClickListener(v -> sendLastKnownOnce());
        close.setOnClickListener(v -> finish());
        battery.setOnClickListener(v -> openBatterySettings());
        resetServer.setOnClickListener(v -> resetServerUrl());

        root.addView(start);
        root.addView(stop);
        root.addView(mapOn);
        root.addView(mapOff);
        root.addView(testLast);
        root.addView(close);
        root.addView(battery);
        root.addView(resetServer);

        status = text("Status: Bereit", 16, true);
        status.setPadding(0, 24, 0, 0);
        root.addView(status);

        debug = text("Debug: --", 14, false);
        debug.setTextColor(Color.rgb(210, 228, 255));
        root.addView(debug);

        setContentView(scroll);
    }

    private TextView text(String value, int size, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(Color.WHITE);
        if (bold) v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        v.setPadding(0, 8, 0, 12);
        return v;
    }

    private TextView label(String value) {
        TextView v = text(value, 13, true);
        v.setTextColor(Color.rgb(0, 183, 255));
        return v;
    }

    private EditText edit(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(Color.rgb(120, 140, 165));
        e.setSingleLine(true);
        e.setPadding(18, 14, 18, 14);
        e.setBackgroundColor(Color.rgb(8, 24, 45));
        return e;
    }

    private Button button(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextSize(17);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.rgb(10, 108, 255));
        b.setPadding(16, 18, 16, 18);
        return b;
    }

    private void loadPrefs() {
        SharedPreferences p = getSharedPreferences("cfg", MODE_PRIVATE);

        String savedUrl = p.getString("url", defaultUrl);
        if (savedUrl == null || savedUrl.trim().isEmpty() || savedUrl.contains("misterchaos.unaux.com") || savedUrl.contains("unaux.com")) {
            savedUrl = defaultUrl;
            p.edit().putString("url", savedUrl).apply();
        }

        urlInput.setText(savedUrl);
        tokenInput.setText(p.getString("token", defaultToken));
        intervalInput.setText(p.getString("interval", "2"));
    }

    private void savePrefs() {
        getSharedPreferences("cfg", MODE_PRIVATE).edit()
                .putString("url", urlInput.getText().toString().trim())
                .putString("token", tokenInput.getText().toString().trim())
                .putString("interval", intervalInput.getText().toString().trim())
                .apply();
    }

    private void startGps() {
        savePrefs();

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            return;
        }

        Intent service = new Intent(this, GpsForegroundService.class);
        service.putExtra("url", urlInput.getText().toString().trim());
        service.putExtra("token", tokenInput.getText().toString().trim());

        int interval = 2;
        try {
            interval = Math.max(1, Integer.parseInt(intervalInput.getText().toString().trim()));
        } catch (Exception ignored) {}

        service.putExtra("interval", interval);

        if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
        else startService(service);

        status.setText("Status: GPS läuft. App kann geschlossen werden.");
    }

    private void stopGps() {
        Intent i = new Intent(this, GpsForegroundService.class);
        stopService(i);
        savePrefs();

        String url = urlInput.getText().toString().trim();
        String token = tokenInput.getText().toString().trim();
        GpsUploader.sendOffline(this, url, token);

        status.setText("Status: GPS AUS wird an Webseite gesendet.");
    }

    private void sendLastKnownOnce() {
        savePrefs();
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            return;
        }

        try {
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            Location best = null;
            Location gps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location net = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

            if (gps != null) best = gps;
            if (net != null && (best == null || net.getTime() > best.getTime())) best = net;

            if (best == null) {
                status.setText("Status: Kein letzter Standort verfügbar. GPS AN drücken und kurz warten.");
                return;
            }

            GpsUploader.upload(this, urlInput.getText().toString().trim(), tokenInput.getText().toString().trim(), best);
            status.setText("Status: Letzter bekannter Standort wird gesendet.");
        } catch (Exception e) {
            status.setText("Status: Fehler: " + e.getMessage());
        }
    }


    private void setMapVisibility(boolean enabled) {
        savePrefs();
        String url = urlInput.getText().toString().trim();
        String token = tokenInput.getText().toString().trim();

        GpsUploader.setMapVisibility(this, url, token, enabled);
        status.setText(enabled ? "Status: Kartenmodus AN wird gesendet." : "Status: Kartenmodus AUS wird gesendet.");
    }

    private void resetServerUrl() {
        urlInput.setText(defaultUrl);
        savePrefs();
        status.setText("Status: Server wurde auf misterchaos.de gesetzt.");
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFY);
        }
    }

    private void openBatterySettings() {
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS));
        }
    }

    private void updateDebugText() {
        SharedPreferences p = getSharedPreferences("cfg", MODE_PRIVATE);
        String s =
                "Letzter Status: " + p.getString("lastStatus", "--") + "\n" +
                "Letzte Koordinaten: " + p.getString("lastCoords", "--") + "\n" +
                "Letzter HTTP Code: " + p.getString("lastHttp", "--") + "\n" +
                "Letzter Upload: " + p.getString("lastUpload", "--");
        debug.setText(s);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_LOCATION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startGps();
        } else if (requestCode == REQ_LOCATION) {
            status.setText("Status: Standort-Berechtigung fehlt.");
        }
    }
}
