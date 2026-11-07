package com.verivote.service;

import com.verivote.data.entity.Voter;
import com.verivote.data.repo.VoterRepo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = ServiceTestApplication.class)
@TestPropertySource(locations = "classpath:application-service.properties")
class VoterServiceTest {

    @Autowired
    private VoterService voterService;

    @Autowired
    private VoterRepo voterRepo;

    @Test
    @DisplayName("1. register -> non-empty secret")
    void registerNonEmptySecret() {
        byte[] secret = voterService.register("NAT-001", "Alice Smith", "P@ssword123!");
        assertNotNull(secret);
        assertEquals(20, secret.length);
    }

    @Test
    @DisplayName("2. duplicate register -> IllegalStateException")
    void duplicateRegister() {
        voterService.register("NAT-002", "Bob Jones", "P@ssword123!");
        assertThrows(IllegalStateException.class, () ->
                voterService.register("NAT-002", "Bob Duplicate", "AnotherPass!"));
    }

    @Test
    @DisplayName("3. verifyPassword correct -> returns id")
    void verifyPasswordCorrect() {
        voterService.register("NAT-003", "Charlie Brown", "CharlieSecret1!");
        Long id = voterService.verifyPassword("NAT-003", "CharlieSecret1!");
        assertNotNull(id);
        assertTrue(id > 0);
    }

    @Test
    @DisplayName("4 & 5. verifyPassword wrong vs unknown nationalId: both throw IllegalArgumentException with EXACT same message")
    void verifyPasswordErrorMessageEquality() {
        voterService.register("NAT-004", "Diana Prince", "ValidDianaPass1!");

        // Case 4: wrong password
        IllegalArgumentException wrongPassEx = assertThrows(IllegalArgumentException.class, () ->
                voterService.verifyPassword("NAT-004", "WrongPass123!"));

        // Case 5: unknown nationalId
        IllegalArgumentException unknownUserEx = assertThrows(IllegalArgumentException.class, () ->
                voterService.verifyPassword("NAT-UNKNOWN-999", "AnyPassword!"));

        assertEquals(wrongPassEx.getMessage(), unknownUserEx.getMessage(),
                "Error message must be identical to prevent user enumeration");
        assertEquals("invalid credentials", wrongPassEx.getMessage());
    }

    @Test
    @DisplayName("6. HMAC deterministic: same nationalId -> same hmac (via repo)")
    void hmacDeterministic() {
        String nid = "NAT-005";
        voterService.register(nid, "Eve Adams", "EvePass123!");

        String computedHmac = voterService.computeNationalIdHmac(nid);
        Voter voter = voterRepo.findByNationalIdHmac(computedHmac).orElseThrow();
        assertEquals(computedHmac, voter.getNationalIdHmac());
    }
}
