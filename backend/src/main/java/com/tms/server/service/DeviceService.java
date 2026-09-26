package com.tms.server.service;

import com.tms.server.config.TmsProperties;
import com.tms.server.domain.Task;
import com.tms.server.domain.TaskStatus;
import com.tms.server.domain.Terminal;
import com.tms.server.domain.TerminalMetric;
import com.tms.server.repository.TaskRepository;
import com.tms.server.repository.TerminalMetricRepository;
import com.tms.server.repository.TerminalRepository;
import com.tms.server.web.dto.DeviceDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/** Logique côté terminal : heartbeat, supervision, distribution des tâches, résultats. */
@Service
public class DeviceService {

    /** Tâches renvoyées au terminal tant qu'il ne les a pas prises en charge. */
    private static final EnumSet<TaskStatus> DELIVERABLE = EnumSet.of(TaskStatus.PENDING, TaskStatus.SENT);
    private static final EnumSet<TaskStatus> DEVICE_REPORTABLE =
            EnumSet.of(TaskStatus.IN_PROGRESS, TaskStatus.SUCCESS, TaskStatus.FAILED);
    private static final long MAX_ARTIFACT_BYTES = 50L * 1024 * 1024;

    private final TerminalRepository terminals;
    private final TaskRepository tasks;
    private final TerminalMetricRepository metrics;
    private final ParameterService parameters;
    private final AuditService audit;
    private final JsonSupport json;
    private final TmsProperties props;
    private final Path artifactDir;
    private final IconService icons;
    private final AgentUpdateService agentUpdates;

    public DeviceService(TerminalRepository terminals, TaskRepository tasks, TerminalMetricRepository metrics,
                         ParameterService parameters, AuditService audit, JsonSupport json, TmsProperties props,
                         IconService icons, AgentUpdateService agentUpdates) {
        this.icons = icons;
        this.agentUpdates = agentUpdates;
        this.terminals = terminals;
        this.tasks = tasks;
        this.metrics = metrics;
        this.parameters = parameters;
        this.audit = audit;
        this.json = json;
        this.props = props;
        // normalize() : un chemin "./data/..." doit rester comparable au chemin résolu des fichiers
        this.artifactDir = Path.of(props.storage().artifactDirOrDefault()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(artifactDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Impossible de créer " + artifactDir, e);
        }
    }

    @Transactional
    public HeartbeatResponse heartbeat(Long terminalId, HeartbeatRequest req, String remoteIp) {
        Terminal t = terminal(terminalId);
        Instant now = Instant.now();
        t.setLastSeenAt(now);
        // Champs absents = inchangés : on conserve la dernière valeur connue
        if (req.batteryLevel() != null) t.setBatteryLevel(req.batteryLevel());
        t.setIpAddress(req.ipAddress() != null ? req.ipAddress() : remoteIp);
        if (req.latitude() != null && req.longitude() != null) {
            t.setLatitude(req.latitude());
            t.setLongitude(req.longitude());
            t.setLocationAt(now);
        }
        if (req.osVersion() != null) t.setOsVersion(req.osVersion());
        if (req.firmwareVersion() != null) t.setFirmwareVersion(req.firmwareVersion());
        if (req.agentVersion() != null) t.setAgentVersion(req.agentVersion());
        if (req.storageTotalBytes() != null) t.setStorageTotalBytes(req.storageTotalBytes());
        if (req.storageFreeBytes() != null) t.setStorageFreeBytes(req.storageFreeBytes());
        if (req.ramTotalBytes() != null) t.setRamTotalBytes(req.ramTotalBytes());
        if (req.ramAvailBytes() != null) t.setRamAvailBytes(req.ramAvailBytes());
        if (req.networkType() != null) t.setNetworkType(req.networkType());
        if (req.uptimeSeconds() != null) t.setUptimeSeconds(req.uptimeSeconds());
        if (req.deviceOwner() != null) t.setDeviceOwner(req.deviceOwner());
        t.setAutoRunPackage(req.autoRunPackage());
        if (req.kioskPackages() != null) t.setKioskPackagesJson(json.write(req.kioskPackages()));
        if (req.installedApps() != null) {
            auditInventoryChanges(t, req.installedApps());
            t.setInstalledAppsJson(json.write(req.installedApps()));
        }
        recordMetric(t.getId(), req);
        // Nouvelle version de l'agent publiée : la tâche d'installation part dans cette même réponse
        agentUpdates.checkForUpdate(t);

        ZoneId zone = ZoneId.systemDefault();
        List<DeviceTask> pending = tasks.findByTerminalIdAndStatusInOrderByIdAsc(terminalId, DELIVERABLE).stream()
                .filter(task -> task.isDeliverableAt(now, zone)) // mise à jour planifiée
                .peek(task -> {
                    if (task.getStatus() == TaskStatus.PENDING) {
                        task.setStatus(TaskStatus.SENT);
                    }
                })
                .map(task -> new DeviceTask(task.getId(), task.getType(), json.readMap(task.getPayloadJson())))
                .toList();

        List<String> iconsWanted = req.installedApps() == null ? List.of()
                : icons.missing(req.installedApps().stream().map(InstalledApp::packageName).toList());
        return new HeartbeatResponse(now, props.device().pollIntervalSeconds(), pending, iconsWanted);
    }

    @Transactional
    public void updateTaskStatus(Long terminalId, Long taskId, TaskStatusUpdate update) {
        Task task = ownedTask(terminalId, taskId);
        if (!DEVICE_REPORTABLE.contains(update.status())) {
            throw ApiException.badRequest("Statut non autorisé depuis un terminal : " + update.status());
        }
        if (task.getStatus().isFinal()) {
            return; // idempotent : un retour tardif ne modifie pas une tâche close
        }
        task.setStatus(update.status());
        task.setMessage(truncate(update.message()));
        if (update.result() != null && !update.result().isEmpty()) {
            task.setResultJson(json.write(update.result()));
        }
    }

    /** Fichier produit par une tâche (logs, fichier extrait), téléversé par le terminal. */
    @Transactional
    public void uploadArtifact(Long terminalId, Long taskId, MultipartFile file) {
        Task task = ownedTask(terminalId, taskId);
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Fichier manquant");
        }
        if (file.getSize() > MAX_ARTIFACT_BYTES) {
            throw ApiException.badRequest("Fichier trop volumineux (max 50 Mo)");
        }
        String original = sanitize(file.getOriginalFilename());
        String stored = "task-" + taskId + "-" + System.currentTimeMillis() + "-" + original;
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, artifactDir.resolve(stored), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        task.setArtifactStoredName(stored);
        task.setArtifactName(original);
        task.setArtifactSize(file.getSize());
    }

