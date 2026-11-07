package com.verivote.data.repo;

import com.verivote.data.entity.Vote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VoteRepo extends JpaRepository<Vote, Long> {
    Optional<Vote> findByReceiptHash(String receiptHash);
    Optional<Vote> findByBlockHeight(Long blockHeight);
}
