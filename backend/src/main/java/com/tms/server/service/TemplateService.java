package com.tms.server.service;

import com.tms.server.domain.*;
import com.tms.server.repository.*;
import com.tms.server.web.dto.AdminDtos.*;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Modèles de paramètres (jeux réutilisables par application) et modèles de déploiement
 * "zéro contact" : un groupe lié à un modèle provisionne automatiquement tout terminal
 * qui le rejoint (applications, paramètres, démarrage auto, kiosque).
 */
@Service
public class TemplateService {

    private final DeploymentTemplateRepository deploymentTemplates;
    private final ParameterTemplateRepository parameterTemplates;
    private final TerminalGroupRepository groups;
    private final TerminalRepository terminals;
    private final AppPackageRepository apps;
    private final ParameterService parameters;
    private final TaskService tasks;
    private final AuditService audit;
    private final JsonSupport json;

    public TemplateService(DeploymentTemplateRepository deploymentTemplates,
                           ParameterTemplateRepository parameterTemplates, TerminalGroupRepository groups,
                           TerminalRepository terminals, AppPackageRepository apps, ParameterService parameters,
                           TaskService tasks, AuditService audit, JsonSupport json) {
        this.deploymentTemplates = deploymentTemplates;
        this.parameterTemplates = parameterTemplates;
        this.groups = groups;
        this.terminals = terminals;
        this.apps = apps;
        this.parameters = parameters;
        this.tasks = tasks;
        this.audit = audit;
        this.json = json;
    }

    // ------------------------------------------------------------------ modèles de paramètres

    @Transactional(readOnly = true)
    public List<ParameterTemplateDto> listParameterTemplates() {
        return parameterTemplates.findAll(Sort.by("name")).stream().map(this::toDto).toList();
    }

    @Transactional
    public ParameterTemplateDto saveParameterTemplate(Long id, ParameterTemplateDto dto) {
        ParameterTemplate t = id == null ? new ParameterTemplate() : parameterTemplate(id);
        t.setName(dto.name().trim());
        t.setPackageName(dto.packageName().trim());
        t.setValuesJson(json.write(dto.values() == null ? Map.of() : new TreeMap<>(dto.values())));
        t.setUpdatedAt(Instant.now());
        ParameterTemplate saved = parameterTemplates.save(t);
        audit.log("PARAM_TEMPLATE_SAVED", null, saved.getName() + " (" + saved.getPackageName() + ")");
        return toDto(saved);
    }

    @Transactional
    public void deleteParameterTemplate(Long id) {
        ParameterTemplate t = parameterTemplate(id);
        boolean used = deploymentTemplates.findAll().stream()
                .anyMatch(d -> json.readLongList(d.getParameterTemplateIdsJson()).contains(id));
        if (used) {
            throw ApiException.conflict("Modèle de paramètres utilisé par un modèle de déploiement");
        }
        parameterTemplates.delete(t);
        audit.log("PARAM_TEMPLATE_DELETED", null, t.getName());
    }

    /** Copie les valeurs du modèle au niveau demandé, et pousse aux terminaux concernés si demandé. */
    @Transactional
    public DeploymentResponse applyParameterTemplate(Long id, ApplyParameterTemplateRequest req) {
        ParameterTemplate t = parameterTemplate(id);
        parameters.upsert(new ParameterUpsertRequest(req.scope(), req.scopeRef(), t.getPackageName(),
                json.readStringMap(t.getValuesJson()), false));
        audit.log("PARAM_TEMPLATE_APPLIED", req.scope() == ParameterScope.TERMINAL ? req.scopeRef() : null,
                t.getName() + " → " + req.scope() + (req.scopeRef() != null ? " #" + req.scopeRef() : ""));
        if (!req.push()) {
            return new DeploymentResponse(null, 0);
        }
        List<Terminal> targets = switch (req.scope()) {
            case GLOBAL -> terminals.findAll();
            case GROUP -> terminals.findAll(TerminalRepository.filter(null, null, req.scopeRef(), null, null));
            case TERMINAL -> terminals.findAllById(List.of(req.scopeRef()));
        };
        targets = targets.stream().filter(x -> x.getStatus() != TerminalStatus.DISABLED).toList();
        String deploymentId = UUID.randomUUID().toString();
        int n = tasks.createTasks(targets, TaskType.PUSH_PARAMS, Map.of("packageName", t.getPackageName()),
                null, deploymentId);
        return new DeploymentResponse(deploymentId, n);
    }

    // ------------------------------------------------------------------ modèles de déploiement

    @Transactional(readOnly = true)
    public List<DeploymentTemplateDto> listDeploymentTemplates() {
        return deploymentTemplates.findAll(Sort.by("name")).stream().map(this::toDto).toList();
    }

    @Transactional
    public DeploymentTemplateDto saveDeploymentTemplate(Long id, DeploymentTemplateDto dto) {
        DeploymentTemplate t = id == null ? new DeploymentTemplate() : deploymentTemplate(id);
        List<Long> appIds = dto.appIds() == null ? List.of() : dto.appIds();
        appIds.forEach(a -> apps.findById(a).orElseThrow(() -> ApiException.notFound("Application", a)));
        List<Long> paramIds = dto.parameterTemplateIds() == null ? List.of() : dto.parameterTemplateIds();
        paramIds.forEach(this::parameterTemplate);
        t.setName(dto.name().trim());
        t.setDescription(dto.description());
        t.setAppIdsJson(json.write(appIds));
        t.setParameterTemplateIdsJson(json.write(paramIds));
        t.setAutoRunPackage(dto.autoRunPackage() == null || dto.autoRunPackage().isBlank()
                ? null : dto.autoRunPackage().trim());
        t.setKioskPackagesJson(json.write(dto.kioskPackages() == null ? List.of()
                : dto.kioskPackages().stream().map(String::trim).filter(s -> !s.isEmpty()).distinct().toList()));
        t.setUpdatedAt(Instant.now());
        DeploymentTemplate saved = deploymentTemplates.save(t);
        audit.log("DEPLOY_TEMPLATE_SAVED", null, saved.getName());
        return toDto(saved);
    }

