package com.tms.server.service;

import com.tms.server.domain.*;
import com.tms.server.repository.AppPackageRepository;
import com.tms.server.repository.TaskRepository;
import com.tms.server.repository.TerminalRepository;
import com.tms.server.web.dto.AdminDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Création de tâches unitaires ou de masse (déploiements) et suivi. */
@Service
public class TaskService {

    private final TaskRepository tasks;
    private final TerminalRepository terminals;
    private final AppPackageRepository apps;
    private final JsonSupport json;

    public TaskService(TaskRepository tasks, TerminalRepository terminals, AppPackageRepository apps, JsonSupport json) {
        this.tasks = tasks;
        this.terminals = terminals;
        this.apps = apps;
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
        String payloadJson = json.write(payload);
        List<Task> created = targets.stream().map(t -> {
            Task task = new Task();
            task.setTerminal(t);
            task.setType(req.type());
            task.setPayloadJson(payloadJson);
            task.setDeploymentId(deploymentId);
            return task;
        }).toList();
        tasks.saveAll(created);
        return new DeploymentResponse(deploymentId, created.size());
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

    @Transactional
    public TaskDto cancel(Long id) {
        Task task = tasks.findById(id).orElseThrow(() -> ApiException.notFound("Tâche", id));
        if (task.getStatus().isFinal() || task.getStatus() == TaskStatus.IN_PROGRESS) {
            throw ApiException.conflict("Tâche " + id + " non annulable (statut " + task.getStatus() + ")");
        }
        task.setStatus(TaskStatus.CANCELLED);
        task.setMessage("Annulée par un administrateur");
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
                payload.put("appId", app.getId());
                payload.put("packageName", app.getPackageName());
                payload.put("versionName", app.getVersionName());
                payload.put("versionCode", app.getVersionCode());
                payload.put("sha256", app.getSha256());
                payload.put("sizeBytes", app.getSizeBytes());
                payload.put("downloadUrl", "/api/device/v1/apps/" + app.getId() + "/download");
            }
            case UNINSTALL_APP, PUSH_PARAMS -> {
                if (req.packageName() == null || req.packageName().isBlank()) {
                    throw ApiException.badRequest("packageName requis pour " + req.type());
                }
                payload.put("packageName", req.packageName().trim());
            }
            case REBOOT -> {
                // pas de payload
            }
        }
        return payload;
    }

    private List<Terminal> resolveTargets(DeploymentTarget target) {
        List<Terminal> list;
        if (target.terminalIds() != null && !target.terminalIds().isEmpty()) {
            list = terminals.findAllById(target.terminalIds());
        } else if (target.groupId() == null && target.merchantId() == null && target.manufacturer() == null
                && !target.all()) {
            throw ApiException.badRequest("Cible vide : précisez terminalIds, groupId, merchantId, manufacturer ou all=true");
        } else {
            list = terminals.findAll(TerminalRepository.filter(
                    target.manufacturer(), null, target.groupId(), target.merchantId(), null));
        }
        // Les terminaux pré-enregistrés reçoivent leurs tâches dès l'enrôlement (staging).
        return list.stream().filter(t -> t.getStatus() != TerminalStatus.DISABLED).toList();
    }

    TaskDto toDto(Task t) {
        Terminal term = t.getTerminal();
        return new TaskDto(t.getId(), term.getId(), term.getSerialNumber(), term.getManufacturer(),
                t.getType(), json.readMap(t.getPayloadJson()), t.getStatus(), t.getMessage(),
                t.getDeploymentId(), t.getCreatedAt(), t.getUpdatedAt());
    }
}
