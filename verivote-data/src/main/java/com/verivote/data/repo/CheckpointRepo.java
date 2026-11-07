package com.verivote.data.repo;

import com.verivote.data.entity.Checkpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CheckpointRepo extends JpaRepository<Checkpoint, Long> {
}
