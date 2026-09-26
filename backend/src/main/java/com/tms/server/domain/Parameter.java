package com.tms.server.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Paramètre applicatif (clé/valeur) pour un package donné, défini à un niveau
 * GLOBAL, GROUP (scopeRef = id du groupe) ou TERMINAL (scopeRef = id du terminal).
 */
@Entity
@Table(name = "parameters", uniqueConstraints = {
        @UniqueConstraint(name = "uk_param", columnNames = {"scope", "scopeRef", "packageName", "paramKey"})
})
public class Parameter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ParameterScope scope;

    private Long scopeRef;

    @Column(nullable = false)
    private String packageName;

    @Column(nullable = false, length = 128)
    private String paramKey;

    @Column(columnDefinition = "text")
    private String paramValue;

    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public ParameterScope getScope() { return scope; }
    public void setScope(ParameterScope scope) { this.scope = scope; }
    public Long getScopeRef() { return scopeRef; }
    public void setScopeRef(Long scopeRef) { this.scopeRef = scopeRef; }
    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }
    public String getParamKey() { return paramKey; }
    public void setParamKey(String paramKey) { this.paramKey = paramKey; }
    public String getParamValue() { return paramValue; }
    public void setParamValue(String paramValue) { this.paramValue = paramValue; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
