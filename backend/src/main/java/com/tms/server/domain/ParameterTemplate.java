package com.tms.server.domain;

import jakarta.persistence.*;

import java.time.Instant;

/** Jeu de paramètres nommé et réutilisable pour une application donnée. */
@Entity
@Table(name = "parameter_templates")
public class ParameterTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 120)
    private String name;

    @Column(nullable = false)
    private String packageName;

    /** Valeurs clé/valeur (objet JSON). */
    @Column(columnDefinition = "text")
    private String valuesJson;

    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }
    public String getValuesJson() { return valuesJson; }
    public void setValuesJson(String valuesJson) { this.valuesJson = valuesJson; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
