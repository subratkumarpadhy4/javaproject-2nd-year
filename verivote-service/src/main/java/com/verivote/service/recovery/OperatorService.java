package com.verivote.service.recovery;

import com.verivote.data.entity.Operator;
import com.verivote.data.repo.OperatorRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class OperatorService {

    private final OperatorRepo operatorRepo;

    @Autowired
    public OperatorService(OperatorRepo operatorRepo) {
        this.operatorRepo = operatorRepo;
    }

    public Operator enroll(String name, String ed25519PubB64, String role, String createdBy) {
        if (operatorRepo.findByName(name).isPresent()) {
            throw new IllegalStateException("Operator already enrolled: " + name);
        }
        Operator.Role roleEnum = Operator.Role.valueOf(role.trim().toUpperCase());
        Operator op = new Operator(name, ed25519PubB64, roleEnum, Instant.now(), createdBy);
        return operatorRepo.saveAndFlush(op);
    }

    public Optional<Operator> findByName(String name) {
        return operatorRepo.findByName(name);
    }

    public Optional<Operator> findActive(String name) {
        return operatorRepo.findByName(name).filter(this::isActive);
    }

    public boolean verifyActiveAudit(String name) {
        return findActive(name)
                .map(op -> op.getRole() == Operator.Role.AUDIT)
                .orElse(false);
    }

    public void deactivate(String name) {
        operatorRepo.findByName(name).ifPresent(op -> {
            op.setActiveTo(Instant.now().minusSeconds(1));
            operatorRepo.saveAndFlush(op);
        });
    }

    private boolean isActive(Operator op) {
        Instant now = Instant.now();
        if (op.getActiveFrom() == null || op.getActiveFrom().isAfter(now)) {
            return false;
        }
        return op.getActiveTo() == null || op.getActiveTo().isAfter(now);
    }
}
