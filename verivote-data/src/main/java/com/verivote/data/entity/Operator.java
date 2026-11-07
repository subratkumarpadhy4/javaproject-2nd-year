package com.verivote.data.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "operator")
public class Operator {

    public enum Role {
        ADMIN, AUDIT, TALLY
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", length = 128, unique = true, nullable = false)
    private String name;

    @Column(name = "ed25519_pub", length = 128, nullable = false)
    private String ed25519Pub;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    @Column(name = "active_from", nullable = false)
    private Instant activeFrom;

    @Column(name = "active_to")
    private Instant activeTo;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "created_by", length = 128, nullable = false)
    private String createdBy;

    public Operator() {}

    public Operator(String name, String ed25519Pub, Role role, Instant activeFrom, String createdBy) {
        this.name = name;
        this.ed25519Pub = ed25519Pub;
        this.role = role;
        this.activeFrom = activeFrom;
        this.createdBy = createdBy;
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEd25519Pub() {
        return ed25519Pub;
    }

    public void setEd25519Pub(String ed25519Pub) {
        this.ed25519Pub = ed25519Pub;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public Instant getActiveFrom() {
        return activeFrom;
    }

    public void setActiveFrom(Instant activeFrom) {
        this.activeFrom = activeFrom;
    }

    public Instant getActiveTo() {
        return activeTo;
    }

    public void setActiveTo(Instant activeTo) {
        this.activeTo = activeTo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
}
