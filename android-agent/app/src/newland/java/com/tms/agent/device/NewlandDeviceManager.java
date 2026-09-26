package com.tms.agent.device;

import android.content.Context;
import android.util.Log;

import com.newland.sdk.ModuleManage;
import com.newland.sdk.module.devicebasic.DeviceBasicModule;
import com.newland.sdk.module.devicebasic.DeviceInfo;

/**
 * Terminaux Newland (N910, N950, N950S, X800…) : implémentation s'appuyant sur le MESDK.
 *
 * <ul>
 *   <li>N° de série et firmware : {@code DeviceBasicModule.getDeviceInfo()}.</li>
 *   <li>Reboot : le MESDK n'expose pas de redémarrage d'Android. {@code SysN.NDK_SysReboot()}
 *       redémarre le processeur sécurisé K21 (log "executed from K21"), pas Android, et peut
 *       interrompre une transaction : il n'est volontairement PAS utilisé. Le reboot Android
 *       passe par l'implémentation générique : agent Device Owner, ou signé avec la clé système Newland.</li>
 *   <li>Installation / désinstallation : pas d'API d'installation d'APK dans le MESDK ;
 *       PackageInstaller est utilisé (silencieux si l'agent est signé avec la clé système Newland).</li>
 * </ul>
 * Toute erreur SDK (librairie native absente, permission MANAGE_NEWLAND refusée…) fait
 * retomber sur le comportement Android standard.
 */
public class NewlandDeviceManager extends GenericDeviceManager {

    private static final String TAG = "TmsNewland";

    private volatile boolean sdkInitDone;
    private volatile boolean sdkReady;
    private String cachedSerial;
    private String cachedFirmware;

    public NewlandDeviceManager(Context context) {
        super(context);
    }

    @Override
    public String vendor() {
        return "NEWLAND";
    }

    /** Initialisation paresseuse du MESDK (une seule fois par process). */
    private synchronized boolean sdk() {
        if (!sdkInitDone) {
            sdkInitDone = true;
            try {
                sdkReady = ModuleManage.getInstance().init(context);
                Log.i(TAG, "MESDK initialisé : " + sdkReady);
            } catch (Throwable t) {
                Log.w(TAG, "MESDK indisponible, API Android standard utilisées", t);
                sdkReady = false;
            }
        }
        return sdkReady;
    }

    private DeviceInfo deviceInfo() {
        if (!sdk()) {
            return null;
        }
        try {
            DeviceBasicModule module = ModuleManage.getInstance().getDeviceBasicModule();
            return module != null ? module.getDeviceInfo() : null;
        } catch (Throwable t) {
            Log.w(TAG, "Lecture DeviceInfo impossible", t);
            return null;
        }
    }

    @Override
    protected String vendorSerial() {
        if (cachedSerial == null) {
            DeviceInfo info = deviceInfo();
            String sn = info != null ? info.getSN() : null;
            if (sn != null && !sn.trim().isEmpty()) {
                cachedSerial = sn.trim();
            }
        }
        return cachedSerial;
    }

    @Override
    public String firmwareVersion() {
        if (cachedFirmware == null) {
            DeviceInfo info = deviceInfo();
            String fw = info != null ? info.getFirmwareVer() : null;
            cachedFirmware = fw != null && !fw.trim().isEmpty() ? fw.trim() : super.firmwareVersion();
        }
        return cachedFirmware;
    }

    // canReboot() / reboot() : implémentation générique (Device Owner, ou permission REBOOT si signé système Newland).
}
