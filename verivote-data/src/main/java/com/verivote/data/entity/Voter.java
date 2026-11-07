package com.verivote.data.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "voter")
public class Voter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "national_id_hmac", length = 128, unique = true, nullable = false)
    private String nationalIdHmac;

    @Column(name = "display_name", length = 255, nullable = false)
    private String displayName;

    @Column(name = "password_hash", length = 255, nullable = false)
    private String passwordHash;

    @Column(name = "totp_secret", length = 256)
    private byte[] totpSecret;

    @Column(name = "has_voted", nullable = false)
    private boolean hasVoted = false;

    @Column(name = "created_at")
    private Instant createdAt;

    public Voter() {}

    public Voter(String nationalIdHmac, String displayName, String passwordHash, byte[] totpSecret) {
        this.nationalIdHmac = nationalIdHmac;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.totpSecret = totpSecret;
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

    public String getNationalIdHmac() {
        return nationalIdHmac;
    }

    public void setNationalIdHmac(String nationalIdHmac) {
        this.nationalIdHmac = nationalIdHmac;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public byte[] getTotpSecret() {
        return totpSecret;
    }

    public void setTotpSecret(byte[] totpSecret) {
        this.totpSecret = totpSecret;
    }

    public boolean isHasVoted() {
        return hasVoted;
    }

    public void setHasVoted(boolean hasVoted) {
        this.hasVoted = hasVoted;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
