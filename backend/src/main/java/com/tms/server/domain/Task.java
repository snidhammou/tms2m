package com.tms.server.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalTime;

@Entity
@Table(name = "tasks", indexes = {
        @Index(name = "idx_task_terminal_status", columnList = "terminal_id, status"),
        @Index(name = "idx_task_deployment", columnList = "deploymentId")
})
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "terminal_id")
    private Terminal terminal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskType type;

    @Column(columnDefinition = "text")
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status = TaskStatus.PENDING;

    @Column(length = 1000)
    private String message;

    /** Identifiant commun aux tâches créées par un même déploiement. */
    @Column(length = 36)
    private String deploymentId;

    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    // --- Planification (mise à jour planifiée) ---
    /** La tâche n'est pas délivrée avant cet instant (null = immédiat). */
    private Instant notBefore;
    /** Fenêtre quotidienne de livraison, heure du serveur (null = toute la journée). */
    private LocalTime windowStart;
    private LocalTime windowEnd;

    // --- Résultats (diagnostic, extraction de logs / fichiers) ---
    @Column(columnDefinition = "text")
    private String resultJson;
    /** Fichier téléversé par le terminal (nom de stockage). */
    private String artifactStoredName;
    /** Nom d'origine du fichier téléversé. */
    private String artifactName;
    private Long artifactSize;

    public Instant getNotBefore() { return notBefore; }
    public void setNotBefore(Instant notBefore) { this.notBefore = notBefore; }
    public LocalTime getWindowStart() { return windowStart; }
    public void setWindowStart(LocalTime windowStart) { this.windowStart = windowStart; }
    public LocalTime getWindowEnd() { return windowEnd; }
    public void setWindowEnd(LocalTime windowEnd) { this.windowEnd = windowEnd; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public String getArtifactStoredName() { return artifactStoredName; }
    public void setArtifactStoredName(String v) { this.artifactStoredName = v; }
    public String getArtifactName() { return artifactName; }
    public void setArtifactName(String v) { this.artifactName = v; }
    public Long getArtifactSize() { return artifactSize; }
    public void setArtifactSize(Long v) { this.artifactSize = v; }

    /** Vrai si la tâche peut être délivrée à cet instant (planification respectée). */
    public boolean isDeliverableAt(Instant now, java.time.ZoneId zone) {
        if (notBefore != null && now.isBefore(notBefore)) {
            return false;
        }
        if (windowStart == null || windowEnd == null) {
            return true;
        }
        LocalTime t = now.atZone(zone).toLocalTime();
        return windowStart.isBefore(windowEnd)
                ? !t.isBefore(windowStart) && t.isBefore(windowEnd)
                : !t.isBefore(windowStart) || t.isBefore(windowEnd); // fenêtre de nuit (ex. 22:00 → 06:00)
    }

    public Long getId() { return id; }
    public Terminal getTerminal() { return terminal; }
    public void setTerminal(Terminal terminal) { this.terminal = terminal; }
    public TaskType getType() { return type; }
    public void setType(TaskType type) { this.type = type; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; this.updatedAt = Instant.now(); }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getDeploymentId() { return deploymentId; }
    public void setDeploymentId(String deploymentId) { this.deploymentId = deploymentId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
