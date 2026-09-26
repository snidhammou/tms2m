package com.tms.server.web.admin;

import com.tms.server.config.TmsProperties;
import com.tms.server.domain.*;
import com.tms.server.service.ParameterService;
import com.tms.server.service.TaskService;
import com.tms.server.service.TerminalService;
import com.tms.server.web.dto.AdminDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/** Tableau de bord, paramètres, tâches et déploiements. */
@RestController
@RequestMapping("/api/admin/v1")
public class OperationsAdminController {

    private final TerminalService terminals;
    private final TaskService tasks;
    private final ParameterService parameters;
    private final TmsProperties props;

    public OperationsAdminController(TerminalService terminals, TaskService tasks, ParameterService parameters,
                                     TmsProperties props) {
        this.terminals = terminals;
        this.tasks = tasks;
        this.parameters = parameters;
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
                Arrays.asList(ParameterScope.values()), props.device().pollIntervalSeconds());
    }

    // ---- Paramètres ----

    @GetMapping("/parameters")
    public List<ParameterDto> parameters() {
        return parameters.list();
    }

    @PutMapping("/parameters")
    public List<ParameterDto> upsertParameters(@Valid @RequestBody ParameterUpsertRequest req) {
        return parameters.upsert(req);
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

    @PostMapping("/deployments")
    @ResponseStatus(HttpStatus.CREATED)
    public DeploymentResponse deploy(@Valid @RequestBody DeploymentRequest req) {
        return tasks.deploy(req);
    }
}
