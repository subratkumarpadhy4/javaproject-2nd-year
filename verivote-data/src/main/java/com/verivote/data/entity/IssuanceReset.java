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
@Table(name = "issuance_reset")
public class IssuanceReset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "voter_id_hmac", length = 128, nullable = false)
    private String voterIdHmac;

    @Column(name = "reset_by", length = 128, nullable = false)
    private String resetBy;

    @Column(name = "reason", columnDefinition = "TEXT", nullable = false)
    private String reason;

    @Column(name = "signature", columnDefinition = "TEXT", nullable = false)
    private String signature;

    @Column(name = "created_at")
    private Instant createdAt;

    public IssuanceReset() {}

    public IssuanceReset(String voterIdHmac, String resetBy, String reason, String signature) {
        this.voterIdHmac = voterIdHmac;
        this.resetBy = resetBy;
        this.reason = reason;
        this.signature = signature;
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

    public String getVoterIdHmac() {
        return voterIdHmac;
    }

    public void setVoterIdHmac(String voterIdHmac) {
        this.voterIdHmac = voterIdHmac;
    }

    public String getResetBy() {
        return resetBy;
    }

    public void setResetBy(String resetBy) {
        this.resetBy = resetBy;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
