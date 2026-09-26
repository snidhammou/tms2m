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

    /** Vrai si l'agent peut lire les logs de toutes les applications (READ_LOGS accordée par adb). */
    public boolean canReadAllLogs() {
        return app.checkCallingOrSelfPermission(android.Manifest.permission.READ_LOGS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Logs du terminal (logcat). Sans READ_LOGS, Android ne donne accès qu'aux logs de l'agent.
     * {@code packageName} (facultatif) : ne garde que les logs du processus de cette application.
     */
    /**
     * @param fromEpochMs début de plage (0 = aucun) ; prioritaire sur {@code sinceMinutes}
     * @param toEpochMs   fin de plage (0 = jusqu'à maintenant)
     */
    public OpResult extractLogs(long taskId, int lines, String packageName, int sinceMinutes,
                                long fromEpochMs, long toEpochMs) throws Exception {
        boolean full = canReadAllLogs();
        // Bornes exprimées à l'heure locale du terminal, format des lignes "logcat -v time"
        java.text.SimpleDateFormat logTime = new java.text.SimpleDateFormat("MM-dd HH:mm:ss.SSS", java.util.Locale.ROOT);
        long fromMs = fromEpochMs > 0 ? fromEpochMs
                : sinceMinutes > 0 ? System.currentTimeMillis() - sinceMinutes * 60_000L : 0;
        String since = fromMs > 0 ? logTime.format(new java.util.Date(fromMs)) : null;   // "-T" de logcat
        String until = toEpochMs > 0 ? logTime.format(new java.util.Date(toEpochMs)) : null;
        boolean filtered = packageName != null && !packageName.isEmpty();
        String uidTag = null;
        java.util.regex.Pattern uidPattern = null;
        if (filtered) {
            if (!full && !packageName.equals(app.getPackageName())) {
                return OpResult.fail("Logs de " + packageName + " inaccessibles : accordez READ_LOGS à l'agent "
                        + "(adb shell pm grant com.tms.agent android.permission.READ_LOGS)");
            }
            try {
                int uid = app.getPackageManager().getApplicationInfo(packageName, 0).uid;
                uidTag = "uid " + uid;
                uidPattern = java.util.regex.Pattern.compile("\\(\\s*(" + uid + "|" + uidName(uid) + ")\\s*:");
            } catch (android.content.pm.PackageManager.NameNotFoundException e) {
                return OpResult.fail("Application non installée : " + packageName);
            }
        }
        // Filtre par application ou par période : on lit ce qu'il faut du journal, puis on garde les N dernières lignes.
        java.util.List<String> args = new java.util.ArrayList<>(java.util.Arrays.asList("logcat", "-d", "-v", "time"));
        if (filtered) {
            args.add("-v");
            args.add("uid");
        }
        if (since != null) {
            args.add("-T");
            args.add(since);
        } else if (!filtered) {
            args.add("-t");
            args.add(String.valueOf(lines));
        }
        String[] cmd = args.toArray(new String[0]);
        java.util.ArrayDeque<String> kept = new java.util.ArrayDeque<>();
        long matched = 0;
        // Ligne la plus ancienne encore présente dans le journal : si elle est postérieure au début
        // de la plage demandée, le tampon circulaire a déjà écrasé ce début
        String first = since != null ? oldestLogTimestamp() : null;
        Process p = Runtime.getRuntime().exec(cmd);
        try (BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = in.readLine()) != null) {
                boolean stamped = line.length() >= 18 && Character.isDigit(line.charAt(0));
                // Format "-v time -v uid" : "I/Tag(10157:17129): message" (uid numérique, parfois aligné par des espaces)
                if (filtered && !uidPattern.matcher(line).find()) {
                    continue;
                }
                // Fin de plage : l'horodatage "MM-dd HH:mm:ss.SSS" se compare comme une chaîne
                if (until != null && stamped && line.substring(0, 18).compareTo(until) > 0) {
                    continue;
                }
                matched++;
                kept.addLast(line);
                if (kept.size() > lines) {
                    kept.removeFirst();
                }
            }
        } finally {
            p.destroy();
        }
        int count = kept.size();
        boolean truncated = matched > count;
        // Compression gzip : ≈ 10x plus léger pour du texte de logs
        File out = new File(app.getCacheDir(), "tms-logs-" + taskId + ".txt.gz");
        try (Writer w = new java.io.OutputStreamWriter(
                new java.util.zip.GZIPOutputStream(new java.io.FileOutputStream(out)), "UTF-8")) {
            w.write("# TMS2M Agent " + BuildConfig.VERSION_NAME + " - " + app.device().vendor() + " "
                    + app.device().model() + " - SN " + app.device().serialNumber()
                    + (filtered ? " - filtre " + packageName + " (" + uidTag + ")" : "") + "\n");
            w.write("# Portée : " + (full ? "logs de toutes les applications (READ_LOGS)"
                    : "logs de l'agent uniquement (READ_LOGS non accordée)") + "\n");
            if (since != null || until != null) {
                w.write("# Plage demandée (heure du terminal) : " + (since != null ? since : "début du journal")
                        + " → " + (until != null ? until : "maintenant") + "\n");
            }
            if (since != null && first != null && first.compareTo(since) > 0) {
                w.write("# ATTENTION : le journal du terminal ne remonte qu'à " + first
                        + " (tampon circulaire) : le début de la plage n'est plus disponible\n");
            }
            if (truncated) {
                w.write("# Tronqué : " + matched + " lignes trouvées, seules les " + count + " dernières sont incluses\n");
            }
            for (String l : kept) {
                w.write(l);
                w.write('\n');
            }
        }
        try {
            upload(taskId, out, "logs-" + app.device().serialNumber()
                    + (filtered ? "-" + packageName : "") + ".txt.gz");
        } finally {
            //noinspection ResultOfMethodCallIgnored
            out.delete();
        }
        return OpResult.ok(count + " ligne(s) de logs remontée(s)"
                + (truncated ? " sur " + matched : "")
                + (full ? " (toutes applications)" : " (agent uniquement, READ_LOGS non accordée)")
                + (since != null && first != null && first.compareTo(since) > 0 ? " — journal disponible depuis " + first : ""));
    }

    /** Horodatage de la plus ancienne ligne du journal ("MM-dd HH:mm:ss.SSS"), ou null. */
    private static String oldestLogTimestamp() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"logcat", "-d", "-v", "time", "-m", "5"});
            try (BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.length() >= 18 && Character.isDigit(line.charAt(0))) {
                        return line.substring(0, 18);
                    }
                }
            } finally {
                p.destroy();
            }
        } catch (Exception ignored) {
            // indéterminable : pas d'avertissement
        }
        return null;
    }

    /** Nom d'utilisateur Linux affiché par "logcat -v uid" (ex. 10120 -> u0_a120). */
    private static String uidName(int uid) {
        int user = uid / 100000;
        int appId = uid % 100000;
        return appId >= 10000 ? "u" + user + "_a" + (appId - 10000) : String.valueOf(uid);
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
