package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.os.SystemClock;
import android.widget.Toast;

/**
 * Sleep timer behind the Sleep pill: after 15 minutes to 2 hours the TV goes to standby, exactly as
 * if the remote's power button was pressed. A home-screen app may not switch the TV off itself, so
 * the last step goes through {@link SleepService}'s accessibility "lock screen" action, which on a
 * TV puts it to sleep. A minute before, a notice says it is coming. Switching the TV off by hand
 * cancels the timer, so it can never turn the TV off just after you turn it back on.
 */
final class SleepTimer {
    static final String ACTION_FIRE = "com.gathin.tvhome.SLEEP_FIRE";
    static final String ACTION_WARN = "com.gathin.tvhome.SLEEP_WARN";
    private static final String PREFS = "sleep";

    private SleepTimer() {
    }

    static void start(Context c, long delayMs) {
        cancel(c);
        AlarmManager am = c.getSystemService(AlarmManager.class);
        long now = SystemClock.elapsedRealtime();
        am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, now + delayMs, pending(c, ACTION_FIRE, true));
        if (delayMs > 90_000L) {
            am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, now + delayMs - 60_000L,
                    pending(c, ACTION_WARN, true));
        }
        prefs(c).edit().putLong("end_wall", System.currentTimeMillis() + delayMs).apply();
    }

    static void cancel(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        for (String action : new String[]{ACTION_FIRE, ACTION_WARN}) {
            PendingIntent pi = pending(c, action, false);
            if (pi != null) {
                am.cancel(pi);
                pi.cancel();
            }
        }
        prefs(c).edit().remove("end_wall").apply();
    }

    /** Minutes left, rounded up; 0 when no timer is running (also after a reboot, which drops alarms). */
    static int minutesLeft(Context c) {
        if (pending(c, ACTION_FIRE, false) == null) return 0;
        long left = prefs(c).getLong("end_wall", 0L) - System.currentTimeMillis();
        return left <= 0 ? 0 : (int) ((left + 59_999L) / 60_000L);
    }

    /** True when the standby step is available, i.e. SleepService is switched on. */
    static boolean ready() {
        return SleepService.instance != null;
    }

    /** Put the TV in standby now. Does nothing if it is already off. */
    static boolean sleepNow(Context c) {
        PowerManager pm = c.getSystemService(PowerManager.class);
        if (pm != null && !pm.isInteractive()) return true;
        SleepService s = SleepService.instance;
        return s != null && s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);
    }

    private static PendingIntent pending(Context c, String action, boolean create) {
        Intent i = new Intent(c, Receiver.class).setAction(action);
        int flags = PendingIntent.FLAG_IMMUTABLE | (create ? PendingIntent.FLAG_UPDATE_CURRENT : PendingIntent.FLAG_NO_CREATE);
        return PendingIntent.getBroadcast(c, action.hashCode(), i, flags);
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The timer's alarms. */
    public static final class Receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent intent) {
            if (ACTION_WARN.equals(intent.getAction())) {
                PowerManager pm = c.getSystemService(PowerManager.class);
                if (pm == null || pm.isInteractive()) {
                    Toast.makeText(c, R.string.sleep_warning, Toast.LENGTH_LONG).show();
                }
            } else if (ACTION_FIRE.equals(intent.getAction())) {
                cancel(c);
                sleepNow(c);
            }
        }
    }

    /**
     * Test hook for adb only (protected by the DUMP permission, which only the shell and the system
     * hold): am broadcast -a com.gathin.tvhome.SLEEP_IN --ei seconds 20 -n com.gathin.tvhome/.SleepTimer\$TestReceiver
     */
    public static final class TestReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent intent) {
            int seconds = intent.getIntExtra("seconds", 0);
            if (seconds > 0) start(c, seconds * 1000L);
            else cancel(c);
        }
    }
}
