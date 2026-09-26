package com.tms.server.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Modèle de déploiement "zéro contact" : ce qu'un terminal doit recevoir dès qu'il
 * rejoint un groupe lié à ce modèle (enrôlement ou affectation au groupe).
 */
@Entity
@Table(name = "deployment_templates")
public class DeploymentTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 120)
    private String name;

    private String description;

    /** Ids des APK à installer (liste JSON). */
    @Column(columnDefinition = "text")
    private String appIdsJson;

    /** Ids des modèles de paramètres à appliquer au terminal (liste JSON). */
    @Column(columnDefinition = "text")
    private String parameterTemplateIdsJson;

    /** Package lancé automatiquement au démarrage (null = aucun). */
    private String autoRunPackage;

    /** Packages autorisés en mode kiosque (liste JSON, vide = kiosque désactivé). */
    @Column(columnDefinition = "text")
    private String kioskPackagesJson;

    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getAppIdsJson() { return appIdsJson; }
    public void setAppIdsJson(String appIdsJson) { this.appIdsJson = appIdsJson; }
    public String getParameterTemplateIdsJson() { return parameterTemplateIdsJson; }
    public void setParameterTemplateIdsJson(String json) { this.parameterTemplateIdsJson = json; }
    public String getAutoRunPackage() { return autoRunPackage; }
    public void setAutoRunPackage(String autoRunPackage) { this.autoRunPackage = autoRunPackage; }
    public String getKioskPackagesJson() { return kioskPackagesJson; }
    public void setKioskPackagesJson(String kioskPackagesJson) { this.kioskPackagesJson = kioskPackagesJson; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
