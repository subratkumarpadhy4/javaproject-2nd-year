package com.verivote.data.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Represents a spent voting token.
 * Anonymity boundary: MUST NOT have voter references, voter IDs, or timestamps.
 */
@Entity
@Table(name = "spent_token")
public class SpentToken {

    @Id
    @Column(name = "token_hash", length = 64, nullable = false)
    private String tokenHash;

    @Column(name = "election_id", nullable = false)
    private Long electionId;

    public SpentToken() {}

    public SpentToken(String tokenHash, Long electionId) {
        this.tokenHash = tokenHash;
        this.electionId = electionId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public Long getElectionId() {
        return electionId;
    }

    public void setElectionId(Long electionId) {
        this.electionId = electionId;
    }
}
