package com.tms.agent.core;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.util.Log;

import androidx.core.content.pm.PackageInfoCompat;

import com.tms.agent.AgentApp;
import com.tms.agent.config.AgentConfig;
import com.tms.agent.device.DeviceManager;
import com.tms.agent.device.OpResult;
import com.tms.agent.net.ApiClient;
import com.tms.agent.net.Dtos;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Locale;

import okhttp3.ResponseBody;
import retrofit2.Response;

/** Exécute les tâches TMS reçues et remonte leur statut au serveur. */
public class TaskExecutor {

    /** Broadcast envoyé à l'application concernée après mise à jour de ses paramètres. */
    public static final String ACTION_PARAMETERS_UPDATED = "com.tms.agent.action.PARAMETERS_UPDATED";

    private static final String TAG = "TmsTask";

    private final Context context;
    private final AgentConfig config;
    private final DeviceManager device;
    private final ApiClient client;
    private final StatusOutbox outbox;
    private final ParameterStore parameters;

    public TaskExecutor(AgentApp app, ApiClient client, StatusOutbox outbox, ParameterStore parameters) {
        this.context = app;
        this.config = app.config();
        this.device = app.device();
        this.client = client;
        this.outbox = outbox;
        this.parameters = parameters;
    }

    public void execute(Dtos.DeviceTask task) {
        Log.i(TAG, "Tâche #" + task.id + " " + task.type);
        report(task.id, "IN_PROGRESS", null);
        OpResult result;
        try {
            switch (task.type) {
                case "INSTALL_APP":
                    result = install(task);
                    break;
                case "UNINSTALL_APP":
                    result = device.uninstall(required(task, "packageName"));
                    break;
                case "PUSH_PARAMS":
                    result = pushParameters(required(task, "packageName"));
                    break;
                case "REBOOT":
                    reboot(task);
                    return;
                default:
                    result = OpResult.fail("Type de tâche non supporté : " + task.type);
            }
        } catch (Exception e) {
            Log.e(TAG, "Tâche #" + task.id + " en échec", e);
            result = OpResult.fail(e.getClass().getSimpleName() + " : " + e.getMessage());
        }
        report(task.id, result.success ? "SUCCESS" : "FAILED", result.message);
    }

    /** Remonte un statut ; en cas d'échec réseau il est conservé dans l'outbox. */
    void report(long taskId, String status, String message) {
        outbox.add(taskId, status, message);
        try {
            outbox.flush(client.api());
        } catch (Exception e) {
            Log.w(TAG, "Statut #" + taskId + " conservé pour renvoi : " + e.getMessage());
        }
    }

    // ------------------------------------------------------------ INSTALL_APP

