package com.verivote.data.repo;

import com.verivote.data.entity.IssuanceCounter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IssuanceCounterRepo extends JpaRepository<IssuanceCounter, Long> {
    Optional<IssuanceCounter> findByElectionId(Long electionId);
}
