package com.tms.agent.device;

import android.content.Context;

/**
 * Terminaux PAX (A920, A80, A77, IM30, A35…).
 *
 * Point d'intégration SDK : NeptuneLite / PAX DAL (jar fourni par PAX sous NDA).
 * Déposer le jar dans app/libs/pax/ puis surcharger ici :
 * <ul>
 *   <li>{@link #vendorSerial()} : IDAL.getSys().getTermInfo().get(ETermInfoKey.SN)</li>
 *   <li>{@link #installApk} / {@link #reboot()} : API système du DAL si l'agent n'est pas signé plateforme.</li>
 * </ul>
 * Sans SDK, le comportement Android standard est utilisé (fonctionnel si l'agent est signé
 * avec la clé PAX de l'environnement cible).
 */
public class PaxDeviceManager extends GenericDeviceManager {

    public PaxDeviceManager(Context context) {
        super(context);
    }

    @Override
    public String vendor() {
        return "PAX";
    }
}
