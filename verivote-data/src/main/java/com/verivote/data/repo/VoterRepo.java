package com.verivote.data.repo;

import com.verivote.data.entity.Voter;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VoterRepo extends JpaRepository<Voter, Long> {
    Optional<Voter> findByNationalIdHmac(String nationalIdHmac);
    Optional<Voter> findByVoterId(String voterId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Voter v WHERE v.id = :id")
    Optional<Voter> findByForUpdate(@Param("id") Long id);
}
