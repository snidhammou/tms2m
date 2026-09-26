package com.tms.server.web.admin;

import com.tms.server.domain.DeploymentTemplate;
import com.tms.server.domain.Merchant;
import com.tms.server.domain.TerminalGroup;
import com.tms.server.repository.MerchantRepository;
import com.tms.server.repository.TerminalGroupRepository;
import com.tms.server.repository.TerminalRepository;
import com.tms.server.service.ApiException;
import com.tms.server.service.AuditService;
import com.tms.server.service.OrganizationService;
import com.tms.server.service.TemplateService;
import com.tms.server.web.dto.AdminDtos.GroupDto;
import com.tms.server.web.dto.AdminDtos.MerchantDto;
import com.tms.server.web.dto.AdminDtos.OrganizationDto;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

/** CRUD des organisations, marchands et groupes de terminaux. */
@RestController
@RequestMapping("/api/admin/v1")
public class ReferenceDataController {

    private final MerchantRepository merchants;
    private final TerminalGroupRepository groups;
    private final TerminalRepository terminals;
    private final OrganizationService organizations;
    private final TemplateService templates;
    private final AuditService audit;

    public ReferenceDataController(MerchantRepository merchants, TerminalGroupRepository groups,
                                   TerminalRepository terminals, OrganizationService organizations,
                                   TemplateService templates, AuditService audit) {
        this.merchants = merchants;
        this.groups = groups;
        this.terminals = terminals;
        this.organizations = organizations;
        this.templates = templates;
        this.audit = audit;
    }

    // ---- Organisations ----

    @GetMapping("/organizations")
    public List<OrganizationDto> organizations() {
        return organizations.list();
    }

    @PostMapping("/organizations")
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationDto createOrganization(@Valid @RequestBody OrganizationDto dto) {
        return organizations.save(null, dto);
    }

    @PutMapping("/organizations/{id}")
    public OrganizationDto updateOrganization(@PathVariable Long id, @Valid @RequestBody OrganizationDto dto) {
        return organizations.save(id, dto);
    }

    @DeleteMapping("/organizations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOrganization(@PathVariable Long id) {
        organizations.delete(id);
    }

    // ---- Marchands ----

    @GetMapping("/merchants")
    @Transactional(readOnly = true)
    public List<MerchantDto> merchants() {
        return merchants.findAll(Sort.by("name")).stream().map(MerchantDto::of).toList();
    }

    @PostMapping("/merchants")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public MerchantDto createMerchant(@Valid @RequestBody MerchantDto dto) {
        Merchant m = merchants.save(apply(new Merchant(), dto));
        audit.log("MERCHANT_CREATED", null, m.getCode() + " " + m.getName());
        return MerchantDto.of(m);
    }

    @PutMapping("/merchants/{id}")
    @Transactional
    public MerchantDto updateMerchant(@PathVariable Long id, @Valid @RequestBody MerchantDto dto) {
        Merchant m = merchants.findById(id).orElseThrow(() -> ApiException.notFound("Marchand", id));
        return MerchantDto.of(merchants.save(apply(m, dto)));
    }

    @DeleteMapping("/merchants/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deleteMerchant(@PathVariable Long id) {
        if (terminals.existsByMerchantId(id)) {
            throw ApiException.conflict("Des terminaux sont rattachés à ce marchand");
        }
        merchants.deleteById(id);
        audit.log("MERCHANT_DELETED", null, "#" + id);
    }

    // ---- Groupes ----

    @GetMapping("/groups")
    @Transactional(readOnly = true)
    public List<GroupDto> groups() {
        return groups.findAll(Sort.by("name")).stream().map(GroupDto::of).toList();
    }

    @PostMapping("/groups")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public GroupDto createGroup(@Valid @RequestBody GroupDto dto) {
        return saveGroup(new TerminalGroup(), dto);
    }

    @PutMapping("/groups/{id}")
    @Transactional
    public GroupDto updateGroup(@PathVariable Long id, @Valid @RequestBody GroupDto dto) {
        TerminalGroup g = groups.findById(id).orElseThrow(() -> ApiException.notFound("Groupe", id));
        return saveGroup(g, dto);
    }

    @DeleteMapping("/groups/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deleteGroup(@PathVariable Long id) {
        if (terminals.existsByGroupId(id)) {
            throw ApiException.conflict("Des terminaux sont rattachés à ce groupe");
        }
        groups.deleteById(id);
        audit.log("GROUP_DELETED", null, "#" + id);
    }

    private GroupDto saveGroup(TerminalGroup g, GroupDto dto) {
        Long oldTemplate = g.getTemplate() != null ? g.getTemplate().getId() : null;
        g.setName(dto.name().trim());
        g.setDescription(dto.description());
        DeploymentTemplate t = dto.templateId() == null ? null : templates.deploymentTemplate(dto.templateId());
        g.setTemplate(t);
        TerminalGroup saved = groups.save(g);
        if (t != null && (!Objects.equals(oldTemplate, t.getId()) || Boolean.TRUE.equals(dto.applyToMembers()))) {
            int n = templates.onGroupTemplateChanged(saved, Boolean.TRUE.equals(dto.applyToMembers()));
            audit.log("GROUP_TEMPLATE", null, saved.getName() + " → modèle « " + t.getName() + " »"
                    + (n > 0 ? ", " + n + " tâche(s) pour les membres" : ""));
        }
        return GroupDto.of(saved);
    }

    private Merchant apply(Merchant m, MerchantDto dto) {
        m.setCode(dto.code().trim());
        m.setName(dto.name().trim());
        m.setCity(dto.city());
        m.setAddress(dto.address());
        m.setOrganization(dto.organizationId() == null ? null : organizations.find(dto.organizationId()));
        return m;
    }
}
