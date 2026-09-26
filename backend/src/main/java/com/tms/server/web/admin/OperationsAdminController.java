package com.tms.server.web.admin;

import com.tms.server.config.TmsProperties;
import com.tms.server.domain.*;
import com.tms.server.service.AuditService;
import com.tms.server.service.DeviceService;
import com.tms.server.service.ParameterService;
import com.tms.server.service.TaskService;
import com.tms.server.service.TerminalService;
import com.tms.server.web.dto.AdminDtos.*;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

/** Tableau de bord, paramètres, tâches, déploiements et historique. */
@RestController
@RequestMapping("/api/admin/v1")
public class OperationsAdminController {

    private final TerminalService terminals;
    private final TaskService tasks;
    private final DeviceService devices;
    private final ParameterService parameters;
    private final AuditService audit;
    private final TmsProperties props;

    public OperationsAdminController(TerminalService terminals, TaskService tasks, DeviceService devices,
                                     ParameterService parameters, AuditService audit, TmsProperties props) {
        this.terminals = terminals;
        this.tasks = tasks;
        this.devices = devices;
        this.parameters = parameters;
        this.audit = audit;
        this.props = props;
    }

    @GetMapping("/dashboard")
    public DashboardDto dashboard() {
        return terminals.dashboard();
    }

    @GetMapping("/meta")
    public MetaDto meta() {
        return new MetaDto(Arrays.asList(Manufacturer.values()), Arrays.asList(TerminalStatus.values()),
                Arrays.asList(TaskType.values()), Arrays.asList(TaskStatus.values()),
                Arrays.asList(ParameterScope.values()), props.device().pollIntervalSeconds(),
                ZoneId.systemDefault().getId(), props.device().agentAutoUpdateOrDefault());
    }

    // ---- Paramètres ----

    @GetMapping("/parameters")
    public List<ParameterDto> parameters() {
        return parameters.list();
    }

    @PutMapping("/parameters")
    public List<ParameterDto> upsertParameters(@Valid @RequestBody ParameterUpsertRequest req) {
        List<ParameterDto> saved = parameters.upsert(req);
        audit.log("PARAMETERS_SAVED", req.scope() == ParameterScope.TERMINAL ? req.scopeRef() : null,
                req.packageName() + " → " + req.scope() + (req.scopeRef() != null ? " #" + req.scopeRef() : "")
                        + " (" + req.values().size() + " clé(s))");
        return saved;
    }

    @DeleteMapping("/parameters/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteParameter(@PathVariable Long id) {
        parameters.delete(id);
    }

    // ---- Tâches & déploiements ----

    @GetMapping("/tasks")
    public List<TaskDto> tasks(@RequestParam(required = false) TaskStatus status,
                               @RequestParam(required = false) Long terminalId,
                               @RequestParam(required = false) String deploymentId) {
        return tasks.list(status, terminalId, deploymentId);
    }

    @PostMapping("/tasks/{id}/cancel")
    public TaskDto cancel(@PathVariable Long id) {
        return tasks.cancel(id);
    }

    /** Fichier remonté par le terminal (logs, fichier extrait). */
    @GetMapping("/tasks/{id}/artifact")
    public ResponseEntity<Resource> artifact(@PathVariable Long id) {
        Task task = tasks.get(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + task.getArtifactName() + "\"")
                .body(new FileSystemResource(devices.artifactPath(task)));
    }

    @PostMapping("/deployments")
    @ResponseStatus(HttpStatus.CREATED)
    public DeploymentResponse deploy(@Valid @RequestBody DeploymentRequest req) {
        return tasks.deploy(req);
    }

    // ---- Historique ----

    @GetMapping("/audit")
    public List<AuditEventDto> audit() {
        return audit.list(null);
    }
}
