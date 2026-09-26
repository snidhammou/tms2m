package com.tms.server.service;

import com.tms.server.domain.*;
import com.tms.server.repository.AppPackageRepository;
import com.tms.server.repository.TaskRepository;
import com.tms.server.repository.TerminalRepository;
import com.tms.server.web.dto.AdminDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.*;

/** Création de tâches unitaires ou de masse (déploiements), planification et suivi. */
@Service
public class TaskService {

    private static final int DEFAULT_LOG_LINES = 2000;
    private static final int MAX_LOG_LINES = 20000;

    private final TaskRepository tasks;
    private final TerminalRepository terminals;
    private final AppPackageRepository apps;
    private final OrganizationService organizations;
    private final AuditService audit;
    private final JsonSupport json;

    public TaskService(TaskRepository tasks, TerminalRepository terminals, AppPackageRepository apps,
                       OrganizationService organizations, AuditService audit, JsonSupport json) {
        this.tasks = tasks;
        this.terminals = terminals;
        this.apps = apps;
        this.organizations = organizations;
        this.audit = audit;
        this.json = json;
    }

    @Transactional
    public DeploymentResponse deploy(DeploymentRequest req) {
        Map<String, Object> payload = buildPayload(req);
        List<Terminal> targets = resolveTargets(req.target());
        if (targets.isEmpty()) {
            throw ApiException.badRequest("Aucun terminal ne correspond à la cible");
        }
        String deploymentId = UUID.randomUUID().toString();
        int count = createTasks(targets, req.type(), payload, req.schedule(), deploymentId);
        audit.log("DEPLOYMENT", targets.size() == 1 ? targets.get(0).getId() : null,
                req.type() + " sur " + count + " terminal(aux)" + describe(payload) + scheduleText(req.schedule()));
        return new DeploymentResponse(deploymentId, count);
    }

    /** Crée une tâche identique par terminal (utilisé par les déploiements et les modèles zéro contact). */
    @Transactional
    public int createTasks(List<Terminal> targets, TaskType type, Map<String, Object> payload, Schedule schedule,
                           String deploymentId) {
        String payloadJson = json.write(payload);
        LocalTime[] window = parseWindow(schedule);
        List<Task> created = targets.stream().map(t -> {
            Task task = new Task();
            task.setTerminal(t);
            task.setType(type);
            task.setPayloadJson(payloadJson);
            task.setDeploymentId(deploymentId);
            if (schedule != null) {
                task.setNotBefore(schedule.notBefore());
            }
            task.setWindowStart(window[0]);
            task.setWindowEnd(window[1]);
            return task;
        }).toList();
        tasks.saveAll(created);
        return created.size();
    }

