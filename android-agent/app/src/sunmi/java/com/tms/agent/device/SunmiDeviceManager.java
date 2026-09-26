package com.tms.agent.device;

import android.content.Context;
import android.os.Looper;
import android.util.Log;

import com.sunmi.pay.hardware.aidl.AidlConstants;
import com.sunmi.pay.hardware.aidlv2.system.BasicOptV2;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import sunmi.paylib.SunmiPayKernel;

/**
 * Terminaux Sunmi (P2, P2 Pro, P3, V2s…) : implémentation s'appuyant sur PayLib
 * (service système {@code com.sunmi.pay.hardware_v3}).
 *
 * <ul>
 *   <li>Reboot : {@code BasicOptV2.sysPowerManage(SYS_REBOOT)}.</li>
 *   <li>N° de série, modèle, firmware : {@code BasicOptV2.getSysParam(...)}.</li>
 *   <li>Installation / désinstallation : PayLib n'expose pas d'API d'installation d'APK ;
 *       PackageInstaller est utilisé (silencieux si l'agent est signé avec la clé système Sunmi).</li>
 * </ul>
 * La liaison au service est asynchrone (callback sur le thread principal) : on ne l'attend
 * jamais depuis le thread principal, seulement depuis le thread de travail de l'agent.
 */
public class SunmiDeviceManager extends GenericDeviceManager {

    private static final String TAG = "TmsSunmi";
    private static final long CONNECT_TIMEOUT_S = 8;

    private final CountDownLatch connected = new CountDownLatch(1);
    private volatile boolean bindRequested;
    private volatile BasicOptV2 basic;

    public SunmiDeviceManager(Context context) {
        super(context);
    }

    @Override
    public String vendor() {
        return "SUNMI";
    }

    private synchronized void bind() {
        if (bindRequested) {
            return;
        }
        bindRequested = true;
        try {
            boolean ok = SunmiPayKernel.getInstance().initPaySDK(context, new SunmiPayKernel.ConnectCallback() {
                @Override
                public void onConnectPaySDK() {
                    basic = SunmiPayKernel.getInstance().mBasicOptV2;
                    Log.i(TAG, "PayLib connecté (BasicOptV2 : " + (basic != null) + ")");
                    connected.countDown();
                }

                @Override
                public void onDisconnectPaySDK() {
                    Log.w(TAG, "PayLib déconnecté");
                    basic = null;
                }
            });
            if (!ok) {
                Log.w(TAG, "Service com.sunmi.pay.hardware_v3 introuvable, API Android standard utilisées");
                connected.countDown();
            }
        } catch (Throwable t) {
            Log.w(TAG, "PayLib indisponible, API Android standard utilisées", t);
            connected.countDown();
        }
    }

    /** Service système Sunmi, ou null s'il n'est pas (encore) disponible. */
    private BasicOptV2 basic() {
        bind();
        if (basic == null && Looper.myLooper() != Looper.getMainLooper()) {
            try {
                connected.await(CONNECT_TIMEOUT_S, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return basic;
    }

    private String sysParam(String key) {
        BasicOptV2 b = basic();
        if (b == null) {
            return null;
        }
        try {
            String v = b.getSysParam(key);
            return v != null && !v.trim().isEmpty() ? v.trim() : null;
        } catch (Throwable t) {
            Log.w(TAG, "getSysParam(" + key + ") en échec", t);
            return null;
        }
    }

    @Override
    protected String vendorSerial() {
        return sysParam(AidlConstants.SysParam.SN);
    }

    @Override
    public String model() {
        String m = sysParam(AidlConstants.SysParam.DEVICE_MODEL);
        return m != null ? m : super.model();
    }

    @Override
    public String firmwareVersion() {
        // Build.DISPLAY (ex. "P3_user_3.0.20_100_20250318") est plus parlant que le firmware du SP
        return super.firmwareVersion();
    }

    @Override
    public boolean canReboot() {
        return basic() != null || super.canReboot();
    }

    @Override
    public String sdkStatus() {
        return "PayLib Sunmi : " + (basic() != null ? "connecté" : "indisponible");
    }

    @Override
    public OpResult reboot() {
        BasicOptV2 b = basic();
        if (b != null) {
            try {
                int ret = b.sysPowerManage(AidlConstants.PowerManage.SYS_REBOOT);
                if (ret == 0) {
                    return OpResult.ok("Redémarrage (PayLib)");
                }
                Log.w(TAG, "sysPowerManage a retourné " + ret + ", tentative Android standard");
            } catch (Throwable t) {
                Log.w(TAG, "sysPowerManage en échec, tentative Android standard", t);
            }
        }
        return super.reboot();
    }
}
