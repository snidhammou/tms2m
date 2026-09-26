package com.tms.agent.device;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import com.tms.agent.admin.AgentDeviceAdmin;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Implémentation Android standard (PackageInstaller / PowerManager).
 *
 * <ul>
 *   <li>Agent signé plateforme ou app système : installation, désinstallation et reboot silencieux.</li>
 *   <li>Agent non privilégié : Android demande une confirmation à l'écran ; reboot impossible.</li>
 * </ul>
 */
public class GenericDeviceManager implements DeviceManager {

    private static final long INSTALL_TIMEOUT_MIN = 10;

    protected final Context context;

    public GenericDeviceManager(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public String vendor() {
        return Build.MANUFACTURER == null ? "OTHER" : Build.MANUFACTURER.toUpperCase(Locale.ROOT);
    }

    @Override
    public String serialNumber() {
        String sn = vendorSerial();
        if (sn == null) {
            sn = SystemProps.first("ro.serialno", "ro.boot.serialno");
        }
        if (sn == null) {
            sn = buildSerial();
        }
        if (sn == null) {
            // Dernier recours : identifiant stable par appareil + signature d'app
            @SuppressLint("HardwareIds")
            String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            sn = "AID-" + androidId;
        }
        return sn.trim();
    }

    /** Point d'extension : numéro de série via le SDK constructeur. */
    protected String vendorSerial() {
        return null;
    }

    @SuppressLint({"HardwareIds", "MissingPermission"})
    private static String buildSerial() {
        String sn = null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                sn = Build.getSerial(); // nécessite READ_PRIVILEGED_PHONE_STATE (app système) sur Android 10+
            } else {
                sn = Build.SERIAL;
            }
        } catch (SecurityException ignored) {
            // pas le droit : on passe au fallback suivant
        }
        return sn == null || Build.UNKNOWN.equalsIgnoreCase(sn) ? null : sn;
    }

    @Override
    public String model() {
        return Build.MODEL;
    }

    @Override
    public String firmwareVersion() {
        return Build.DISPLAY;
    }

    @Override
    public OpResult installApk(File apk, String packageName) {
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(packageName);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Mise à jour silencieuse possible si l'agent est l'installeur d'origine (Android 12+)
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
        }
        InstallResults.Pending pending = InstallResults.register();
        try {
            int sessionId = installer.createSession(params);
            try (PackageInstaller.Session session = installer.openSession(sessionId)) {
                try (InputStream in = new FileInputStream(apk);
                     OutputStream out = session.openWrite("base.apk", 0, apk.length())) {
                    byte[] buffer = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buffer)) > 0) {
                        out.write(buffer, 0, n);
                    }
                    session.fsync(out);
                }
                session.commit(InstallResults.sender(context, pending));
            }
            return pending.await(INSTALL_TIMEOUT_MIN, TimeUnit.MINUTES);
        } catch (Exception e) {
            pending.cancel();
            return OpResult.fail("Installation impossible : " + e.getMessage());
        }
    }

    @Override
    public OpResult uninstall(String packageName) {
        if (!isInstalled(packageName)) {
            return OpResult.ok("Déjà absent");
        }
        InstallResults.Pending pending = InstallResults.register();
        try {
            context.getPackageManager().getPackageInstaller()
                    .uninstall(packageName, InstallResults.sender(context, pending));
            return pending.await(INSTALL_TIMEOUT_MIN, TimeUnit.MINUTES);
        } catch (Exception e) {
            pending.cancel();
            return OpResult.fail("Désinstallation impossible : " + e.getMessage());
        }
    }

    @Override
    public boolean canReboot() {
        return AgentDeviceAdmin.canRebootAsOwner(context)
                || context.checkCallingOrSelfPermission(Manifest.permission.REBOOT) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public OpResult reboot() {
        // 1. Device Owner : API officielle, sans signature constructeur (Android 7+)
        if (AgentDeviceAdmin.canRebootAsOwner(context)) {
            try {
                DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
                dpm.reboot(AgentDeviceAdmin.component(context));
                return OpResult.ok("Redémarrage (Device Owner)");
            } catch (RuntimeException e) {
                // IllegalStateException : appel téléphonique en cours
                return OpResult.fail("Redémarrage Device Owner refusé : " + e.getMessage());
            }
        }
        // 2. Agent signé avec la clé plateforme du constructeur
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            pm.reboot(null);
            return OpResult.ok("Redémarrage");
        } catch (SecurityException e) {
            return OpResult.fail("Permission REBOOT absente (agent ni Device Owner, ni signé plateforme)");
        }
    }

    protected boolean isInstalled(String packageName) {
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