    /** Payload d'installation d'un APK (partagé avec les modèles de déploiement). */
    public Map<String, Object> installPayload(AppPackage app) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("appId", app.getId());
        payload.put("packageName", app.getPackageName());
        payload.put("versionName", app.getVersionName());
        payload.put("versionCode", app.getVersionCode());
        payload.put("sha256", app.getSha256());
        payload.put("sizeBytes", app.getSizeBytes());
        payload.put("downloadUrl", "/api/device/v1/apps/" + app.getId() + "/download");
        return payload;
    }

    @Transactional(readOnly = true)
    public List<TaskDto> list(TaskStatus status, Long terminalId, String deploymentId) {
        List<Task> result;
        if (deploymentId != null && !deploymentId.isBlank()) {
            result = tasks.findByDeploymentIdOrderByIdAsc(deploymentId);
        } else if (terminalId != null) {
            result = tasks.findTop200ByTerminalIdOrderByIdDesc(terminalId);
        } else if (status != null) {
            result = tasks.findTop200ByStatusOrderByIdDesc(status);
        } else {
            result = tasks.findTop200ByOrderByIdDesc();
        }
        return result.stream()
                .filter(t -> status == null || t.getStatus() == status)
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Task get(Long id) {
        return tasks.findById(id).orElseThrow(() -> ApiException.notFound("Tâche", id));
    }

    @Transactional
    public TaskDto cancel(Long id) {
        Task task = get(id);
        if (task.getStatus().isFinal() || task.getStatus() == TaskStatus.IN_PROGRESS) {
            throw ApiException.conflict("Tâche " + id + " non annulable (statut " + task.getStatus() + ")");
        }
        task.setStatus(TaskStatus.CANCELLED);
        task.setMessage("Annulée par un administrateur");
        audit.log("TASK_CANCELLED", task.getTerminal().getId(), "#" + id + " " + task.getType());
        return toDto(task);
    }

    private Map<String, Object> buildPayload(DeploymentRequest req) {
        Map<String, Object> payload = new LinkedHashMap<>();
        switch (req.type()) {
            case INSTALL_APP -> {
                if (req.appId() == null) {
                    throw ApiException.badRequest("appId requis pour INSTALL_APP");
                }
                AppPackage app = apps.findById(req.appId())
                        .orElseThrow(() -> ApiException.notFound("Application", req.appId()));
                payload.putAll(installPayload(app));
            }
            case UNINSTALL_APP, PUSH_PARAMS -> payload.put("packageName", requiredPackage(req));
            case SET_AUTORUN -> payload.put("packageName", req.packageName() == null ? "" : req.packageName().trim());
            case SET_KIOSK -> payload.put("packages", req.kioskPackages() == null ? List.of()
                    : req.kioskPackages().stream().map(String::trim).filter(s -> !s.isEmpty()).distinct().toList());
            case EXTRACT_LOGS -> {
                int lines = req.logLines() == null ? DEFAULT_LOG_LINES : req.logLines();
                payload.put("lines", Math.max(100, Math.min(lines, MAX_LOG_LINES)));
            }
            case EXTRACT_FILE -> {
                String path = req.filePath() == null ? "" : req.filePath().trim();
                if (!path.startsWith("/")) {
                    throw ApiException.badRequest("filePath doit être un chemin absolu sur le terminal (ex. /sdcard/...)");
                }
                payload.put("path", path);
            }
            case REBOOT, DIAGNOSE -> {
                // pas de payload
            }
        }
        return payload;
    }

    private static String requiredPackage(DeploymentRequest req) {
        if (req.packageName() == null || req.packageName().isBlank()) {
            throw ApiException.badRequest("packageName requis pour " + req.type());
        }
        return req.packageName().trim();
    }

    private List<Terminal> resolveTargets(DeploymentTarget target) {
        List<Terminal> list;
        if (target.terminalIds() != null && !target.terminalIds().isEmpty()) {
            list = terminals.findAllById(target.terminalIds());
        } else if (target.organizationId() == null && target.groupId() == null && target.merchantId() == null
                && target.manufacturer() == null && !target.all()) {
            throw ApiException.badRequest(
                    "Cible vide : précisez terminalIds, organizationId, groupId, merchantId, manufacturer ou all=true");
        } else {
            Collection<Long> orgIds = target.organizationId() == null ? null
                    : organizations.descendantIds(target.organizationId());
            list = terminals.findAll(TerminalRepository.filter(
                    target.manufacturer(), null, target.groupId(), target.merchantId(), orgIds, null));
        }
        // Les terminaux pré-enregistrés reçoivent leurs tâches dès l'enrôlement (staging).
        return list.stream().filter(t -> t.getStatus() != TerminalStatus.DISABLED).toList();
    }

    private static LocalTime[] parseWindow(Schedule s) {
        if (s == null || isBlank(s.windowStart()) || isBlank(s.windowEnd())) {
            if (s != null && isBlank(s.windowStart()) != isBlank(s.windowEnd())) {
                throw ApiException.badRequest("Fenêtre de mise à jour : renseignez début ET fin");
            }
            return new LocalTime[]{null, null};
        }
        try {
            LocalTime start = LocalTime.parse(s.windowStart().trim());
            LocalTime end = LocalTime.parse(s.windowEnd().trim());
            if (start.equals(end)) {
                throw ApiException.badRequest("Fenêtre de mise à jour vide (début = fin)");
            }
            return new LocalTime[]{start, end};
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("Heure invalide (format HH:mm attendu)");
        }
    }

    private static String scheduleText(Schedule s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (s.notBefore() != null) sb.append(", à partir de ").append(s.notBefore());
        if (!isBlank(s.windowStart())) sb.append(", fenêtre ").append(s.windowStart()).append("-").append(s.windowEnd());
        return sb.toString();
    }

    private static String describe(Map<String, Object> payload) {
        Object pkg = payload.get("packageName");
        return pkg != null && !pkg.toString().isEmpty() ? " (" + pkg + ")" : "";
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    @SuppressWarnings("unchecked")
    TaskDto toDto(Task t) {
        Terminal term = t.getTerminal();
        Map<String, Object> result = t.getResultJson() == null ? null : json.readMap(t.getResultJson());
        return new TaskDto(t.getId(), term.getId(), term.getSerialNumber(), term.getManufacturer(),
                t.getType(), json.readMap(t.getPayloadJson()), t.getStatus(), t.getMessage(),
                t.getDeploymentId(), t.getCreatedAt(), t.getUpdatedAt(),
                t.getNotBefore(),
                t.getWindowStart() != null ? t.getWindowStart().toString() : null,
                t.getWindowEnd() != null ? t.getWindowEnd().toString() : null,
                result, t.getArtifactName(), t.getArtifactSize());
    }
}
