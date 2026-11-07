package com.verivote.core.encoding;

import java.util.Base64;

/**
 * Base64 encoding/decoding utilities.
 */
public final class Base64Codec {

    private Base64Codec() {}

    /**
     * Encodes {@code b} to a Base64 string (RFC 4648 with padding).
     */
    public static String encode(byte[] b) {
        if (b == null) {
            throw new IllegalArgumentException("Byte array cannot be null");
        }
        return Base64.getEncoder().encodeToString(b);
    }

    /**
     * Decodes a Base64 string to bytes.
     * Strict decoder: rejects invalid Base64, null throws IllegalArgumentException.
     */
    public static byte[] decode(String s) {
        if (s == null) {
            throw new IllegalArgumentException("Base64 string cannot be null");
        }
        try {
            return Base64.getDecoder().decode(s);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid Base64 string: " + e.getMessage(), e);
        }
    }
}
