package com.tms.agent.device;

import android.content.Context;
import android.util.Log;

import com.pax.dal.IDAL;
import com.pax.dal.ISys;
import com.pax.dal.entity.ETermInfoKey;
import com.pax.neptunelite.api.NeptuneLiteUser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

/**
 * Terminaux PAX (A920, A80, A77, A35, IM30…) : implémentation s'appuyant sur NeptuneLite (DAL).
 *
 * <ul>
 *   <li>Installation / désinstallation silencieuses : {@code ISys.installApp(path)} / {@code uninstallApp(pkg)}.</li>
 *   <li>Reboot : {@code ISys.reboot()}.</li>
 *   <li>N° de série, modèle : {@code ISys.getTermInfo()}.</li>
 * </ul>
 * Code retour 0 = succès ; le résultat est de plus vérifié via PackageManager. En cas d'erreur
 * du DAL, on retombe sur le comportement Android standard.
 */
public class PaxDeviceManager extends GenericDeviceManager {

    private static final String TAG = "TmsPax";

    private volatile boolean dalInitDone;
    private volatile IDAL dal;
    private Map<ETermInfoKey, String> termInfo;

    public PaxDeviceManager(Context context) {
        super(context);
    }

    @Override
    public String vendor() {
        return "PAX";
    }

    /** Initialisation paresseuse du DAL (une seule fois par process). */
    private synchronized ISys sys() {
        if (!dalInitDone) {
            dalInitDone = true;
            try {
                dal = NeptuneLiteUser.getInstance().getDal(context);
                Log.i(TAG, "NeptuneLite DAL initialisé : " + (dal != null));
            } catch (Throwable t) {
                Log.w(TAG, "NeptuneLite indisponible, API Android standard utilisées", t);
                dal = null;
            }
        }
        try {
            return dal != null ? dal.getSys() : null;
        } catch (Throwable t) {
            Log.w(TAG, "ISys indisponible", t);
            return null;
        }
    }

    private synchronized String info(ETermInfoKey key) {
        if (termInfo == null) {
            ISys sys = sys();
            if (sys == null) {
                return null;
            }
            try {
                termInfo = sys.getTermInfo();
            } catch (Throwable t) {
                Log.w(TAG, "getTermInfo en échec", t);
                return null;
            }
        }
        String v = termInfo != null ? termInfo.get(key) : null;
        return v != null && !v.trim().isEmpty() ? v.trim() : null;
    }

    @Override
    protected String vendorSerial() {
        return info(ETermInfoKey.SN);
    }

    @Override
    public String model() {
        String m = info(ETermInfoKey.MODEL);
        return m != null ? m : super.model();
    }

    // firmwareVersion() : Build.DISPLAY (implémentation générique) donne la version PayDroid complète,
    // ex. "A930_PayDroid_7.1.1_Virgo_V04.5.15_20240802" ; MON_VER du DAL ne vaut que "01 00" sur A930.

    @Override
    public OpResult installApk(File apk, String packageName) {
        ISys sys = sys();
        if (sys != null) {
            File readable = null;
            try {
                // Le service système PAX lit le fichier : il ne doit pas rester dans le cache privé
                readable = exportForSystem(apk);
                int ret = sys.installApp(readable.getAbsolutePath());
                if (ret == 0) {
                    return OpResult.ok("Installé (NeptuneLite)");
                }
                Log.w(TAG, "installApp a retourné " + ret + ", tentative Android standard");
            } catch (Throwable t) {
                Log.w(TAG, "installApp en échec, tentative Android standard", t);
            } finally {
                if (readable != null && !readable.equals(apk)) {
                    //noinspection ResultOfMethodCallIgnored
                    readable.delete();
                }
            }
        }
        return super.installApk(apk, packageName);
    }

    @Override
    public OpResult uninstall(String packageName) {
        if (!isInstalled(packageName)) {
            return OpResult.ok("Déjà absent");
        }
        ISys sys = sys();
        if (sys != null) {
            try {
                int ret = sys.uninstallApp(packageName);
                if (ret == 0 && !isInstalled(packageName)) {
                    return OpResult.ok("Désinstallé (NeptuneLite)");
                }
                Log.w(TAG, "uninstallApp a retourné " + ret + ", tentative Android standard");
            } catch (Throwable t) {
                Log.w(TAG, "uninstallApp en échec, tentative Android standard", t);
            }
        }
        return super.uninstall(packageName);
    }

    @Override
    public boolean canReboot() {
        return sys() != null || super.canReboot();
    }

    @Override
    public String sdkStatus() {
        return "NeptuneLite PAX : " + (sys() != null ? "DAL disponible" : "indisponible");
    }

    @Override
    public OpResult reboot() {
        ISys sys = sys();
        if (sys != null) {
            try {
                sys.reboot();
                return OpResult.ok("Redémarrage (NeptuneLite)");
            } catch (Throwable t) {
                Log.w(TAG, "reboot NeptuneLite en échec, tentative Android standard", t);
            }
        }
        return super.reboot();
    }

    /** Copie l'APK dans le stockage externe de l'app, lisible par le service d'installation PAX. */
    private File exportForSystem(File apk) throws Exception {
        File dir = context.getExternalFilesDir("tms-install");
        if (dir == null || (!dir.exists() && !dir.mkdirs())) {
            return apk;
        }
        File target = new File(dir, apk.getName());
        try (InputStream in = new FileInputStream(apk); OutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        target.setReadable(true, false);
        return target;
    }
}
