package com.verivote.data.repo;

import com.verivote.data.entity.Election;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ElectionRepo extends JpaRepository<Election, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Election e WHERE e.id = :id")
    Optional<Election> findByIdForUpdate(@Param("id") Long id);
}
