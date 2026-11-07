package com.verivote.data.repo;

import com.verivote.data.entity.RecoveryEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RecoveryEventRepo extends JpaRepository<RecoveryEvent, Long> {
    Optional<RecoveryEvent> findByBlockHeight(Long blockHeight);
    boolean existsByBlockHeight(Long blockHeight);
    List<RecoveryEvent> findAllByOrderByBlockHeightAsc();
    default List<RecoveryEvent> findAllOrderByBlockHeightAsc() {
        return findAllByOrderByBlockHeightAsc();
    }
}
