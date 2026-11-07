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
@Table(name = "issuance_published")
public class IssuancePublished {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "election_id", nullable = false)
    private Long electionId;

    @Column(name = "issued", nullable = false)
    private long issued;

    @Column(name = "resets", nullable = false)
    private long resets;

    @Column(name = "votes_cast", nullable = false)
    private long votesCast;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "signature", columnDefinition = "TEXT", nullable = false)
    private String signature;

    public IssuancePublished() {}

    public IssuancePublished(Long electionId, long issued, long resets, long votesCast, String signature) {
        this.electionId = electionId;
        this.issued = issued;
        this.resets = resets;
        this.votesCast = votesCast;
        this.signature = signature;
    }

    @PrePersist
    public void prePersist() {
        if (publishedAt == null) {
            publishedAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getElectionId() {
        return electionId;
    }

    public void setElectionId(Long electionId) {
        this.electionId = electionId;
    }

    public long getIssued() {
        return issued;
    }

    public void setIssued(long issued) {
        this.issued = issued;
    }

    public long getResets() {
        return resets;
    }

    public void setResets(long resets) {
        this.resets = resets;
    }

    public long getVotesCast() {
        return votesCast;
    }

    public void setVotesCast(long votesCast) {
        this.votesCast = votesCast;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }
}
