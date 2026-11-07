package com.verivote.data.repo;

import com.verivote.data.entity.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CandidateRepo extends JpaRepository<Candidate, Long> {
    List<Candidate> findByElectionId(Long electionId);
}