    public Path artifactPath(Task task) {
        if (task.getArtifactStoredName() == null) {
            throw ApiException.notFound("Fichier de la tâche", task.getId());
        }
        Path p = artifactDir.resolve(task.getArtifactStoredName()).normalize();
        if (!p.startsWith(artifactDir) || !Files.exists(p)) {
            throw ApiException.notFound("Fichier de la tâche", task.getId());
        }
        return p;
    }

    @Transactional(readOnly = true)
    public ParametersResponse parameters(Long terminalId, String packageName) {
        Map<String, String> values = parameters.resolve(terminal(terminalId), packageName);
        return new ParametersResponse(packageName, values);
    }

    private void recordMetric(Long terminalId, HeartbeatRequest req) {
        TerminalMetric m = new TerminalMetric();
        m.setTerminalId(terminalId);
        m.setBatteryLevel(req.batteryLevel());
        m.setStorageFreeBytes(req.storageFreeBytes());
        m.setRamAvailBytes(req.ramAvailBytes());
        m.setRxBytes(req.rxBytes());
        m.setTxBytes(req.txBytes());
        m.setNetworkType(req.networkType());
        metrics.save(m);
    }

    /** Historique : applications apparues / disparues / mises à jour depuis le dernier inventaire. */
    private void auditInventoryChanges(Terminal t, List<InstalledApp> now) {
        if (t.getInstalledAppsJson() == null) {
            return; // premier inventaire : pas de différence à signaler
        }
        Map<String, InstalledApp> before = json.readApps(t.getInstalledAppsJson()).stream()
                .collect(Collectors.toMap(InstalledApp::packageName, a -> a, (a, b) -> a));
        Map<String, InstalledApp> after = now.stream()
                .collect(Collectors.toMap(InstalledApp::packageName, a -> a, (a, b) -> a));
        after.forEach((pkg, app) -> {
            InstalledApp old = before.get(pkg);
            if (old == null) {
                audit.logDevice("APP_INSTALLED", t.getId(), pkg + " " + app.versionName());
            } else if (old.versionCode() != app.versionCode()) {
                audit.logDevice("APP_UPDATED", t.getId(), pkg + " " + old.versionName() + " → " + app.versionName());
            }
        });
        before.keySet().stream().filter(pkg -> !after.containsKey(pkg))
                .forEach(pkg -> audit.logDevice("APP_REMOVED", t.getId(), pkg));
    }

    private Task ownedTask(Long terminalId, Long taskId) {
        return tasks.findById(taskId)
                .filter(x -> x.getTerminal().getId().equals(terminalId))
                .orElseThrow(() -> ApiException.notFound("Tâche", taskId));
    }

    private Terminal terminal(Long id) {
        return terminals.findById(id).orElseThrow(() -> ApiException.notFound("Terminal", id));
    }

    private static String sanitize(String name) {
        String n = name == null || name.isBlank() ? "fichier" : Path.of(name).getFileName().toString();
        return n.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String truncate(String s) {
        return s == null || s.length() <= 1000 ? s : s.substring(0, 1000);
    }
}
