package com.verivote.data.repo;

import com.verivote.data.entity.Operator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OperatorRepo extends JpaRepository<Operator, Long> {
    Optional<Operator> findByName(String name);
}
