package com.verivote.data.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "issuance_counter")
public class IssuanceCounter {

    @Id
    @Column(name = "election_id")
    private Long electionId;

    @Column(name = "issued", nullable = false)
    private long issued = 0L;

    @Column(name = "resets", nullable = false)
    private long resets = 0L;

    public IssuanceCounter() {}

    public IssuanceCounter(Long electionId, long issued, long resets) {
        this.electionId = electionId;
        this.issued = issued;
        this.resets = resets;
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
}