    private OpResult install(Dtos.DeviceTask task) throws IOException {
        String pkg = required(task, "packageName");
        long versionCode = task.payloadLong("versionCode", -1);
        long installed = installedVersionCode(pkg);
        if (versionCode > 0 && installed >= versionCode) {
            return OpResult.ok("Déjà installé (versionCode " + installed + ")");
        }

        File apk = download(required(task, "downloadUrl"), task.payloadString("sha256"), pkg, versionCode);
        boolean selfUpdate = pkg.equals(context.getPackageName());
        try {
            PackageInfo archive = context.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), 0);
            if (archive == null) {
                return OpResult.fail("APK illisible par Android (fichier invalide ou incompatible)");
            }
            if (!pkg.equals(archive.packageName)) {
                return OpResult.fail("L'APK contient " + archive.packageName + " alors que le TMS annonce " + pkg
                        + " : republiez l'APK sur le serveur");
            }
            if (selfUpdate) {
                // Le process sera tué par l'installation : clôture au redémarrage (SyncEngine)
                config.setPendingSelfUpdate(task.id, versionCode);
            }
            OpResult r = device.installApk(apk, pkg);
            if (selfUpdate) {
                config.clearPendingSelfUpdate();
            }
            if (r.success && versionCode > 0) {
                // Ne pas se fier au seul code retour du SDK : vérifier la version réellement installée
                long now = installedVersionCode(pkg);
                for (int i = 0; i < 15 && now < versionCode; i++) {
                    SystemClock.sleep(1000); // certains SDK installent de façon asynchrone
                    now = installedVersionCode(pkg);
                }
                if (now < versionCode) {
                    return OpResult.fail("Installation annoncée réussie (" + r.message + ") mais versionCode installé = "
                            + now + " au lieu de " + versionCode);
                }
            }
            return r;
        } finally {
            //noinspection ResultOfMethodCallIgnored
            apk.delete();
        }
    }

    private File download(String url, String expectedSha256, String pkg, long versionCode) throws IOException {
        File dir = new File(context.getCacheDir(), "apks");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Création de " + dir + " impossible");
        }
        File[] leftovers = dir.listFiles();
        if (leftovers != null) {
            for (File f : leftovers) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
        File target = new File(dir, pkg + "-" + versionCode + ".apk");

        Response<ResponseBody> resp = client.api().download(client.absolute(url)).execute();
        if (!resp.isSuccessful() || resp.body() == null) {
            throw new IOException("Téléchargement HTTP " + resp.code());
        }
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IOException(e);
        }
        try (ResponseBody body = resp.body();
             InputStream in = body.byteStream();
             OutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) {
                md.update(buffer, 0, n);
                out.write(buffer, 0, n);
            }
        }
        String actual = toHex(md.digest());
        if (expectedSha256 != null && !expectedSha256.equalsIgnoreCase(actual)) {
            //noinspection ResultOfMethodCallIgnored
            target.delete();
            throw new IOException("Empreinte SHA-256 invalide (fichier corrompu ou altéré)");
        }
        return target;
    }

    private long installedVersionCode(String pkg) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(pkg, 0);
            return PackageInfoCompat.getLongVersionCode(info);
        } catch (PackageManager.NameNotFoundException e) {
            return -1;
        }
    }

    // ------------------------------------------------------------ PUSH_PARAMS

    private OpResult pushParameters(String pkg) throws IOException {
        Response<Dtos.ParametersResponse> resp = client.api().parameters(pkg).execute();
        if (!resp.isSuccessful() || resp.body() == null) {
            return OpResult.fail("Récupération des paramètres HTTP " + resp.code());
        }
        String json = parameters.save(pkg, resp.body().parameters);
        // Broadcast ciblé : seule l'application concernée le reçoit
        // FLAG_INCLUDE_STOPPED_PACKAGES : une app installée par le TMS mais jamais lancée doit être notifiée
        Intent intent = new Intent(ACTION_PARAMETERS_UPDATED)
                .setPackage(pkg)
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                .putExtra("packageName", pkg)
                .putExtra("parameters", json);
        context.sendBroadcast(intent);
        int count = resp.body().parameters == null ? 0 : resp.body().parameters.size();
        return OpResult.ok(count + " paramètre(s) appliqué(s)");
    }

    // ------------------------------------------------------------ REBOOT

    /** Délai au-delà duquel on considère que la commande de reboot a été ignorée. */
    private static final long REBOOT_GRACE_MS = 90_000;

    /** Heure (epoch ms) du dernier démarrage du terminal. */
    static long bootTimeMillis() {
        return System.currentTimeMillis() - SystemClock.elapsedRealtime();
    }

    private void reboot(Dtos.DeviceTask task) {
        if (!device.canReboot()) {
            report(task.id, "FAILED", "Reboot non autorisé : agent ni Device Owner, ni signé avec la clé plateforme "
                    + device.vendor());
            return;
        }
        // Le succès sera confirmé au redémarrage de l'agent (SyncEngine) si l'heure de boot a changé :
        // certains firmwares acceptent la commande sans l'exécuter, on ne se fie pas au code retour.
        config.setPendingReboot(task.id, bootTimeMillis());
        OpResult r = device.reboot();
        if (!r.success) {
            config.clearPendingReboot();
            report(task.id, "FAILED", r.message);
            return;
        }
        SystemClock.sleep(REBOOT_GRACE_MS);
        // Toujours en vie : le système a ignoré la commande
        config.clearPendingReboot();
        report(task.id, "FAILED", "Commande de reboot acceptée mais ignorée par le système "
                + device.vendor() + " (" + r.message + ") : signature constructeur requise ?");
    }

    // ------------------------------------------------------------ utils

    private static String required(Dtos.DeviceTask task, String key) {
        String v = task.payloadString(key);
        if (v == null || v.isEmpty()) {
            throw new IllegalArgumentException("Payload incomplet : " + key + " manquant");
        }
        return v;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.ROOT, "%02x", b));
        }
        return sb.toString();
    }
}
