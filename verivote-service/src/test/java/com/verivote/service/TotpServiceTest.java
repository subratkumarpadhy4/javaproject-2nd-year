package com.verivote.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class TotpServiceTest {

    @Test
    @DisplayName("1. generate + verify at same time -> true")
    void generateAndVerifySameTime() {
        byte[] secret = new byte[20];
        new SecureRandom().nextBytes(secret);

        int code = TotpService.generateTotp(secret);
        assertTrue(TotpService.verify(secret, code, 0));
    }

    @Test
    @DisplayName("2. verify within ±1 window -> true")
    void verifyWithinWindow() {
        byte[] secret = new byte[20];
        new SecureRandom().nextBytes(secret);

        long nowSec = System.currentTimeMillis() / 1000L;
        int codePrev = TotpService.generateTotp(secret, nowSec - 30);
        int codeNext = TotpService.generateTotp(secret, nowSec + 30);

        assertTrue(TotpService.verify(secret, codePrev, 1));
        assertTrue(TotpService.verify(secret, codeNext, 1));
    }

    @Test
    @DisplayName("3. verify outside window (T+90s) -> false")
    void verifyOutsideWindow() {
        byte[] secret = new byte[20];
        new SecureRandom().nextBytes(secret);

        long nowSec = System.currentTimeMillis() / 1000L;
        int codeFuture = TotpService.generateTotp(secret, nowSec + 90);

        assertFalse(TotpService.verify(secret, codeFuture, 1));
    }

    @Test
    @DisplayName("4. wrong code -> false")
    void wrongCode() {
        byte[] secret = new byte[20];
        new SecureRandom().nextBytes(secret);

        int actual = TotpService.generateTotp(secret);
        int wrong = (actual + 1) % 1_000_000;

        assertFalse(TotpService.verify(secret, wrong, 1));
    }

    @Test
    @DisplayName("5. base32 round-trip")
    void base32RoundTrip() {
        byte[] original = "VeriVote-J TOTP Secret Bytes".getBytes(StandardCharsets.UTF_8);
        String encoded = TotpService.base32Encode(original);
        assertNotNull(encoded);
        byte[] decoded = TotpService.base32Decode(encoded);

        assertArrayEquals(original, decoded);
    }

    @Test
    @DisplayName("6. RFC 6238 test vector: secret=\"12345678901234567890\", at T=59, 6-digit code = 287082")
    void rfc6238TestVector() {
        byte[] secret = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
        int code = TotpService.generateTotp(secret, 59L);
        assertEquals(287082, code);
    }
}
