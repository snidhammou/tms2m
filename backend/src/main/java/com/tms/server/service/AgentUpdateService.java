package com.tms.server.service;

import com.tms.server.config.TmsProperties;
import com.tms.server.domain.*;
import com.tms.server.repository.AppPackageRepository;
import com.tms.server.repository.TaskRepository;
import com.tms.server.web.dto.DeviceDtos.InstalledApp;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Mise à jour automatique de TMS2M Agent : dès qu'une version plus récente de l'agent est publiée
 * dans le dépôt d'applications, chaque terminal reçoit une tâche d'installation à son heartbeat.
 * <p>
 * Chaque marque a son propre APK (SDK constructeur) : la variante est lue dans le suffixe du
 * versionName ({@code 1.0.1-pax}, {@code 1.0.1-newland}, {@code 1.0.1} pour universal) et seul
 * l'APK de la même variante est proposé au terminal.
 */
@Service
public class AgentUpdateService {

    public static final String AGENT_PACKAGE = "com.tms.agent";
    /** Délai avant un nouvel essai après un échec ou une confirmation restée sans réponse. */
    private static final Duration RETRY_AFTER_FAILURE = Duration.ofHours(6);

    private final AppPackageRepository apps;
    private final TaskRepository tasks;
    private final TaskService taskService;
    private final AuditService audit;
    private final JsonSupport json;
    private final boolean enabled;

    public AgentUpdateService(AppPackageRepository apps, TaskRepository tasks, TaskService taskService,
                              AuditService audit, JsonSupport json, TmsProperties props) {
        this.apps = apps;
        this.tasks = tasks;
        this.taskService = taskService;
        this.audit = audit;
        this.json = json;
        this.enabled = props.device().agentAutoUpdateOrDefault();
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Appelé dans la transaction du heartbeat, avant la sélection des tâches à délivrer. */
    public void checkForUpdate(Terminal t) {
        if (!enabled || t.getStatus() != TerminalStatus.ACTIVE || t.getAgentVersion() == null) {
            return;
        }
        long installed = installedAgentVersionCode(t);
        if (installed < 0) {
            return; // inventaire pas encore remonté
        }
        Optional<AppPackage> latest = latestFor(variant(t.getAgentVersion()));
        if (latest.isEmpty() || latest.get().getVersionCode() <= installed) {
            return;
        }
        AppPackage app = latest.get();
        // Une tâche par (terminal, version) : pas de doublon, pas de boucle si l'installation échoue
        String marker = "agent-update:" + app.getId();
        Task last = tasks.findTopByTerminalIdAndDeploymentIdOrderByIdDesc(t.getId(), marker);
        if (last != null && !isRetryable(last)) {
            return;
        }
        taskService.createTasks(List.of(t), TaskType.INSTALL_APP, taskService.installPayload(app), null, marker);
        audit.logSystem("AGENT_AUTO_UPDATE", t.getId(), t.getAgentVersion() + " → " + app.getVersionName()
                + (last != null ? " (nouvel essai)" : ""));
    }

    /** Échec, ou confirmation à l'écran jamais faite : nouvel essai après le délai. */
    private static boolean isRetryable(Task last) {
        boolean stale = last.getUpdatedAt().isBefore(Instant.now().minus(RETRY_AFTER_FAILURE));
        return stale && (last.getStatus() == TaskStatus.FAILED || last.getStatus() == TaskStatus.IN_PROGRESS);
    }

    /** Dernière version publiée de l'agent pour une variante ("" = universal, "pax", "newland"…). */
    public Optional<AppPackage> latestFor(String variant) {
        return apps.findByPackageNameOrderByVersionCodeDesc(AGENT_PACKAGE).stream()
                .filter(a -> Objects.equals(variant(a.getVersionName()), variant))
                .findFirst();
    }

    private long installedAgentVersionCode(Terminal t) {
        return json.readApps(t.getInstalledAppsJson()).stream()
                .filter(a -> AGENT_PACKAGE.equals(a.packageName()))
                .mapToLong(InstalledApp::versionCode)
                .findFirst().orElse(-1);
    }

    /** "1.0.1-pax" → "pax" ; "1.0.1" → "" (universal). */
    static String variant(String versionName) {
        if (versionName == null) {
            return "";
        }
        int dash = versionName.indexOf('-');
        return dash < 0 ? "" : versionName.substring(dash + 1).trim().toLowerCase();
    }
}
