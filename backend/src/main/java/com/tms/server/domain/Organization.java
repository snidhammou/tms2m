package com.tms.server.domain;

import jakarta.persistence.*;

/**
 * Nœud de la hiérarchie d'organisations (ex. acquéreur > région > agence).
 * Les marchands sont rattachés à une organisation ; filtrer sur une organisation
 * inclut toutes ses sous-organisations.
 */
@Entity
@Table(name = "organizations")
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @ManyToOne
    @JoinColumn(name = "parent_id")
    private Organization parent;

    private String description;

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Organization getParent() { return parent; }
    public void setParent(Organization parent) { this.parent = parent; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
