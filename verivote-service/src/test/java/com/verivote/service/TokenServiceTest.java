package com.verivote.service;

import com.verivote.core.crypto.BlindSignatureService;
import com.verivote.core.crypto.BlindSignatureService.KeyPair;
import com.verivote.core.encoding.Base64Codec;
import com.verivote.data.entity.Voter;
import com.verivote.data.repo.VoterRepo;
import com.verivote.service.dto.TokenIssuanceResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = ServiceTestApplication.class)
@TestPropertySource(locations = "classpath:application-service.properties")
class TokenServiceTest {

    @Autowired
    private TokenService tokenService;

    @Autowired
    private VoterRepo voterRepo;

    private Long createVoter(String suffix) {
        Voter voter = new Voter("nid-tok-" + suffix, "Voter " + suffix, "hash", new byte[]{1, 2, 3});
        voter = voterRepo.saveAndFlush(voter);
        return voter.getId();
    }

    @Test
    @DisplayName("1. issue -> signature returned, issued=1")
    void issueFirstToken() {
        Long voterId = createVoter("t1");
        byte[] blinded = new byte[256];
        blinded[0] = 0x01;

        TokenIssuanceResponse resp = tokenService.issueToken(voterId, blinded);
        assertNotNull(resp);
        assertNotNull(resp.signature());
        assertTrue(resp.issuanceCount() >= 1L);
    }

    @Test
    @DisplayName("2. issue again same voter -> IllegalStateException 'already voted'")
    void issueAgainSameVoter() {
        Long voterId = createVoter("t2");
        byte[] blinded = new byte[256];
        blinded[0] = 0x02;

        tokenService.issueToken(voterId, blinded);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                tokenService.issueToken(voterId, blinded));
        assertTrue(ex.getMessage().contains("already voted"));
    }

    @Test
    @DisplayName("3. issue to fresh voter -> issued increments")
    void issueFreshVoter() {
        Long v1 = createVoter("t3_1");
        Long v2 = createVoter("t3_2");
        byte[] blinded = new byte[256];
        blinded[0] = 0x03;

        TokenIssuanceResponse r1 = tokenService.issueToken(v1, blinded);
        TokenIssuanceResponse r2 = tokenService.issueToken(v2, blinded);

        assertEquals(r1.issuanceCount() + 1, r2.issuanceCount());
    }

    @Test
    @DisplayName("4. Concurrent 5 threads same voter: exactly 1 succeeds, 4 throw IllegalStateException")
    void concurrentSameVoter() throws InterruptedException {
        Long voterId = createVoter("t4");
        byte[] blinded = new byte[256];
        blinded[0] = 0x04;

        int threads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    tokenService.issueToken(voterId, blinded);
                    successes.incrementAndGet();
                } catch (IllegalStateException e) {
                    failures.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        done.await();
        executor.shutdown();

        assertEquals(1, successes.get(), "Exactly one thread must succeed");
        assertEquals(4, failures.get(), "Exactly 4 threads must fail with already voted");
    }

    @Test
    @DisplayName("5. Full blind-sig round-trip: blind -> issue -> unblind -> verify == true")
    void fullBlindSigRoundTrip() {
        Long voterId = createVoter("t5");
        KeyPair kp = tokenService.getElectionKeyPair();
        assertNotNull(kp);

        byte[] msg = "VERIVOTE-TOKEN-v1-msg".getBytes(StandardCharsets.UTF_8);
        SecureRandom rnd = new SecureRandom();
        BigInteger[] blindingFactor = new BigInteger[1];

        byte[] blinded = BlindSignatureService.blind(msg, kp, rnd, blindingFactor);
        TokenIssuanceResponse resp = tokenService.issueToken(voterId, blinded);

        byte[] sigBytes = Base64Codec.decode(resp.signature());
        byte[] unblinded = BlindSignatureService.unblind(sigBytes, blindingFactor[0].toByteArray(), kp, 256);

        assertTrue(BlindSignatureService.verify(msg, unblinded, kp),
                "Unblinded token signature must verify against election public key");
    }
}
