package com.tms.agent.core;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.BatteryManager;
import android.os.Build;

import androidx.core.content.pm.PackageInfoCompat;

import com.tms.agent.BuildConfig;
import com.tms.agent.device.DeviceManager;
import com.tms.agent.net.Dtos;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Collecte l'état du terminal envoyé à chaque heartbeat. */
public class DeviceInfo {

    private final Context context;
    private final DeviceManager device;

    public DeviceInfo(Context context, DeviceManager device) {
        this.context = context;
        this.device = device;
    }

    public Dtos.HeartbeatRequest heartbeat() {
        Dtos.HeartbeatRequest req = new Dtos.HeartbeatRequest();
        req.batteryLevel = batteryLevel();
        req.ipAddress = ipAddress();
        req.osVersion = Build.VERSION.RELEASE;
        req.firmwareVersion = device.firmwareVersion();
        req.agentVersion = BuildConfig.VERSION_NAME;
        req.installedApps = installedApps();
        return req;
    }

    private Integer batteryLevel() {
        Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery == null) {
            return null;
        }
        int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        return level < 0 || scale <= 0 ? null : Math.round(level * 100f / scale);
    }

    private static String ipAddress() {
        try {
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nif.isUp() || nif.isLoopback()) {
                    continue;
                }
                for (InetAddress addr : Collections.list(nif.getInetAddresses())) {
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
            // IP non déterminable : le serveur utilisera l'adresse source
        }
        return null;
    }

    /** Applications installées hors système (+ applications système mises à jour). */
    private List<Dtos.InstalledApp> installedApps() {
        List<Dtos.InstalledApp> apps = new ArrayList<>();
        for (PackageInfo p : context.getPackageManager().getInstalledPackages(0)) {
            ApplicationInfo ai = p.applicationInfo;
            boolean system = ai != null && (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                    && (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
            if (!system) {
                apps.add(new Dtos.InstalledApp(p.packageName, p.versionName, PackageInfoCompat.getLongVersionCode(p)));
            }
        }
        return apps;
    }
}
