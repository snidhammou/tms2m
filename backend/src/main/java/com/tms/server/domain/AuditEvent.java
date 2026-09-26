package com.tms.server.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Journal d'historique : actions des administrateurs et événements des terminaux
 * (enrôlement, applications installées / retirées, changements de statut…).
 */
@Entity
@Table(name = "audit_events", indexes = {
        @Index(name = "idx_audit_terminal", columnList = "terminalId"),
        @Index(name = "idx_audit_time", columnList = "occurredAt")
})
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant occurredAt = Instant.now();

    /** Utilisateur admin, ou "device" / "system". */
    @Column(nullable = false, length = 64)
    private String actor;

    @Column(nullable = false, length = 64)
    private String action;

    private Long terminalId;

    @Column(length = 1000)
    private String details;

    public AuditEvent() {
    }

    public AuditEvent(String actor, String action, Long terminalId, String details) {
        this.actor = actor;
        this.action = action;
        this.terminalId = terminalId;
        this.details = details != null && details.length() > 1000 ? details.substring(0, 1000) : details;
    }

    public Long getId() { return id; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getActor() { return actor; }
    public String getAction() { return action; }
    public Long getTerminalId() { return terminalId; }
    public String getDetails() { return details; }
}
