package com.verivote.core;

import com.verivote.core.crypto.BlindSignatureService;
import com.verivote.core.crypto.BlindSignatureService.KeyPair;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlindSignatureServiceTest {

    @Test
    @DisplayName("1. Round-trip success: blind -> sign -> unblind -> verify succeeds")
    void roundTripSuccess() {
        KeyPair kp = BlindSignatureService.generateKeyPair();
        byte[] msg = "test message".getBytes(StandardCharsets.UTF_8);
        BigInteger[] blinding = new BigInteger[1];
        byte[] blinded = BlindSignatureService.blind(msg, kp, new SecureRandom(), blinding);
        byte[] signature = BlindSignatureService.sign(blinded, kp);
        byte[] unblinded = BlindSignatureService.unblind(signature, blinding[0].toByteArray(), kp, 256);
        assertTrue(BlindSignatureService.verify(msg, unblinded, kp));
    }

    @Test
    @DisplayName("2. Wrong message fails verify")
    void wrongMessageFailsVerify() {
        KeyPair kp = BlindSignatureService.generateKeyPair();
        byte[] msg = "test message".getBytes(StandardCharsets.UTF_8);
        BigInteger[] blinding = new BigInteger[1];
        byte[] blinded = BlindSignatureService.blind(msg, kp, new SecureRandom(), blinding);
        byte[] signature = BlindSignatureService.sign(blinded, kp);
        byte[] unblinded = BlindSignatureService.unblind(signature, blinding[0].toByteArray(), kp, 256);

        assertFalse(BlindSignatureService.verify("other message".getBytes(StandardCharsets.UTF_8), unblinded, kp));
    }

    @Test
    @DisplayName("3. Wrong key fails verify")
    void wrongKeyFailsVerify() {
        KeyPair kp = BlindSignatureService.generateKeyPair();
        byte[] msg = "test message".getBytes(StandardCharsets.UTF_8);
        BigInteger[] blinding = new BigInteger[1];
        byte[] blinded = BlindSignatureService.blind(msg, kp, new SecureRandom(), blinding);
        byte[] signature = BlindSignatureService.sign(blinded, kp);
        byte[] unblinded = BlindSignatureService.unblind(signature, blinding[0].toByteArray(), kp, 256);

        KeyPair kp2 = BlindSignatureService.generateKeyPair();
        assertFalse(BlindSignatureService.verify(msg, unblinded, kp2));
    }

    @Test
    @DisplayName("4. Tampered signature fails verify")
    void tamperedSignatureFailsVerify() {
        KeyPair kp = BlindSignatureService.generateKeyPair();
        byte[] msg = "test message".getBytes(StandardCharsets.UTF_8);
        BigInteger[] blinding = new BigInteger[1];
        byte[] blinded = BlindSignatureService.blind(msg, kp, new SecureRandom(), blinding);
        byte[] signature = BlindSignatureService.sign(blinded, kp);
        byte[] unblinded = BlindSignatureService.unblind(signature, blinding[0].toByteArray(), kp, 256);

        unblinded[0] ^= 0x01;
        assertFalse(BlindSignatureService.verify(msg, unblinded, kp));
    }

    @Test
    @DisplayName("5. Leading-zero unblind edge case: 2000 iterations to verify left-padding")
    void leadingZeroUnblindEdgeCase() {
        KeyPair kp = BlindSignatureService.generateKeyPair();
        SecureRandom rnd = new SecureRandom();

        for (int i = 0; i < 2000; i++) {
            byte[] tokenPlain = new byte[32];
            rnd.nextBytes(tokenPlain);
            byte[] msg = tokenPlain;

            BigInteger[] blinding = new BigInteger[1];
            byte[] blinded = BlindSignatureService.blind(msg, kp, rnd, blinding);
            byte[] signature = BlindSignatureService.sign(blinded, kp);
            byte[] unblinded = BlindSignatureService.unblind(signature, blinding[0].toByteArray(), kp, 256);

            assertEquals(256, unblinded.length, "Unblinded signature must always be 256 bytes");
            assertTrue(BlindSignatureService.verify(msg, unblinded, kp), "Verify must succeed on iteration " + i);
        }
    }

    @Test
    @DisplayName("6. Blinded message length is 256")
    void blindedMessageLength() {
        KeyPair kp = BlindSignatureService.generateKeyPair();
        byte[] msg = "test message".getBytes(StandardCharsets.UTF_8);
        BigInteger[] blinding = new BigInteger[1];
        byte[] blinded = BlindSignatureService.blind(msg, kp, new SecureRandom(), blinding);
        assertEquals(256, blinded.length);
    }

    @Test
    @DisplayName("7. PSS is probabilistic: blinding and signing the same message twice produces different signatures")
    void pssIsProbabilistic() {
        KeyPair kp = BlindSignatureService.generateKeyPair();
        byte[] msg = "test message".getBytes(StandardCharsets.UTF_8);
        SecureRandom rnd = new SecureRandom();

        BigInteger[] bf1 = new BigInteger[1];
        byte[] blinded1 = BlindSignatureService.blind(msg, kp, rnd, bf1);
        byte[] sig1 = BlindSignatureService.sign(blinded1, kp);
        byte[] unblinded1 = BlindSignatureService.unblind(sig1, bf1[0].toByteArray(), kp, 256);

        BigInteger[] bf2 = new BigInteger[1];
        byte[] blinded2 = BlindSignatureService.blind(msg, kp, rnd, bf2);
        byte[] sig2 = BlindSignatureService.sign(blinded2, kp);
        byte[] unblinded2 = BlindSignatureService.unblind(sig2, bf2[0].toByteArray(), kp, 256);

        assertFalse(Arrays.equals(blinded1, blinded2), "Blinded messages should differ due to PSS salt and blinding factor");
        assertFalse(Arrays.equals(sig1, sig2), "Blind signatures should differ");
        assertFalse(Arrays.equals(unblinded1, unblinded2), "Unblinded signatures should differ");
        assertTrue(BlindSignatureService.verify(msg, unblinded1, kp));
        assertTrue(BlindSignatureService.verify(msg, unblinded2, kp));
    }

    @Test
    @DisplayName("8. Signature is modLen (256)")
    void signatureIsModLen() {
        KeyPair kp = BlindSignatureService.generateKeyPair();
        byte[] msg = "test message".getBytes(StandardCharsets.UTF_8);
        BigInteger[] blinding = new BigInteger[1];
        byte[] blinded = BlindSignatureService.blind(msg, kp, new SecureRandom(), blinding);
        byte[] signature = BlindSignatureService.sign(blinded, kp);
        assertEquals(256, signature.length);
    }
}
