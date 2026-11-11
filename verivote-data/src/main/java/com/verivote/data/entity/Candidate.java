package com.verivote.data.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "candidate")
public class Candidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "election_id", nullable = false)
    private Long electionId;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "party", length = 255)
    private String party;

    @Column(name = "constituency", length = 255)
    private String constituency;

    @Column(name = "symbol", length = 255)
    private String symbol;

    public Candidate() {}

    public Candidate(Long electionId, String name, String party) {
        this.electionId = electionId;
        this.name = name;
        this.party = party;
    }

    public Candidate(Long electionId, String name, String party, String constituency, String symbol) {
        this.electionId = electionId;
        this.name = name;
        this.party = party;
        this.constituency = constituency;
        this.symbol = symbol;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getParty() {
        return party;
    }

    public void setParty(String party) {
        this.party = party;
    }

    public String getConstituency() {
        return constituency;
    }

    public void setConstituency(String constituency) {
        this.constituency = constituency;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }
}
