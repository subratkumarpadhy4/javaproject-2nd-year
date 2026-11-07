package com.verivote.data.repo;

import com.verivote.data.entity.SpentToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpentTokenRepo extends JpaRepository<SpentToken, String> {
}
