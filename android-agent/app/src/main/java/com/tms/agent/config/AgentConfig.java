package com.tms.agent.config;

import android.content.Context;
import android.content.SharedPreferences;

import com.tms.agent.BuildConfig;

/** Configuration et état persistant de l'agent. */
public class AgentConfig {

    private static final String PREFS = "tms_agent";
    private static final String K_SERVER_URL = "server_url";
    private static final String K_ENROLL_KEY = "enrollment_key";
    private static final String K_TOKEN = "device_token";
    private static final String K_TERMINAL_ID = "terminal_id";
    private static final String K_POLL = "poll_interval";
    private static final String K_LAST_SYNC = "last_sync";
    private static final String K_LAST_STATUS = "last_status";
    private static final String K_SELF_UPDATE_TASK = "self_update_task";
    private static final String K_SELF_UPDATE_VERSION = "self_update_version";

    private final SharedPreferences prefs;

    public AgentConfig(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String getServerUrl() {
        return prefs.getString(K_SERVER_URL, BuildConfig.DEFAULT_SERVER_URL);
    }

    public String getEnrollmentKey() {
        return prefs.getString(K_ENROLL_KEY, BuildConfig.DEFAULT_ENROLLMENT_KEY);
    }

    public void setConnection(String serverUrl, String enrollmentKey) {
        String url = serverUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        boolean changed = !url.equals(getServerUrl());
        SharedPreferences.Editor e = prefs.edit()
                .putString(K_SERVER_URL, url)
                .putString(K_ENROLL_KEY, enrollmentKey.trim());
        if (changed) {
            // Nouveau serveur : l'ancien jeton n'y est pas valide
            e.remove(K_TOKEN).remove(K_TERMINAL_ID);
        }
        e.apply();
    }

    public boolean isConfigured() {
        return !getServerUrl().isEmpty() && !getEnrollmentKey().isEmpty();
    }

    public boolean isEnrolled() {
        return getDeviceToken() != null;
    }

    public String getDeviceToken() {
        return prefs.getString(K_TOKEN, null);
    }

    public long getTerminalId() {
        return prefs.getLong(K_TERMINAL_ID, -1);
    }

    public void saveEnrollment(long terminalId, String token, int pollIntervalSeconds) {
        prefs.edit()
                .putLong(K_TERMINAL_ID, terminalId)
                .putString(K_TOKEN, token)
                .putInt(K_POLL, pollIntervalSeconds)
                .apply();
    }

    public void clearEnrollment() {
        prefs.edit().remove(K_TOKEN).remove(K_TERMINAL_ID).apply();
    }

    public int getPollIntervalSeconds() {
        return Math.max(15, prefs.getInt(K_POLL, 60));
    }

    public void setPollIntervalSeconds(int seconds) {
        prefs.edit().putInt(K_POLL, seconds).apply();
    }

    public long getLastSyncAt() {
        return prefs.getLong(K_LAST_SYNC, 0);
    }

    public void markSynced() {
        prefs.edit().putLong(K_LAST_SYNC, System.currentTimeMillis()).apply();
    }

    public String getLastStatus() {
        return prefs.getString(K_LAST_STATUS, "");
    }

    public void setLastStatus(String status) {
        prefs.edit().putString(K_LAST_STATUS, status).apply();
    }

    // --- Auto-mise à jour de l'agent : le process est tué pendant l'installation,
    //     on mémorise la tâche pour la clôturer au redémarrage.

    public void setPendingSelfUpdate(long taskId, long versionCode) {
        prefs.edit().putLong(K_SELF_UPDATE_TASK, taskId).putLong(K_SELF_UPDATE_VERSION, versionCode).commit();
    }

    public long getPendingSelfUpdateTask() {
        return prefs.getLong(K_SELF_UPDATE_TASK, -1);
    }

    public long getPendingSelfUpdateVersion() {
        return prefs.getLong(K_SELF_UPDATE_VERSION, -1);
    }

    public void clearPendingSelfUpdate() {
        prefs.edit().remove(K_SELF_UPDATE_TASK).remove(K_SELF_UPDATE_VERSION).apply();
    }

    // --- Reboot : le succès n'est confirmé qu'après un vrai redémarrage (heure de boot changée).

    private static final String K_REBOOT_TASK = "reboot_task";
    private static final String K_REBOOT_BOOT_TIME = "reboot_boot_time";

    public void setPendingReboot(long taskId, long bootTimeMillis) {
        prefs.edit().putLong(K_REBOOT_TASK, taskId).putLong(K_REBOOT_BOOT_TIME, bootTimeMillis).commit();
    }

    public long getPendingRebootTask() {
        return prefs.getLong(K_REBOOT_TASK, -1);
    }

    public long getPendingRebootBootTime() {
        return prefs.getLong(K_REBOOT_BOOT_TIME, -1);
    }

    public void clearPendingReboot() {
        prefs.edit().remove(K_REBOOT_TASK).remove(K_REBOOT_BOOT_TIME).commit();
    }

    // --- Démarrage auto et kiosque (appliqués par l'agent, relancés au boot)

    private static final String K_AUTORUN = "autorun_package";
    private static final String K_KIOSK = "kiosk_packages";

    public String getAutoRunPackage() {
        return prefs.getString(K_AUTORUN, null);
    }

    public void setAutoRunPackage(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            prefs.edit().remove(K_AUTORUN).apply();
        } else {
            prefs.edit().putString(K_AUTORUN, packageName).apply();
        }
    }

    public java.util.List<String> getKioskPackages() {
        String v = prefs.getString(K_KIOSK, "");
        java.util.List<String> list = new java.util.ArrayList<>();
        for (String s : v.split(",")) {
            if (!s.trim().isEmpty()) {
                list.add(s.trim());
            }
        }
        return list;
    }

    public void setKioskPackages(java.util.List<String> packages) {
        prefs.edit().putString(K_KIOSK, android.text.TextUtils.join(",", packages)).apply();
    }
}
