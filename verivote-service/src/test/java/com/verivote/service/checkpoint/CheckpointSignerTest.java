package com.verivote.service.checkpoint;

import com.verivote.core.encoding.Hex;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.*;

class CheckpointSignerTest {

    private final SecureRandom rnd = new SecureRandom();

    private byte[] randomBytes(int len) {
        byte[] b = new byte[len];
        rnd.nextBytes(b);
        return b;
    }

    @Test
    @DisplayName("1. generate key pair -> sign -> verify -> true")
    void generateSignVerify() {
        KeyPair kp = CheckpointSigner.generateKeyPair();
        assertNotNull(kp);

        long treeSize = 42L;
        byte[] root = randomBytes(32);
        byte[] head = randomBytes(32);

        byte[] sig = CheckpointSigner.sign(treeSize, root, head, kp.getPrivate());
        assertNotNull(sig);
        assertTrue(sig.length > 0);

        boolean ok = CheckpointSigner.verify(treeSize, root, head, sig, kp.getPublic());
        assertTrue(ok, "Signature must verify successfully");
    }

    @Test
    @DisplayName("2. tamper sig -> verify false")
    void tamperSigFails() {
        KeyPair kp = CheckpointSigner.generateKeyPair();
        long treeSize = 10L;
        byte[] root = randomBytes(32);
        byte[] head = randomBytes(32);

        byte[] sig = CheckpointSigner.sign(treeSize, root, head, kp.getPrivate());
        sig[0] ^= 0x01; // flip 1 bit

        boolean ok = CheckpointSigner.verify(treeSize, root, head, sig, kp.getPublic());
        assertFalse(ok, "Tampered signature must fail verification");
    }

    @Test
    @DisplayName("3. wrong key -> verify false")
    void wrongKeyFails() {
        KeyPair kp1 = CheckpointSigner.generateKeyPair();
        KeyPair kp2 = CheckpointSigner.generateKeyPair();

        long treeSize = 7L;
        byte[] root = randomBytes(32);
        byte[] head = randomBytes(32);

        byte[] sig = CheckpointSigner.sign(treeSize, root, head, kp1.getPrivate());
        boolean ok = CheckpointSigner.verify(treeSize, root, head, sig, kp2.getPublic());
        assertFalse(ok, "Verifying with wrong public key must fail");
    }

    @Test
    @DisplayName("4. PKCS8 encode/decode round-trip")
    void pkcs8RoundTrip() {
        KeyPair kp = CheckpointSigner.generateKeyPair();
        String encoded = CheckpointSigner.encodePrivateKey(kp);
        assertNotNull(encoded);

        PrivateKey decoded = CheckpointSigner.decodePrivateKey(encoded);
        assertNotNull(decoded);
        assertArrayEquals(kp.getPrivate().getEncoded(), decoded.getEncoded());
    }

    @Test
    @DisplayName("5. SPKI encode/decode round-trip")
    void spkiRoundTrip() {
        KeyPair kp = CheckpointSigner.generateKeyPair();
        String encoded = CheckpointSigner.encodePublicKey(kp);
        assertNotNull(encoded);

        PublicKey decoded = CheckpointSigner.decodePublicKey(encoded);
        assertNotNull(decoded);
        assertArrayEquals(kp.getPublic().getEncoded(), decoded.getEncoded());
    }
}
