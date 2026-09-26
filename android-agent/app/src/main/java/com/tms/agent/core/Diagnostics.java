package com.tms.agent.core;

import android.app.ActivityManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;

import com.tms.agent.AgentApp;
import com.tms.agent.BuildConfig;
import com.tms.agent.admin.AgentDeviceAdmin;
import com.tms.agent.config.AgentConfig;
import com.tms.agent.device.DeviceManager;
import com.tms.agent.device.OpResult;
import com.tms.agent.net.ApiClient;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Response;

/** Assistance à distance : diagnostic, extraction de logs et de fichiers. */
public class Diagnostics {

    private static final long MAX_UPLOAD_BYTES = 20L * 1024 * 1024;

    private final AgentApp app;
    private final DeviceInfo info;
    private final ApiClient client;

    public Diagnostics(AgentApp app, DeviceInfo info, ApiClient client) {
        this.app = app;
        this.info = info;
        this.client = client;
    }

    /** Rapport d'état du terminal (affiché tel quel dans la console). */
    public Map<String, Object> run() {
        DeviceManager device = app.device();
        AgentConfig config = app.config();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("agentVersion", BuildConfig.VERSION_NAME);
        r.put("vendor", device.vendor());
        r.put("model", device.model());
        r.put("serialNumber", device.serialNumber());
        r.put("android", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        r.put("firmware", device.firmwareVersion());
        r.put("vendorSdk", device.sdkStatus());
        r.put("deviceOwner", AgentDeviceAdmin.isDeviceOwner(app));
        r.put("canReboot", device.canReboot());

        r.put("batteryLevel", info.batteryLevel());
        r.put("charging", info.isCharging());
        StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
        r.put("storageTotalMb", fs.getTotalBytes() / (1024 * 1024));
        r.put("storageFreeMb", fs.getAvailableBytes() / (1024 * 1024));
        r.put("storageFreePercent", fs.getTotalBytes() > 0 ? Math.round(100.0 * fs.getAvailableBytes() / fs.getTotalBytes()) : null);
        ActivityManager.MemoryInfo mem = info.memory();
        r.put("ramTotalMb", mem.totalMem / (1024 * 1024));
        r.put("ramAvailMb", mem.availMem / (1024 * 1024));
        r.put("lowMemory", mem.lowMemory);
        r.put("networkType", info.networkType());
        r.put("uptimeMinutes", SystemClock.elapsedRealtime() / 60000);
        r.put("installedApps", info.installedApps().size());
        r.put("autoRunPackage", config.getAutoRunPackage());
        r.put("kioskPackages", config.getKioskPackages());

        // Joignabilité du serveur TMS et dérive d'horloge
        long start = SystemClock.elapsedRealtime();
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(config.getServerUrl() + "/actuator/health").openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(5000);
            int code = c.getResponseCode();
            r.put("serverReachable", code == 200);
            r.put("serverLatencyMs", SystemClock.elapsedRealtime() - start);
            long serverDate = c.getDate();
            if (serverDate > 0) {
                r.put("clockDriftSeconds", (System.currentTimeMillis() - serverDate) / 1000);
            }
            c.disconnect();
        } catch (Exception e) {
            r.put("serverReachable", false);
            r.put("serverError", e.getMessage());
        }
        return r;
    }

    public String summary(Map<String, Object> r) {
        return "Batterie " + r.get("batteryLevel") + " %, stockage libre " + r.get("storageFreePercent")
                + " %, réseau " + r.get("networkType") + ", serveur "
                + (Boolean.TRUE.equals(r.get("serverReachable")) ? "OK (" + r.get("serverLatencyMs") + " ms)" : "injoignable");
    }

    /**
     * Logs de l'agent (logcat). Android ne donne accès qu'aux logs de l'application elle-même,
     * sauf aux applications signées système.
     */
    public OpResult extractLogs(long taskId, int lines) throws Exception {
        File out = new File(app.getCacheDir(), "tms-logs-" + taskId + ".txt");
        int count = 0;
        Process p = Runtime.getRuntime().exec(new String[]{"logcat", "-d", "-v", "time", "-t", String.valueOf(lines)});
        try (BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
             Writer w = new FileWriter(out)) {
            w.write("# TMS2M Agent " + BuildConfig.VERSION_NAME + " - " + app.device().vendor() + " "
                    + app.device().model() + " - SN " + app.device().serialNumber() + "\n");
            String line;
            while ((line = in.readLine()) != null) {
                w.write(line);
                w.write('\n');
                count++;
            }
        } finally {
            p.destroy();
        }
        try {
            upload(taskId, out, "logs-" + app.device().serialNumber() + ".txt");
        } finally {
            //noinspection ResultOfMethodCallIgnored
            out.delete();
        }
        return OpResult.ok(count + " ligne(s) de logs remontée(s)");
    }

    public OpResult extractFile(long taskId, String path) throws Exception {
        File f = new File(path);
        if (!f.exists()) {
            return OpResult.fail("Fichier introuvable : " + path);
        }
        if (f.isDirectory()) {
            return OpResult.fail("Le chemin est un dossier : " + path);
        }
        if (!f.canRead()) {
            return OpResult.fail("Accès refusé par Android : " + path);
        }
        if (f.length() > MAX_UPLOAD_BYTES) {
            return OpResult.fail("Fichier trop volumineux (" + f.length() / (1024 * 1024) + " Mo, max 20 Mo)");
        }
        upload(taskId, f, f.getName());
        return OpResult.ok(f.getName() + " remonté (" + f.length() + " octets)");
    }

    private void upload(long taskId, File file, String name) throws Exception {
        RequestBody body = RequestBody.create(MediaType.parse("application/octet-stream"), file);
        MultipartBody.Part part = MultipartBody.Part.createFormData("file", name, body);
        Response<Void> resp = client.api().uploadArtifact(taskId, part).execute();
        if (!resp.isSuccessful()) {
            throw new IllegalStateException("Téléversement refusé : HTTP " + resp.code());
        }
    }
}
