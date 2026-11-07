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
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * Audit log of recovery actions for ledger discrepancies.
 * Unique constraint on block_height prevents race conditions (Fix F2).
 */
@Entity
@Table(
    name = "recovery_event",
    uniqueConstraints = @UniqueConstraint(name = "uk_recovery_block", columnNames = {"block_height"})
)
public class RecoveryEvent {

    public enum Action {
        ACCEPT_AS_ORPHAN, RECOVER_FROM_LEDGER
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Unique constraint on block_height enforced via @Column(unique=true) and @Table(uniqueConstraints=...)
    @Column(name = "block_height", nullable = false, unique = true)
    private Long blockHeight;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private Action action;

    @Column(name = "block_hash", length = 64, nullable = false)
    private String blockHash;

    @Column(name = "token_hash", length = 64, nullable = false)
    private String tokenHash;

    @Column(name = "reason", length = 500, nullable = false)
    private String reason;

    @Column(name = "operator1", length = 128, nullable = false)
    private String operator1;

    @Column(name = "signature1", columnDefinition = "TEXT", nullable = false)
    private String signature1;

    @Column(name = "operator2", length = 128)
    private String operator2;

    @Column(name = "signature2", columnDefinition = "TEXT")
    private String signature2;

    @Column(name = "created_at")
    private Instant createdAt;

    public RecoveryEvent() {}

    public RecoveryEvent(Long blockHeight, Action action, String blockHash, String tokenHash,
                         String reason, String operator1, String signature1,
                         String operator2, String signature2) {
        this.blockHeight = blockHeight;
        this.action = action;
        this.blockHash = blockHash;
        this.tokenHash = tokenHash;
        this.reason = reason;
        this.operator1 = operator1;
        this.signature1 = signature1;
        this.operator2 = operator2;
        this.signature2 = signature2;
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

    public Long getBlockHeight() {
        return blockHeight;
    }

    public void setBlockHeight(Long blockHeight) {
        this.blockHeight = blockHeight;
    }

    public Action getAction() {
        return action;
    }

    public void setAction(Action action) {
        this.action = action;
    }

    public String getBlockHash() {
        return blockHash;
    }

    public void setBlockHash(String blockHash) {
        this.blockHash = blockHash;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getOperator1() {
        return operator1;
    }

    public void setOperator1(String operator1) {
        this.operator1 = operator1;
    }

    public String getSignature1() {
        return signature1;
    }

    public void setSignature1(String signature1) {
        this.signature1 = signature1;
    }

    public String getOperator2() {
        return operator2;
    }

    public void setOperator2(String operator2) {
        this.operator2 = operator2;
    }

    public String getSignature2() {
        return signature2;
    }

    public void setSignature2(String signature2) {
        this.signature2 = signature2;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
