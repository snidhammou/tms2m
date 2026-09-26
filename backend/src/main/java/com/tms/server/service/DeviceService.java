package com.tms.server.service;

import com.tms.server.config.TmsProperties;
import com.tms.server.domain.Task;
import com.tms.server.domain.TaskStatus;
import com.tms.server.domain.Terminal;
import com.tms.server.repository.TaskRepository;
import com.tms.server.repository.TerminalRepository;
import com.tms.server.web.dto.DeviceDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/** Logique côté terminal : heartbeat, distribution des tâches, retour de statut. */
@Service
public class DeviceService {

    /** Tâches renvoyées au terminal tant qu'il ne les a pas prises en charge. */
    private static final EnumSet<TaskStatus> DELIVERABLE = EnumSet.of(TaskStatus.PENDING, TaskStatus.SENT);
    private static final EnumSet<TaskStatus> DEVICE_REPORTABLE =
            EnumSet.of(TaskStatus.IN_PROGRESS, TaskStatus.SUCCESS, TaskStatus.FAILED);

    private final TerminalRepository terminals;
    private final TaskRepository tasks;
    private final ParameterService parameters;
    private final JsonSupport json;
    private final TmsProperties props;

    public DeviceService(TerminalRepository terminals, TaskRepository tasks, ParameterService parameters,
                         JsonSupport json, TmsProperties props) {
        this.terminals = terminals;
        this.tasks = tasks;
        this.parameters = parameters;
        this.json = json;
        this.props = props;
    }

    @Transactional
    public HeartbeatResponse heartbeat(Long terminalId, HeartbeatRequest req, String remoteIp) {
        Terminal t = terminal(terminalId);
        t.setLastSeenAt(Instant.now());
        // Champs absents = inchangés : on conserve la dernière valeur connue
        if (req.batteryLevel() != null) t.setBatteryLevel(req.batteryLevel());
        t.setIpAddress(req.ipAddress() != null ? req.ipAddress() : remoteIp);
        if (req.latitude() != null && req.longitude() != null) {
            t.setLatitude(req.latitude());
            t.setLongitude(req.longitude());
        }
        if (req.osVersion() != null) t.setOsVersion(req.osVersion());
        if (req.firmwareVersion() != null) t.setFirmwareVersion(req.firmwareVersion());
        if (req.agentVersion() != null) t.setAgentVersion(req.agentVersion());
        if (req.installedApps() != null) t.setInstalledAppsJson(json.write(req.installedApps()));

        List<DeviceTask> pending = tasks.findByTerminalIdAndStatusInOrderByIdAsc(terminalId, DELIVERABLE).stream()
                .peek(task -> {
                    if (task.getStatus() == TaskStatus.PENDING) {
                        task.setStatus(TaskStatus.SENT);
                    }
                })
                .map(task -> new DeviceTask(task.getId(), task.getType(), json.readMap(task.getPayloadJson())))
                .toList();

        return new HeartbeatResponse(Instant.now(), props.device().pollIntervalSeconds(), pending);
    }

    @Transactional
    public void updateTaskStatus(Long terminalId, Long taskId, TaskStatusUpdate update) {
        Task task = tasks.findById(taskId)
                .filter(x -> x.getTerminal().getId().equals(terminalId))
                .orElseThrow(() -> ApiException.notFound("Tâche", taskId));
        if (!DEVICE_REPORTABLE.contains(update.status())) {
            throw ApiException.badRequest("Statut non autorisé depuis un terminal : " + update.status());
        }
        if (task.getStatus().isFinal()) {
            return; // idempotent : un retour tardif ne modifie pas une tâche close
        }
        task.setStatus(update.status());
        task.setMessage(truncate(update.message()));
    }

    @Transactional(readOnly = true)
    public ParametersResponse parameters(Long terminalId, String packageName) {
        Map<String, String> values = parameters.resolve(terminal(terminalId), packageName);
        return new ParametersResponse(packageName, values);
    }

    private Terminal terminal(Long id) {
        return terminals.findById(id).orElseThrow(() -> ApiException.notFound("Terminal", id));
    }

    private static String truncate(String s) {
        return s == null || s.length() <= 1000 ? s : s.substring(0, 1000);
    }
}
