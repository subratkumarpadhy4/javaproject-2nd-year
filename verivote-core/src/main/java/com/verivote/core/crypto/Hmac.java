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
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec keySpec = new SecretKeySpec(key, HMAC_SHA256);
            mac.init(keySpec);
            return mac.doFinal(message);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("HmacSHA256 algorithm not available", e);
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("Invalid HMAC key", e);
        }
    }
}
