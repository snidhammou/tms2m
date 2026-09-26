package com.tms.server.service;

import com.tms.server.domain.AuditEvent;
import com.tms.server.repository.AuditEventRepository;
import com.tms.server.web.dto.AdminDtos.AuditEventDto;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Historique des actions (administrateurs) et des événements terminaux. */
@Service
public class AuditService {

    private final AuditEventRepository repo;

    public AuditService(AuditEventRepository repo) {
        this.repo = repo;
    }

    /** Enregistre un événement ; l'acteur est l'administrateur connecté, sinon "system". */
    @Transactional(propagation = Propagation.REQUIRED)
    public void log(String action, Long terminalId, String details) {
        repo.save(new AuditEvent(currentActor(), action, terminalId, details));
    }

    /** Action automatique du serveur (mise à jour de l'agent…). */
    @Transactional
    public void logSystem(String action, Long terminalId, String details) {
        repo.save(new AuditEvent("system", action, terminalId, details));
    }

    @Transactional
    public void logDevice(String action, Long terminalId, String details) {
        repo.save(new AuditEvent("device", action, terminalId, details));
    }

    @Transactional(readOnly = true)
    public List<AuditEventDto> list(Long terminalId) {
        var events = terminalId != null
                ? repo.findTop300ByTerminalIdOrderByIdDesc(terminalId)
                : repo.findTop300ByOrderByIdDesc();
        return events.stream().map(AuditEventDto::of).toList();
    }

    static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return "system";
        }
        return auth.getPrincipal() instanceof Long ? "device" : auth.getName();
    }
}