    @Transactional
    public void deleteDeploymentTemplate(Long id) {
        DeploymentTemplate t = deploymentTemplate(id);
        if (groups.existsByTemplateId(id)) {
            throw ApiException.conflict("Modèle lié à un groupe : détachez-le d'abord");
        }
        deploymentTemplates.delete(t);
        audit.log("DEPLOY_TEMPLATE_DELETED", null, t.getName());
    }

    /**
     * Appelé quand le modèle d'un groupe change : les paramètres du modèle sont copiés au niveau
     * GROUP, puis, si demandé, le modèle est appliqué aux terminaux déjà membres.
     */
    @Transactional
    public int onGroupTemplateChanged(TerminalGroup group, boolean applyToMembers) {
        DeploymentTemplate t = group.getTemplate();
        if (t == null) {
            return 0;
        }
        for (Long pid : json.readLongList(t.getParameterTemplateIdsJson())) {
            ParameterTemplate pt = parameterTemplate(pid);
            parameters.upsert(new ParameterUpsertRequest(ParameterScope.GROUP, group.getId(), pt.getPackageName(),
                    json.readStringMap(pt.getValuesJson()), false));
        }
        if (!applyToMembers) {
            return 0;
        }
        List<Terminal> members = terminals.findAll(TerminalRepository.filter(null, null, group.getId(), null, null))
                .stream().filter(x -> x.getStatus() != TerminalStatus.DISABLED).toList();
        return applyDeploymentTemplate(t, members);
    }

    /** Zéro contact : provisionne un terminal qui vient de rejoindre un groupe doté d'un modèle. */
    @Transactional
    public int onTerminalJoinedGroup(Terminal terminal) {
        TerminalGroup g = terminal.getGroup();
        if (g == null || g.getTemplate() == null || terminal.getStatus() == TerminalStatus.DISABLED) {
            return 0;
        }
        int n = applyDeploymentTemplate(g.getTemplate(), List.of(terminal));
        audit.log("ZERO_TOUCH", terminal.getId(), "Modèle « " + g.getTemplate().getName() + " » ("
                + n + " tâche(s)) via le groupe " + g.getName());
        return n;
    }

    private int applyDeploymentTemplate(DeploymentTemplate t, List<Terminal> targets) {
        if (targets.isEmpty()) {
            return 0;
        }
        String deploymentId = "template-" + t.getId() + "-" + UUID.randomUUID().toString().substring(0, 8);
        int n = 0;
        for (Long appId : json.readLongList(t.getAppIdsJson())) {
            AppPackage app = apps.findById(appId).orElse(null);
            if (app != null) {
                n += tasks.createTasks(targets, TaskType.INSTALL_APP, tasks.installPayload(app), null, deploymentId);
            }
        }
        Set<String> paramPackages = new LinkedHashSet<>();
        for (Long pid : json.readLongList(t.getParameterTemplateIdsJson())) {
            parameterTemplates.findById(pid).ifPresent(pt -> paramPackages.add(pt.getPackageName()));
        }
        for (String pkg : paramPackages) {
            n += tasks.createTasks(targets, TaskType.PUSH_PARAMS, Map.of("packageName", pkg), null, deploymentId);
        }
        if (t.getAutoRunPackage() != null) {
            n += tasks.createTasks(targets, TaskType.SET_AUTORUN, Map.of("packageName", t.getAutoRunPackage()),
                    null, deploymentId);
        }
        List<String> kiosk = json.readStringList(t.getKioskPackagesJson());
        if (!kiosk.isEmpty()) {
            n += tasks.createTasks(targets, TaskType.SET_KIOSK, Map.of("packages", kiosk), null, deploymentId);
        }
        return n;
    }

    // ------------------------------------------------------------------ utils

    private ParameterTemplate parameterTemplate(Long id) {
        return parameterTemplates.findById(id).orElseThrow(() -> ApiException.notFound("Modèle de paramètres", id));
    }

    @Transactional(readOnly = true)
    public DeploymentTemplate deploymentTemplate(Long id) {
        return deploymentTemplates.findById(id).orElseThrow(() -> ApiException.notFound("Modèle de déploiement", id));
    }

    private ParameterTemplateDto toDto(ParameterTemplate t) {
        return new ParameterTemplateDto(t.getId(), t.getName(), t.getPackageName(),
                json.readStringMap(t.getValuesJson()), t.getUpdatedAt());
    }

    private DeploymentTemplateDto toDto(DeploymentTemplate t) {
        return new DeploymentTemplateDto(t.getId(), t.getName(), t.getDescription(),
                json.readLongList(t.getAppIdsJson()), json.readLongList(t.getParameterTemplateIdsJson()),
                t.getAutoRunPackage(), json.readStringList(t.getKioskPackagesJson()), t.getUpdatedAt());
    }
}
