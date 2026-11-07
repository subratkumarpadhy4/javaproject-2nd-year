package com.verivote.core.crypto;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-256 hashing utilities.
 */
public final class Sha256 {

    private Sha256() {}

    /**
     * Returns the SHA-256 hash of {@code input}.
     * Returns 32 bytes. Null throws IllegalArgumentException.
     */
    public static byte[] hash(byte[] input) {
        if (input == null) {
            throw new IllegalArgumentException("Input byte array cannot be null");
        }
        MessageDigest md = createDigest();
        return md.digest(input);
    }

    /**
     * Returns the SHA-256 hash of the concatenation of all {@code parts}.
     * Null element in parts throws IllegalArgumentException.
     * Null parts array throws IllegalArgumentException.
     * Empty parts returns SHA-256 of empty string.
     */
    public static byte[] hash(byte[]... parts) {
        if (parts == null) {
            throw new IllegalArgumentException("Parts array cannot be null");
        }
        MessageDigest md = createDigest();
        for (byte[] part : parts) {
            if (part == null) {
                throw new IllegalArgumentException("Part byte array cannot be null");
            }
            md.update(part);
        }
        return md.digest();
    }

    private static MessageDigest createDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
