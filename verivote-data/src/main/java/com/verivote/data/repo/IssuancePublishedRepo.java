package com.verivote.data.repo;

import com.verivote.data.entity.IssuancePublished;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IssuancePublishedRepo extends JpaRepository<IssuancePublished, Long> {
    List<IssuancePublished> findByElectionId(Long electionId);
    Optional<IssuancePublished> findFirstByElectionIdOrderByPublishedAtDesc(Long electionId);
}
