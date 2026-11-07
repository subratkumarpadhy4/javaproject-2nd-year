package com.verivote.data.repo;

import com.verivote.data.entity.Voter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VoterRepo extends JpaRepository<Voter, Long> {
    Optional<Voter> findByNationalIdHmac(String nationalIdHmac);
}
