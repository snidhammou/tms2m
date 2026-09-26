package com.tms.server.service;

import com.tms.server.domain.Parameter;
import com.tms.server.domain.ParameterScope;
import com.tms.server.domain.Terminal;
import com.tms.server.repository.ParameterRepository;
import com.tms.server.repository.TerminalGroupRepository;
import com.tms.server.repository.TerminalRepository;
import com.tms.server.web.dto.AdminDtos.ParameterDto;
import com.tms.server.web.dto.AdminDtos.ParameterUpsertRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class ParameterService {

    private final ParameterRepository repo;
    private final TerminalRepository terminals;
    private final TerminalGroupRepository groups;

    public ParameterService(ParameterRepository repo, TerminalRepository terminals, TerminalGroupRepository groups) {
        this.repo = repo;
        this.terminals = terminals;
        this.groups = groups;
    }

    /** Fusion GLOBAL -> GROUP -> TERMINAL : le niveau le plus spécifique l'emporte. */
    @Transactional(readOnly = true)
    public Map<String, String> resolve(Terminal terminal, String packageName) {
        Map<String, String> result = new TreeMap<>();
        put(result, repo.findByScopeAndScopeRefIsNullAndPackageName(ParameterScope.GLOBAL, packageName));
        if (terminal.getGroup() != null) {
            put(result, repo.findByScopeAndScopeRefAndPackageName(
                    ParameterScope.GROUP, terminal.getGroup().getId(), packageName));
        }
        put(result, repo.findByScopeAndScopeRefAndPackageName(ParameterScope.TERMINAL, terminal.getId(), packageName));
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, String> resolve(Long terminalId, String packageName) {
        Terminal t = terminals.findById(terminalId).orElseThrow(() -> ApiException.notFound("Terminal", terminalId));
        return resolve(t, packageName);
    }

    @Transactional(readOnly = true)
    public List<ParameterDto> list() {
        return repo.findAllByOrderByPackageNameAscScopeAscParamKeyAsc().stream().map(ParameterDto::of).toList();
    }

    @Transactional
    public List<ParameterDto> upsert(ParameterUpsertRequest req) {
        Long scopeRef = validateScope(req.scope(), req.scopeRef());
        List<Parameter> existing = scopeRef == null
                ? repo.findByScopeAndScopeRefIsNullAndPackageName(req.scope(), req.packageName())
                : repo.findByScopeAndScopeRefAndPackageName(req.scope(), scopeRef, req.packageName());

        Map<String, Parameter> byKey = new HashMap<>();
        existing.forEach(p -> byKey.put(p.getParamKey(), p));

        List<Parameter> saved = new ArrayList<>();
        req.values().forEach((key, value) -> {
            String k = key.trim();
            if (k.isEmpty()) {
                return;
            }
            Parameter p = byKey.remove(k);
            if (p == null) {
                p = new Parameter();
                p.setScope(req.scope());
                p.setScopeRef(scopeRef);
                p.setPackageName(req.packageName().trim());
                p.setParamKey(k);
            }
            p.setParamValue(value);
            p.setUpdatedAt(Instant.now());
            saved.add(repo.save(p));
        });
        if (req.replace()) {
            repo.deleteAll(byKey.values());
        }
        return saved.stream().map(ParameterDto::of).toList();
    }

    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id)) {
            throw ApiException.notFound("Paramètre", id);
        }
        repo.deleteById(id);
    }

    private Long validateScope(ParameterScope scope, Long scopeRef) {
        switch (scope) {
            case GLOBAL:
                return null;
            case GROUP:
                if (scopeRef == null || !groups.existsById(scopeRef)) {
                    throw ApiException.badRequest("scopeRef doit référencer un groupe existant");
                }
                return scopeRef;
            case TERMINAL:
                if (scopeRef == null || !terminals.existsById(scopeRef)) {
                    throw ApiException.badRequest("scopeRef doit référencer un terminal existant");
                }
                return scopeRef;
            default:
                throw ApiException.badRequest("Scope inconnu");
        }
    }

    private static void put(Map<String, String> target, List<Parameter> params) {
        params.forEach(p -> target.put(p.getParamKey(), p.getParamValue()));
    }
}
