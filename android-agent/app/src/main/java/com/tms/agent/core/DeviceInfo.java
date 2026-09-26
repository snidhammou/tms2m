package com.tms.agent.core;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.TrafficStats;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;

import androidx.core.content.ContextCompat;
import androidx.core.content.pm.PackageInfoCompat;

import com.tms.agent.BuildConfig;
import com.tms.agent.admin.AgentDeviceAdmin;
import com.tms.agent.config.AgentConfig;
import com.tms.agent.device.DeviceManager;
import com.tms.agent.net.Dtos;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Collecte l'état du terminal envoyé à chaque heartbeat (supervision). */
public class DeviceInfo {

    private final Context context;
    private final DeviceManager device;
    private final AgentConfig config;

    public DeviceInfo(Context context, DeviceManager device, AgentConfig config) {
        this.context = context;
        this.device = device;
        this.config = config;
    }

    public Dtos.HeartbeatRequest heartbeat() {
        Dtos.HeartbeatRequest req = new Dtos.HeartbeatRequest();
        req.batteryLevel = batteryLevel();
        req.ipAddress = ipAddress();
        req.osVersion = Build.VERSION.RELEASE;
        req.firmwareVersion = device.firmwareVersion();
        req.agentVersion = BuildConfig.VERSION_NAME;
        req.installedApps = installedApps();

        StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
        req.storageTotalBytes = fs.getTotalBytes();
        req.storageFreeBytes = fs.getAvailableBytes();
        ActivityManager.MemoryInfo mem = memory();
        req.ramTotalBytes = mem.totalMem;
        req.ramAvailBytes = mem.availMem;
        req.networkType = networkType();
        long rx = TrafficStats.getTotalRxBytes();
        long tx = TrafficStats.getTotalTxBytes();
        req.rxBytes = rx == TrafficStats.UNSUPPORTED ? null : rx;
        req.txBytes = tx == TrafficStats.UNSUPPORTED ? null : tx;
        req.uptimeSeconds = SystemClock.elapsedRealtime() / 1000;
        req.deviceOwner = AgentDeviceAdmin.isDeviceOwner(context);
        req.autoRunPackage = config.getAutoRunPackage();
        req.kioskPackages = config.getKioskPackages();

        Location loc = lastLocation();
        if (loc != null) {
            req.latitude = loc.getLatitude();
            req.longitude = loc.getLongitude();
        }
        return req;
    }

    public Integer batteryLevel() {
        Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery == null) {
            return null;
        }
        int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        return level < 0 || scale <= 0 ? null : Math.round(level * 100f / scale);
    }

    public boolean isCharging() {
        Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int plugged = battery == null ? 0 : battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        return plugged != 0;
    }

    public ActivityManager.MemoryInfo memory() {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mem = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mem);
        return mem;
    }

    /** WIFI, MOBILE, ETHERNET ou NONE. */
    @SuppressWarnings("deprecation")
    public String networkType() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return null;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
            if (caps == null) return "NONE";
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "WIFI";
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "MOBILE";
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "ETHERNET";
            return "OTHER";
        }
        NetworkInfo info = cm.getActiveNetworkInfo();
        if (info == null || !info.isConnected()) return "NONE";
        switch (info.getType()) {
            case ConnectivityManager.TYPE_WIFI: return "WIFI";
            case ConnectivityManager.TYPE_MOBILE: return "MOBILE";
            case ConnectivityManager.TYPE_ETHERNET: return "ETHERNET";
            default: return "OTHER";
        }
    }

    /** Dernière position connue (réseau ou GPS), si la permission de localisation est accordée. */
    @SuppressLint("MissingPermission")
    private Location lastLocation() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) {
            return null;
        }
        Location best = null;
        for (String provider : lm.getProviders(true)) {
            try {
                Location l = lm.getLastKnownLocation(provider);
                if (l != null && (best == null || l.getTime() > best.getTime())) {
                    best = l;
                }
            } catch (SecurityException ignored) {
                // provider non autorisé
            }
        }
        return best;
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
    public List<Dtos.InstalledApp> installedApps() {
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
