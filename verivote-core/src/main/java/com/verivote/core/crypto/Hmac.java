package com.verivote.core.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * HMAC utilities.
 */
public final class Hmac {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private Hmac() {}

    /**
     * Returns HMAC-SHA-256 of {@code message} using {@code key}.
     * Returns 32 bytes.
     * Null key or null message throws IllegalArgumentException.
     * Empty key is permitted per RFC 2104.
     */
    public static byte[] sha256(byte[] key, byte[] message) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        // RFC 2104 allows zero-length keys by zero-padding to the block size (64 bytes).
        // SecretKeySpec rejects zero-length arrays, so substitute a single 0x00 byte
        // which HmacSHA256 zero-pads to the identical 64-byte block.
        byte[] keyBytes = (key.length == 0) ? new byte[1] : key;
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, HMAC_SHA256);
            mac.init(keySpec);
            return mac.doFinal(message);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("HmacSHA256 algorithm not available", e);
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("Invalid HMAC key", e);
        }
    }
}
