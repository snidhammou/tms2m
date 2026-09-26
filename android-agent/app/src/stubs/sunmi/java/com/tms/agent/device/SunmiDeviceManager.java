package com.tms.agent.device;

import android.content.Context;

/**
 * Terminaux Sunmi (P2, P2 Pro, P3, V2s, T2…).
 *
 * Le numéro de série est exposé par la propriété système {@code ro.serialno}
 * (lue par l'implémentation générique).
 *
 * Point d'intégration SDK : Sunmi PayLib / services système Sunmi, pour l'installation
 * silencieuse et le reboot lorsque l'agent n'est pas signé avec la clé Sunmi.
 * Déposer le SDK dans app/libs/sunmi/ puis surcharger {@link #installApk} et {@link #reboot()}.
 */
public class SunmiDeviceManager extends GenericDeviceManager {

    public SunmiDeviceManager(Context context) {
        super(context);
    }

    @Override
    public String vendor() {
        return "SUNMI";
    }
}
