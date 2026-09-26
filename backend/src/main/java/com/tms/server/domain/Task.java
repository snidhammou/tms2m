package com.tms.server.domain;

import jakarta.persistence.*;

import java.time.Instant;

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
