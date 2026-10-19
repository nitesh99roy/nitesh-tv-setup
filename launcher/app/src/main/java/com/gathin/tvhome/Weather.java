package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.Locale;

/**
 * Current weather for the welcome block, from Open-Meteo (free, no account, no key).
 *
 * Location: the city in config.xml (weather_city) if set, otherwise the TV's own last known
 * location from Wi-Fi, rounded to about a kilometre before it leaves the TV. Results are cached
 * for 30 minutes and kept across restarts, so coming Home never waits on the network.
 */
final class Weather {
    interface Listener {
        void onWeather(Result r);
    }

    static final class Result {
        final int temp;
        final int code;
        final boolean day;
        final String place;
        final long at;

        Result(int temp, int code, boolean day, String place, long at) {
            this.temp = temp;
            this.code = code;
            this.day = day;
            this.place = place;
            this.at = at;
        }

        String describe() {
            switch (WeatherIconView.kindFor(code)) {
                case WeatherIconView.CLEAR: return day ? "Clear" : "Clear night";
                case WeatherIconView.PARTLY: return code == 1 ? "Mostly clear" : "Partly cloudy";
                case WeatherIconView.FOG: return "Fog";
                case WeatherIconView.DRIZZLE: return "Drizzle";
                case WeatherIconView.RAIN: return code >= 80 ? "Showers" : "Rain";
                case WeatherIconView.SNOW: return "Snow";
                case WeatherIconView.STORM: return "Thunderstorm";
                default: return "Cloudy";
            }
        }
    }

    private static final long STALE_MS = 30 * 60_000L;
    private static final String PREFS = "weather";

    private final Context ctx;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final boolean enabled;
    private final String city;
    private final boolean fahrenheit;
    private boolean busy;

    Weather(Context ctx, Listener listener) {
        this.ctx = ctx.getApplicationContext();
        this.listener = listener;
        enabled = ctx.getResources().getBoolean(R.bool.weather_enabled);
        city = ctx.getString(R.string.weather_city).trim();
        fahrenheit = "fahrenheit".equalsIgnoreCase(ctx.getString(R.string.weather_units).trim());
    }

    /** Show the cached reading at once, and fetch a new one in the background if it is stale. */
    void refresh() {
        if (!enabled) return;
        Result cached = load();
        if (cached != null) listener.onWeather(cached);
        if (busy || (cached != null && System.currentTimeMillis() - cached.at < STALE_MS)) return;
        busy = true;
        new Thread(() -> {
            Result r = null;
            try {
                r = fetch();
            } catch (Exception ignored) {
                // Offline or the service is down: keep showing the last reading.
            }
            final Result result = r;
            main.post(() -> {
                busy = false;
                if (result == null) return;
                save(result);
                listener.onWeather(result);
            });
        }, "weather").start();
    }

    // ---- background ------------------------------------------------------------------------------

    private Result fetch() throws Exception {
        double lat, lon;
        String place;
        if (!city.isEmpty()) {
            JSONObject hit = new JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?count=1&language=en&name="
                    + URLEncoder.encode(city, "UTF-8"))).getJSONArray("results").getJSONObject(0);
            lat = hit.getDouble("latitude");
            lon = hit.getDouble("longitude");
            place = hit.optString("name", city);
        } else {
            Location loc = lastLocation();
            if (loc == null) return null;
            // ~1 km is plenty for weather and keeps the exact spot on the TV.
            lat = Math.round(loc.getLatitude() * 100) / 100.0;
            lon = Math.round(loc.getLongitude() * 100) / 100.0;
            place = placeName(lat, lon);
        }
        String url = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f"
                        + "&current=temperature_2m,weather_code,is_day&timezone=auto%s",
                lat, lon, fahrenheit ? "&temperature_unit=fahrenheit" : "");
        JSONObject cur = new JSONObject(get(url)).getJSONObject("current");
        return new Result(Math.round((float) cur.getDouble("temperature_2m")), cur.getInt("weather_code"),
                cur.optInt("is_day", 1) == 1, place, System.currentTimeMillis());
    }

    private Location lastLocation() {
        LocationManager lm = ctx.getSystemService(LocationManager.class);
        if (lm == null) return null;
        Location best = null;
        for (String p : new String[]{"fused", LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER,
                LocationManager.GPS_PROVIDER}) {
            try {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            } catch (SecurityException | IllegalArgumentException ignored) {
            }
        }
        return best;
    }

    /** Area name for the rounded spot, looked up once and remembered. */
    private String placeName(double lat, double lon) {
        SharedPreferences p = prefs();
        String key = String.format(Locale.US, "place_%.2f_%.2f", lat, lon);
        String known = p.getString(key, null);
        if (known != null) return known;
        String name = "";
        try {
            if (Geocoder.isPresent()) {
                List<Address> list = new Geocoder(ctx, Locale.ENGLISH).getFromLocation(lat, lon, 1);
                if (list != null && !list.isEmpty()) {
                    Address a = list.get(0);
                    name = firstNonEmpty(a.getLocality(), a.getSubAdminArea(), a.getAdminArea());
                }
            }
        } catch (Exception ignored) {
        }
        if (!name.isEmpty()) p.edit().putString(key, name).apply();
        return name;
    }

    private static String firstNonEmpty(String... s) {
        for (String v : s) if (v != null && !v.trim().isEmpty()) return v.trim();
        return "";
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", "tvhome-launcher");
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    // ---- cache -----------------------------------------------------------------------------------

    private SharedPreferences prefs() {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private Result load() {
        SharedPreferences p = prefs();
        if (!p.contains("at")) return null;
        return new Result(p.getInt("temp", 0), p.getInt("code", 0), p.getBoolean("day", true),
                p.getString("place", ""), p.getLong("at", 0L));
    }

    private void save(Result r) {
        prefs().edit().putInt("temp", r.temp).putInt("code", r.code).putBoolean("day", r.day)
                .putString("place", r.place).putLong("at", r.at).apply();
    }
}
