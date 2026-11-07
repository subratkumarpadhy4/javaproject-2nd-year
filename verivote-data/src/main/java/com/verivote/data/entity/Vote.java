package com.verivote.data.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Represents a cast vote.
 * Anonymity boundary: MUST NOT have voter references, voter IDs, client nonce, or timestamps.
 */
@Entity
@Table(name = "vote")
public class Vote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "election_id", nullable = false)
    private Long electionId;

    @Column(name = "token_hash", length = 64, nullable = false)
    private String tokenHash;

    @Column(name = "receipt_hash", length = 64, unique = true, nullable = false)
    private String receiptHash;

    @Column(name = "encrypted_vote", columnDefinition = "TEXT", nullable = false)
    private String encryptedVote;

    @Column(name = "block_height", nullable = false)
    private Long blockHeight;

    @Column(name = "block_hash", length = 64, nullable = false)
    private String blockHash;

    public Vote() {}

    public Vote(Long electionId, String tokenHash, String receiptHash, String encryptedVote, Long blockHeight, String blockHash) {
        this.electionId = electionId;
        this.tokenHash = tokenHash;
        this.receiptHash = receiptHash;
        this.encryptedVote = encryptedVote;
        this.blockHeight = blockHeight;
        this.blockHash = blockHash;
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

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public String getReceiptHash() {
        return receiptHash;
    }

    public void setReceiptHash(String receiptHash) {
        this.receiptHash = receiptHash;
    }

    public String getEncryptedVote() {
        return encryptedVote;
    }

    public void setEncryptedVote(String encryptedVote) {
        this.encryptedVote = encryptedVote;
    }

    public Long getBlockHeight() {
        return blockHeight;
    }

    public void setBlockHeight(Long blockHeight) {
        this.blockHeight = blockHeight;
    }

    public String getBlockHash() {
        return blockHash;
    }

    public void setBlockHash(String blockHash) {
        this.blockHash = blockHash;
    }
}
