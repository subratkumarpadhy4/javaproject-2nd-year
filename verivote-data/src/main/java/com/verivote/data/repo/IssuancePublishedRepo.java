package com.verivote.data.repo;

import com.verivote.data.entity.IssuancePublished;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IssuancePublishedRepo extends JpaRepository<IssuancePublished, Long> {
    List<IssuancePublished> findByElectionId(Long electionId);
}
