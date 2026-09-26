package com.tms.server.web.admin;

import com.tms.server.service.TemplateService;
import com.tms.server.web.dto.AdminDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Modèles de paramètres et modèles de déploiement (zéro contact). */
@RestController
@RequestMapping("/api/admin/v1")
public class TemplateAdminController {

    private final TemplateService templates;

    public TemplateAdminController(TemplateService templates) {
        this.templates = templates;
    }

    // ---- Modèles de paramètres ----

    @GetMapping("/parameter-templates")
    public List<ParameterTemplateDto> parameterTemplates() {
        return templates.listParameterTemplates();
    }

    @PostMapping("/parameter-templates")
    @ResponseStatus(HttpStatus.CREATED)
    public ParameterTemplateDto createParameterTemplate(@Valid @RequestBody ParameterTemplateDto dto) {
        return templates.saveParameterTemplate(null, dto);
    }

    @PutMapping("/parameter-templates/{id}")
    public ParameterTemplateDto updateParameterTemplate(@PathVariable Long id, @Valid @RequestBody ParameterTemplateDto dto) {
        return templates.saveParameterTemplate(id, dto);
    }

    @DeleteMapping("/parameter-templates/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteParameterTemplate(@PathVariable Long id) {
        templates.deleteParameterTemplate(id);
    }

    @PostMapping("/parameter-templates/{id}/apply")
    public DeploymentResponse applyParameterTemplate(@PathVariable Long id,
                                                     @Valid @RequestBody ApplyParameterTemplateRequest req) {
        return templates.applyParameterTemplate(id, req);
    }

    // ---- Modèles de déploiement ----

    @GetMapping("/deployment-templates")
    public List<DeploymentTemplateDto> deploymentTemplates() {
        return templates.listDeploymentTemplates();
    }

    @PostMapping("/deployment-templates")
    @ResponseStatus(HttpStatus.CREATED)
    public DeploymentTemplateDto createDeploymentTemplate(@Valid @RequestBody DeploymentTemplateDto dto) {
        return templates.saveDeploymentTemplate(null, dto);
    }

    @PutMapping("/deployment-templates/{id}")
    public DeploymentTemplateDto updateDeploymentTemplate(@PathVariable Long id,
                                                          @Valid @RequestBody DeploymentTemplateDto dto) {
        return templates.saveDeploymentTemplate(id, dto);
    }

    @DeleteMapping("/deployment-templates/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDeploymentTemplate(@PathVariable Long id) {
        templates.deleteDeploymentTemplate(id);
    }
}
