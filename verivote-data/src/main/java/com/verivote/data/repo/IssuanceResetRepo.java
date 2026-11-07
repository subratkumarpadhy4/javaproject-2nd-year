package com.verivote.data.repo;

import com.verivote.data.entity.IssuanceReset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IssuanceResetRepo extends JpaRepository<IssuanceReset, Long> {
}
