package com.tms.agent.core;

import android.util.Log;

import com.tms.agent.AgentApp;
import com.tms.agent.BuildConfig;
import com.tms.agent.config.AgentConfig;
import com.tms.agent.device.DeviceManager;
import com.tms.agent.net.ApiClient;
import com.tms.agent.net.Dtos;

import java.io.IOException;

import retrofit2.Response;

/** Un cycle = enrôlement si besoin, vidage de l'outbox, heartbeat, exécution des tâches reçues. */
public class SyncEngine {

    private static final String TAG = "TmsAgent";

    private final AgentApp app;
    private final AgentConfig config;
    private final DeviceManager device;
    private final ApiClient client;
    private final StatusOutbox outbox;
    private final DeviceInfo deviceInfo;
    private final TaskExecutor tasks;

    public SyncEngine(AgentApp app) {
        this.app = app;
        this.config = app.config();
        this.device = app.device();
        this.client = new ApiClient(config);
        this.outbox = new StatusOutbox(app);
        this.deviceInfo = new DeviceInfo(app, device, config);
        this.tasks = new TaskExecutor(app, client, outbox, new ParameterStore(app), deviceInfo);
    }

    private boolean permissionsGranted;

    public void runOnce() {
        if (!config.isConfigured()) {
            status("Non configuré : renseignez l'URL du serveur et la clé d'enrôlement");
            return;
        }
        try {
            if (!config.isEnrolled() && !enroll()) {
                return;
            }
            if (!permissionsGranted) {
                // En Device Owner : position et lecture de fichiers accordées sans intervention à l'écran
                com.tms.agent.admin.AgentDeviceAdmin.grantSelfPermissions(app);
                permissionsGranted = true;
            }
            outbox.flush(client.api());
            closePendingSelfUpdate();
            closePendingReboot();

            Response<Dtos.HeartbeatResponse> resp = client.api().heartbeat(deviceInfo.heartbeat()).execute();
            if (resp.code() == 401) {
                config.clearEnrollment();
                status("Jeton refusé par le serveur : ré-enrôlement au prochain cycle");
                return;
            }
            if (!resp.isSuccessful() || resp.body() == null) {
                status("Heartbeat refusé : HTTP " + resp.code());
                return;
            }
            Dtos.HeartbeatResponse hb = resp.body();
            if (hb.pollIntervalSeconds > 0) {
                config.setPollIntervalSeconds(hb.pollIntervalSeconds);
            }
            config.markSynced();

            int count = hb.tasks == null ? 0 : hb.tasks.size();
            if (count == 0) {
                status("Synchronisé");
                return;
            }
            status(count + " tâche(s) en cours…");
            for (Dtos.DeviceTask task : hb.tasks) {
                tasks.execute(task);
            }
            status("Synchronisé — " + count + " tâche(s) traitée(s)");
        } catch (IOException e) {
            status("Serveur injoignable : " + e.getMessage());
        } catch (IllegalArgumentException e) {
            status(e.getMessage());
        }
    }

    private boolean enroll() throws IOException {
        Dtos.EnrollRequest req = new Dtos.EnrollRequest();
        req.serialNumber = device.serialNumber();
        req.enrollmentKey = config.getEnrollmentKey();
        req.manufacturer = device.vendor();
        req.model = device.model();
        req.osVersion = android.os.Build.VERSION.RELEASE;
        req.firmwareVersion = device.firmwareVersion();
        req.agentVersion = BuildConfig.VERSION_NAME;

        Response<Dtos.EnrollResponse> resp = client.api().enroll(req).execute();
        if (!resp.isSuccessful() || resp.body() == null) {
            String detail = resp.errorBody() != null ? resp.errorBody().string() : "";
            status("Enrôlement refusé (HTTP " + resp.code() + ") " + detail);
            return false;
        }
        Dtos.EnrollResponse body = resp.body();
        config.saveEnrollment(body.terminalId, body.deviceToken, body.pollIntervalSeconds);
        Log.i(TAG, "Enrôlé : terminalId=" + body.terminalId + " sn=" + req.serialNumber);
        status("Enrôlé (terminal #" + body.terminalId + ")");
        return true;
    }

    /** Clôture la tâche d'auto-mise à jour de l'agent après redémarrage du process. */
    private void closePendingSelfUpdate() {
        long taskId = config.getPendingSelfUpdateTask();
        if (taskId < 0) {
            return;
        }
        long expected = config.getPendingSelfUpdateVersion();
        boolean ok = BuildConfig.VERSION_CODE >= expected;
        tasks.report(taskId, ok ? "SUCCESS" : "FAILED",
                ok ? "Agent mis à jour en " + BuildConfig.VERSION_NAME
                        : "Agent toujours en versionCode " + BuildConfig.VERSION_CODE);
        config.clearPendingSelfUpdate();
    }

    /** Clôture une tâche REBOOT interrompue par le redémarrage (ou par un arrêt de l'agent). */
    private void closePendingReboot() {
        long taskId = config.getPendingRebootTask();
        if (taskId < 0) {
            return;
        }
        // Tolérance de 10 s : l'heure de boot calculée varie légèrement d'une mesure à l'autre
        boolean rebooted = TaskExecutor.bootTimeMillis() > config.getPendingRebootBootTime() + 10_000;
        tasks.report(taskId, rebooted ? "SUCCESS" : "FAILED",
                rebooted ? "Terminal redémarré" : "Agent relancé sans redémarrage du terminal");
        config.clearPendingReboot();
    }

    private void status(String s) {
        Log.i(TAG, s);
        config.setLastStatus(s);
    }
}
