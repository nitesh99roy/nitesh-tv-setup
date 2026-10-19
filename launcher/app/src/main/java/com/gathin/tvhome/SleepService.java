package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.accessibility.AccessibilityEvent;

/**
 * Lets the Sleep timer put the TV in standby: an accessibility service may perform the system's
 * "lock screen" action, which on a TV is the same as pressing the power button. It reads nothing on
 * screen and handles no keys. It also cancels a running timer when the TV is switched off by hand.
 * Opt-in: SLEEP_SERVICE=1 build-and-install.sh (or -SleepService on Windows) enables it.
 */
public class SleepService extends AccessibilityService {
    static volatile SleepService instance;

    private final BroadcastReceiver screenOff = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            SleepTimer.cancel(c);
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        registerReceiver(screenOff, new IntentFilter(Intent.ACTION_SCREEN_OFF));
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        try {
            unregisterReceiver(screenOff);
        } catch (IllegalArgumentException ignored) {
        }
        return super.onUnbind(intent);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
