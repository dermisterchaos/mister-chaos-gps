package de.misterchaos.gpssender;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;

import java.util.Locale;

public class GpsForegroundService extends Service {
    private static final String CHANNEL_ID = "mister_chaos_gps";
    private static final int NOTIFICATION_ID = 77;

    private LocationManager locationManager;
    private PowerManager.WakeLock wakeLock;

    private String url = "";
    private String token = "";
    private int intervalSec = 2;

    private final LocationListener listener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            GpsUploader.upload(GpsForegroundService.this, url, token, location);
            updateNotification("GPS live: " + String.format(Locale.US, "%.6f, %.6f", location.getLatitude(), location.getLongitude()));
        }

        @Override public void onProviderEnabled(String provider) {}
        @Override public void onProviderDisabled(String provider) {}
        @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MisterChaosGPS:LiveTracking");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            url = intent.getStringExtra("url");
            token = intent.getStringExtra("token");
            intervalSec = intent.getIntExtra("interval", 2);
        }

        if (intervalSec < 1) intervalSec = 1;

        startForeground(NOTIFICATION_ID, buildNotification("GPS startet..."));

        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire();
        }

        startLocationUpdates();
        sendLastKnownImmediately();
        return START_STICKY;
    }

    private void startLocationUpdates() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            updateNotification("Standort-Berechtigung fehlt");
            stopSelf();
            return;
        }

        try {
            long intervalMs = intervalSec * 1000L;

            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, intervalMs, 0, listener);
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, intervalMs, 0, listener);

            updateNotification("GPS läuft im Hintergrund");
        } catch (Exception e) {
            updateNotification("GPS Fehler: " + e.getMessage());
        }
    }

    private void sendLastKnownImmediately() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;

        try {
            Location best = null;
            Location gps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location net = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

            if (gps != null) best = gps;
            if (net != null && (best == null || net.getTime() > best.getTime())) best = net;

            if (best != null) {
                GpsUploader.upload(this, url, token, best);
                updateNotification("Letzter Standort gesendet");
            } else {
                getSharedPreferences("cfg", MODE_PRIVATE).edit()
                        .putString("lastStatus", "Warte auf ersten GPS-Fix")
                        .apply();
            }
        } catch (Exception e) {
            getSharedPreferences("cfg", MODE_PRIVATE).edit()
                    .putString("lastStatus", "LastKnown Fehler: " + e.getMessage())
                    .apply();
        }
    }

    private void updateNotification(String text) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, buildNotification(text));
    }

    private Notification buildNotification(String text) {
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        builder.setContentTitle("Mister Chaos GPS läuft")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setOngoing(true);

        return builder.build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Mister Chaos GPS",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        try {
            if (locationManager != null) locationManager.removeUpdates(listener);
        } catch (Exception ignored) {}

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }

        getSharedPreferences("cfg", MODE_PRIVATE).edit()
                .putString("lastStatus", "Service beendet")
                .apply();

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
