package de.misterchaos.gpssender;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class GpsUploader {
    public static void upload(Context context, String url, String token, Location location) {
        new Thread(() -> {
            SharedPreferences prefs = context.getSharedPreferences("cfg", Context.MODE_PRIVATE);
            try {
                String coords = String.format(Locale.US, "%.7f, %.7f", location.getLatitude(), location.getLongitude());
                prefs.edit()
                        .putString("lastStatus", "Sende Standort...")
                        .putString("lastCoords", coords)
                        .apply();

                String params =
                        "action=mcirl_direct_gps_push" +
                                "&token=" + enc(token) +
                                "&lat=" + enc(String.valueOf(location.getLatitude())) +
                                "&lng=" + enc(String.valueOf(location.getLongitude())) +
                                "&accuracy=" + enc(String.valueOf(location.getAccuracy())) +
                                "&altitude=" + enc(location.hasAltitude() ? String.valueOf(location.getAltitude()) : "") +
                                "&heading=" + enc(location.hasBearing() ? String.valueOf(location.getBearing()) : "") +
                                "&speed=" + enc(location.hasSpeed() ? String.valueOf(location.getSpeed()) : "") +
                                "&clientTime=" + enc(String.valueOf(System.currentTimeMillis()));

                URL endpoint = new URL(url);
                HttpURLConnection conn = (HttpURLConnection) endpoint.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                conn.setRequestProperty("User-Agent", "MisterChaosGPS/1.1");

                OutputStream os = conn.getOutputStream();
                os.write(params.getBytes("UTF-8"));
                os.flush();
                os.close();

                int code = conn.getResponseCode();

                BufferedReader br;
                if (code >= 200 && code < 300) br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                else br = new BufferedReader(new InputStreamReader(conn.getErrorStream()));

                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) response.append(line);
                br.close();

                String now = new SimpleDateFormat("HH:mm:ss", Locale.GERMANY).format(new Date());

                prefs.edit()
                        .putString("lastHttp", String.valueOf(code))
                        .putString("lastUpload", now)
                        .putString("lastStatus", code >= 200 && code < 300 ? "Upload OK" : "Upload Fehler: " + code)
                        .putString("lastResponse", response.toString())
                        .apply();

                conn.disconnect();
            } catch (Exception e) {
                prefs.edit()
                        .putString("lastStatus", "Upload Fehler: " + e.getMessage())
                        .putString("lastHttp", "FEHLER")
                        .apply();
            }
        }).start();
    }


    public static void setMapVisibility(Context context, String url, String token, boolean enabled) {
        new Thread(() -> {
            SharedPreferences prefs = context.getSharedPreferences("cfg", Context.MODE_PRIVATE);
            try {
                prefs.edit()
                        .putString("lastStatus", enabled ? "Kartenmodus AN senden..." : "Kartenmodus AUS senden...")
                        .apply();

                String base = url;
                if (base.contains("wp-admin/admin-ajax.php")) {
                    base = base.substring(0, base.indexOf("wp-admin/admin-ajax.php"));
                }
                if (!base.endsWith("/")) {
                    int lastSlash = base.lastIndexOf("/");
                    if (lastSlash > "https://".length()) {
                        base = base.substring(0, lastSlash + 1);
                    } else {
                        base = base + "/";
                    }
                }

                String fullUrl = base +
                        "?mcirl_gps_visibility=1" +
                        "&token=" + enc(token) +
                        "&enabled=" + enc(enabled ? "1" : "0");

                URL endpoint = new URL(fullUrl);
                HttpURLConnection conn = (HttpURLConnection) endpoint.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("User-Agent", "MisterChaosGPS/1.3");

                int code = conn.getResponseCode();

                BufferedReader br;
                if (code >= 200 && code < 300) br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                else br = new BufferedReader(new InputStreamReader(conn.getErrorStream()));

                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) response.append(line);
                br.close();

                String now = new SimpleDateFormat("HH:mm:ss", Locale.GERMANY).format(new Date());

                prefs.edit()
                        .putString("lastHttp", String.valueOf(code))
                        .putString("lastUpload", now)
                        .putString("lastStatus", code >= 200 && code < 300 ? (enabled ? "Kartenmodus AN OK" : "Kartenmodus AUS OK") : "Kartenmodus Fehler: " + code)
                        .putString("lastResponse", response.toString())
                        .apply();

                conn.disconnect();
            } catch (Exception e) {
                prefs.edit()
                        .putString("lastStatus", "Kartenmodus Fehler: " + e.getMessage())
                        .putString("lastHttp", "FEHLER")
                        .apply();
            }
        }).start();
    }


    public static void sendOffline(Context context, String url, String token) {
        new Thread(() -> {
            SharedPreferences prefs = context.getSharedPreferences("cfg", Context.MODE_PRIVATE);
            try {
                prefs.edit()
                        .putString("lastStatus", "GPS AUS an Webseite senden...")
                        .apply();

                String base = url;
                if (base.contains("wp-admin/admin-ajax.php")) {
                    base = base.substring(0, base.indexOf("wp-admin/admin-ajax.php"));
                }
                if (!base.endsWith("/")) {
                    int lastSlash = base.lastIndexOf("/");
                    if (lastSlash > "https://".length()) {
                        base = base.substring(0, lastSlash + 1);
                    } else {
                        base = base + "/";
                    }
                }

                String fullUrl = base +
                        "?mcirl_gps_offline=1" +
                        "&token=" + enc(token);

                URL endpoint = new URL(fullUrl);
                HttpURLConnection conn = (HttpURLConnection) endpoint.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("User-Agent", "MisterChaosGPS/1.4");

                int code = conn.getResponseCode();

                BufferedReader br;
                if (code >= 200 && code < 300) br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                else br = new BufferedReader(new InputStreamReader(conn.getErrorStream()));

                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) response.append(line);
                br.close();

                String now = new SimpleDateFormat("HH:mm:ss", Locale.GERMANY).format(new Date());

                prefs.edit()
                        .putString("lastHttp", String.valueOf(code))
                        .putString("lastUpload", now)
                        .putString("lastStatus", code >= 200 && code < 300 ? "GPS AUS OK" : "GPS AUS Fehler: " + code)
                        .putString("lastResponse", response.toString())
                        .apply();

                conn.disconnect();
            } catch (Exception e) {
                prefs.edit()
                        .putString("lastStatus", "GPS AUS Fehler: " + e.getMessage())
                        .putString("lastHttp", "FEHLER")
                        .apply();
            }
        }).start();
    }

    private static String enc(String value) throws Exception {
        return URLEncoder.encode(value == null ? "" : value, "UTF-8");
    }
}
