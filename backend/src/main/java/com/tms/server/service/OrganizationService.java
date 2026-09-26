package com.tms.server.service;

import com.tms.server.domain.Organization;
import com.tms.server.repository.MerchantRepository;
import com.tms.server.repository.OrganizationRepository;
import com.tms.server.web.dto.AdminDtos.OrganizationDto;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Hiérarchie d'organisations (ex. acquéreur > région > agence). */
@Service
public class OrganizationService {

    private final OrganizationRepository repo;
    private final MerchantRepository merchants;
    private final AuditService audit;

    public OrganizationService(OrganizationRepository repo, MerchantRepository merchants, AuditService audit) {
        this.repo = repo;
        this.merchants = merchants;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<OrganizationDto> list() {
        return repo.findAll(Sort.by("name")).stream().map(OrganizationDto::of).toList();
    }

    @Transactional
    public OrganizationDto save(Long id, OrganizationDto dto) {
        Organization o = id == null ? new Organization() : find(id);
        o.setName(dto.name().trim());
        o.setDescription(dto.description());
        Organization parent = dto.parentId() == null ? null : find(dto.parentId());
        if (parent != null && id != null && descendantIds(id).contains(parent.getId())) {
            throw ApiException.badRequest("Une organisation ne peut pas être rattachée à elle-même ou à une descendante");
        }
        o.setParent(parent);
        Organization saved = repo.save(o);
        audit.log(id == null ? "ORGANIZATION_CREATED" : "ORGANIZATION_UPDATED", null, saved.getName());
        return OrganizationDto.of(saved);
    }

    @Transactional
    public void delete(Long id) {
        Organization o = find(id);
        if (repo.existsByParentId(id) || merchants.existsByOrganizationId(id)) {
            throw ApiException.conflict("Organisation non vide (sous-organisations ou marchands rattachés)");
        }
        repo.delete(o);
        audit.log("ORGANIZATION_DELETED", null, o.getName());
    }

    /** L'organisation et toutes ses descendantes. */
    @Transactional(readOnly = true)
    public Set<Long> descendantIds(Long rootId) {
        Map<Long, List<Long>> children = new HashMap<>();
        for (Organization o : repo.findAll()) {
            if (o.getParent() != null) {
                children.computeIfAbsent(o.getParent().getId(), k -> new ArrayList<>()).add(o.getId());
            }
        }
        Set<Long> result = new LinkedHashSet<>();
        Deque<Long> todo = new ArrayDeque<>(List.of(rootId));
        while (!todo.isEmpty()) {
            Long id = todo.pop();
            if (result.add(id)) {
                todo.addAll(children.getOrDefault(id, List.of()));
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Organization find(Long id) {
        return repo.findById(id).orElseThrow(() -> ApiException.notFound("Organisation", id));
    }
}
