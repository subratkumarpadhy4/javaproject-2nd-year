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
@Table(name = "checkpoint")
public class Checkpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tree_size", nullable = false)
    private int treeSize;

    @Column(name = "merkle_root", length = 64, nullable = false)
    private String merkleRoot;

    @Column(name = "head_hash", length = 64, nullable = false)
    private String headHash;

    @Column(name = "signature", columnDefinition = "TEXT", nullable = false)
    private String signature;

    @Column(name = "created_at")
    private Instant createdAt;

    public Checkpoint() {}

    public Checkpoint(int treeSize, String merkleRoot, String headHash, String signature) {
        this.treeSize = treeSize;
        this.merkleRoot = merkleRoot;
        this.headHash = headHash;
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

    public int getTreeSize() {
        return treeSize;
    }

    public void setTreeSize(int treeSize) {
        this.treeSize = treeSize;
    }

    public String getMerkleRoot() {
        return merkleRoot;
    }

    public void setMerkleRoot(String merkleRoot) {
        this.merkleRoot = merkleRoot;
    }

    public String getHeadHash() {
        return headHash;
    }

    public void setHeadHash(String headHash) {
        this.headHash = headHash;
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
