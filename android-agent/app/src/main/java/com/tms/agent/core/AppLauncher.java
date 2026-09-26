package com.tms.agent.core;

import android.app.ActivityOptions;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.tms.agent.AgentApp;
import com.tms.agent.admin.AgentDeviceAdmin;
import com.tms.agent.config.AgentConfig;
import com.tms.agent.device.OpResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Démarrage automatique d'une application et mode kiosque (lock task).
 *
 * Le kiosque exige le statut Device Owner : l'agent déclare les applications autorisées
 * ({@link DevicePolicyManager#setLockTaskPackages}) puis lance la première en mode épinglé
 * (Android 9+). Le lancement d'activité depuis l'arrière-plan (boot) est autorisé au Device
 * Owner ; sans ce statut, Android 10+ peut bloquer le démarrage automatique.
 */
public final class AppLauncher {

    private static final String TAG = "TmsLauncher";

    private AppLauncher() {
    }

    /** Au démarrage du terminal : relance le kiosque s'il est actif, sinon l'application auto-run. */
    public static void onBoot(Context context) {
        AgentConfig config = AgentApp.get().config();
        List<String> kiosk = config.getKioskPackages();
        if (!kiosk.isEmpty() && AgentDeviceAdmin.isDeviceOwner(context)) {
            launch(context, kiosk.get(0), true);
        } else if (config.getAutoRunPackage() != null) {
            launch(context, config.getAutoRunPackage(), false);
        }
    }

    public static OpResult setAutoRun(Context context, String packageName) {
        AgentConfig config = AgentApp.get().config();
        if (packageName == null || packageName.isEmpty()) {
            config.setAutoRunPackage(null);
            return OpResult.ok("Démarrage auto désactivé");
        }
        config.setAutoRunPackage(packageName);
        boolean installed = context.getPackageManager().getLaunchIntentForPackage(packageName) != null;
        return OpResult.ok("Démarrage auto : " + packageName + (installed ? "" : " (pas encore installée)"));
    }

    public static OpResult setKiosk(Context context, List<String> packages) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return OpResult.fail("Mode kiosque non supporté avant Android 6");
        }
        if (!AgentDeviceAdmin.isDeviceOwner(context)) {
            return OpResult.fail("Mode kiosque impossible : TMS2M Agent n'est pas Device Owner");
        }
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        AgentConfig config = AgentApp.get().config();
        if (packages.isEmpty()) {
            dpm.setLockTaskPackages(AgentDeviceAdmin.component(context), new String[0]);
            config.setKioskPackages(packages);
            return OpResult.ok("Mode kiosque désactivé");
        }
        List<String> allowed = new ArrayList<>(packages);
        if (!allowed.contains(context.getPackageName())) {
            allowed.add(context.getPackageName()); // l'agent reste accessible pour la maintenance
        }
        dpm.setLockTaskPackages(AgentDeviceAdmin.component(context), allowed.toArray(new String[0]));
        config.setKioskPackages(packages);
        boolean launched = launch(context, packages.get(0), true);
        return OpResult.ok("Kiosque actif sur " + packages + (launched ? "" : " (application à lancer, non installée ?)"));
    }

    private static boolean launch(Context context, String packageName, boolean lockTask) {
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(packageName);
        if (intent == null) {
            Log.w(TAG, "Aucune activité de lancement pour " + packageName);
            return false;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        try {
            if (lockTask && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Bundle options = ActivityOptions.makeBasic().setLockTaskEnabled(true).toBundle();
                context.startActivity(intent, options);
            } else {
                context.startActivity(intent);
            }
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Lancement de " + packageName + " impossible", e);
            return false;
        }
    }
}
