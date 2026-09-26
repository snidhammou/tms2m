package com.tms.agent.admin;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * Administrateur d'appareil de TMS2M Agent.
 *
 * Une fois l'agent défini comme Device Owner (une seule fois par terminal) :
 * <pre>adb shell dpm set-device-owner com.tms.agent/.admin.AgentDeviceAdmin</pre>
 * Android lui ouvre le redémarrage ({@link DevicePolicyManager#reboot}) et l'installation /
 * désinstallation silencieuses, sans signature constructeur. Fonctionne sur tout terminal
 * Android 7+ sans compte configuré, quelle que soit la marque.
 */
public class AgentDeviceAdmin extends DeviceAdminReceiver {

    private static final String TAG = "TmsAdmin";

    public static ComponentName component(Context context) {
        return new ComponentName(context.getApplicationContext(), AgentDeviceAdmin.class);
    }

    /** Vrai si TMS2M Agent est le propriétaire (Device Owner) de ce terminal. */
    public static boolean isDeviceOwner(Context context) {
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        return dpm != null && dpm.isDeviceOwnerApp(context.getPackageName());
    }

    /** Le redémarrage via DevicePolicyManager exige Android 7.0 (API 24). */
    public static boolean canRebootAsOwner(Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isDeviceOwner(context);
    }

    /**
     * Retire le statut Device Owner (utile en développement ; en production, c'est la
     * réinitialisation usine qui le retire).
     */
    @SuppressWarnings("deprecation")
    public static void clearDeviceOwner(Context context) {
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm != null && isDeviceOwner(context)) {
            dpm.clearDeviceOwnerApp(context.getPackageName());
            Log.i(TAG, "Statut Device Owner retiré");
        }
    }

    /**
     * En Device Owner, l'agent s'accorde les permissions utiles à la supervision
     * (position du terminal, lecture de fichiers pour l'extraction) sans intervention à l'écran.
     */
    public static void grantSelfPermissions(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || !isDeviceOwner(context)) {
            return;
        }
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        String[] perms = {
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
        };
        for (String p : perms) {
            try {
                dpm.setPermissionGrantState(component(context), context.getPackageName(), p,
                        DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
            } catch (Exception e) {
                Log.w(TAG, "Permission " + p + " non accordée : " + e.getMessage());
            }
        }
    }

    @Override
    public void onEnabled(Context context, Intent intent) {
        Log.i(TAG, "Administrateur activé (Device Owner : " + isDeviceOwner(context) + ")");
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        Log.i(TAG, "Administrateur désactivé");
    }
}
