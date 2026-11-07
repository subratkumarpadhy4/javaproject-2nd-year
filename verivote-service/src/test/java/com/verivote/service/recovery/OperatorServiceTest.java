package com.verivote.service.recovery;

import com.verivote.data.entity.Operator;
import com.verivote.data.repo.OperatorRepo;
import com.verivote.service.ServiceTestApplication;
import com.verivote.service.checkpoint.CheckpointSigner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.security.KeyPair;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = ServiceTestApplication.class)
@TestPropertySource(locations = "classpath:application-service.properties")
class OperatorServiceTest {

    @Autowired
    private OperatorRepo operatorRepo;

    @Autowired
    private OperatorService operatorService;

    @BeforeEach
    void setUp() {
        operatorRepo.deleteAll();
    }

    private String samplePub() {
        KeyPair kp = CheckpointSigner.generateKeyPair();
        return CheckpointSigner.encodePublicKey(kp);
    }

    @Test
    @DisplayName("1. enroll -> findActive returns present")
    void enrollAndFindActive() {
        Operator op = operatorService.enroll("op-1", samplePub(), "AUDIT", "admin");
        assertNotNull(op);
        assertEquals("op-1", op.getName());

        Optional<Operator> active = operatorService.findActive("op-1");
        assertTrue(active.isPresent());
        assertEquals(Operator.Role.AUDIT, active.get().getRole());
    }

    @Test
    @DisplayName("2. enroll duplicate name -> IllegalStateException")
    void enrollDuplicateThrows() {
        String pub = samplePub();
        operatorService.enroll("op-dup", pub, "AUDIT", "admin");
        assertThrows(IllegalStateException.class, () ->
                operatorService.enroll("op-dup", pub, "AUDIT", "admin"));
    }

    @Test
    @DisplayName("3. verifyActiveAudit -> true for active AUDIT")
    void verifyActiveAuditTrue() {
        operatorService.enroll("op-audit", samplePub(), "AUDIT", "admin");
        assertTrue(operatorService.verifyActiveAudit("op-audit"));

        operatorService.enroll("op-admin", samplePub(), "ADMIN", "admin");
        assertFalse(operatorService.verifyActiveAudit("op-admin"));
    }

    @Test
    @DisplayName("4. deactivate -> verifyActiveAudit -> false")
    void deactivateOperator() {
        operatorService.enroll("op-deact", samplePub(), "AUDIT", "admin");
        assertTrue(operatorService.verifyActiveAudit("op-deact"));

        operatorService.deactivate("op-deact");
        assertFalse(operatorService.verifyActiveAudit("op-deact"));
        assertTrue(operatorService.findActive("op-deact").isEmpty());
    }
}
