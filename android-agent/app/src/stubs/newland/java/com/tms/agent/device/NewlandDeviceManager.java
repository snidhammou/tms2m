package com.tms.agent.device;

import android.content.Context;

/**
 * Terminaux Newland (N910, N950, N750, X800…).
 *
 * Point d'intégration SDK : NSDK / N-SDK Newland (fourni par Newland).
 * Déposer le jar/aar dans app/libs/newland/ puis surcharger ici :
 * <ul>
 *   <li>{@link #vendorSerial()} : lecture du SN via le module "device info" du SDK</li>
 *   <li>{@link #installApk} / {@link #reboot()} : module "system" du SDK (install/reboot silencieux)</li>
 * </ul>
 * Sans SDK, le comportement Android standard est utilisé (APK signé avec la clé Newland requis
 * pour l'installation sur terminal de production).
 */
public class NewlandDeviceManager extends GenericDeviceManager {

    public NewlandDeviceManager(Context context) {
        super(context);
    }

    @Override
    public String vendor() {
        return "NEWLAND";
    }
}
