package com.tms.server.web.admin;

import com.tms.server.domain.Manufacturer;
import com.tms.server.domain.TerminalStatus;
import com.tms.server.service.AuditService;
import com.tms.server.service.ParameterService;
import com.tms.server.service.TerminalService;
import com.tms.server.web.dto.AdminDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/v1/terminals")
public class TerminalAdminController {

    private final TerminalService terminals;
    private final ParameterService parameters;
    private final AuditService audit;

    public TerminalAdminController(TerminalService terminals, ParameterService parameters, AuditService audit) {
        this.terminals = terminals;
        this.parameters = parameters;
        this.audit = audit;
    }

    @GetMapping
    public List<TerminalDto> list(@RequestParam(required = false) Manufacturer manufacturer,
                                  @RequestParam(required = false) TerminalStatus status,
                                  @RequestParam(required = false) Long groupId,
                                  @RequestParam(required = false) Long merchantId,
                                  @RequestParam(required = false) Long organizationId,
                                  @RequestParam(required = false) String q) {
        return terminals.list(manufacturer, status, groupId, merchantId, organizationId, q);
    }

    @GetMapping("/{id}")
    public TerminalDto get(@PathVariable Long id) {
        return terminals.get(id);
    }

    /** Pré-enregistrement (staging) d'un terminal avant sa livraison. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TerminalDto create(@Valid @RequestBody TerminalCreateRequest req) {
        return terminals.create(req);
    }

    /** Pré-enregistrement par lots : un n° de série par ligne. */
    @PostMapping("/bulk")
    @ResponseStatus(HttpStatus.CREATED)
    public BulkRegisterResponse bulk(@Valid @RequestBody BulkRegisterRequest req) {
        return terminals.bulkRegister(req);
    }

    @PutMapping("/{id}")
    public TerminalDto update(@PathVariable Long id, @RequestBody TerminalUpdateRequest req) {
        return terminals.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        terminals.delete(id);
    }

    /** Paramètres effectifs (après fusion GLOBAL/GROUP/TERMINAL) tels que le terminal les recevra. */
    @GetMapping("/{id}/parameters")
    public Map<String, String> effectiveParameters(@PathVariable Long id, @RequestParam String packageName) {
        return parameters.resolve(id, packageName);
    }

    /** Points de supervision (batterie, stockage, RAM, trafic réseau) sur les dernières heures. */
    @GetMapping("/{id}/metrics")
    public List<MetricPointDto> metrics(@PathVariable Long id, @RequestParam(defaultValue = "24") int hours) {
        return terminals.metrics(id, hours);
    }

    /** Historique du terminal (enrôlement, applications, actions admin…). */
    @GetMapping("/{id}/history")
    public List<AuditEventDto> history(@PathVariable Long id) {
        return audit.list(id);
    }
}
