package com.tms.server.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "terminal_groups")
public class TerminalGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    private String description;

    /** Modèle appliqué automatiquement aux terminaux qui rejoignent ce groupe (zéro contact). */
    @ManyToOne
    @JoinColumn(name = "template_id")
    private DeploymentTemplate template;

    public DeploymentTemplate getTemplate() { return template; }
    public void setTemplate(DeploymentTemplate template) { this.template = template; }
    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
