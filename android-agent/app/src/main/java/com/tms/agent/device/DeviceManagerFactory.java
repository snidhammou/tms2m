package com.tms.agent.device;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.util.Locale;

/** Détecte le constructeur au runtime : un seul APK pour tout le parc multi-marques. */
public final class DeviceManagerFactory {

    private DeviceManagerFactory() {
    }

    public static DeviceManager create(Context context) {
        String id = (Build.MANUFACTURER + " " + Build.BRAND).toUpperCase(Locale.ROOT);
        DeviceManager dm;
        if (id.contains("PAX")) {
            dm = new PaxDeviceManager(context);
        } else if (id.contains("NEWLAND")) {
            dm = new NewlandDeviceManager(context);
        } else if (id.contains("SUNMI")) {
            dm = new SunmiDeviceManager(context);
        } else {
            dm = new GenericDeviceManager(context);
        }
        Log.i("TmsAgent", "Constructeur détecté : " + id.trim() + " -> " + dm.getClass().getSimpleName());
        return dm;
    }
}
