package com.tms.server.web.admin;

import com.tms.server.domain.Manufacturer;
import com.tms.server.domain.TerminalStatus;
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

    public TerminalAdminController(TerminalService terminals, ParameterService parameters) {
        this.terminals = terminals;
        this.parameters = parameters;
    }

    @GetMapping
    public List<TerminalDto> list(@RequestParam(required = false) Manufacturer manufacturer,
                                  @RequestParam(required = false) TerminalStatus status,
                                  @RequestParam(required = false) Long groupId,
                                  @RequestParam(required = false) Long merchantId,
                                  @RequestParam(required = false) String q) {
        return terminals.list(manufacturer, status, groupId, merchantId, q);
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
}
