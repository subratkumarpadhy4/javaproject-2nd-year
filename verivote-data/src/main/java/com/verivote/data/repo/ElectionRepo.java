package com.verivote.data.repo;

import com.verivote.data.entity.Election;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ElectionRepo extends JpaRepository<Election, Long> {
}
