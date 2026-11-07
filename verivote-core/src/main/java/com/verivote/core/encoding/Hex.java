package com.verivote.core.encoding;

/**
 * Hexadecimal encoding/decoding utilities (lowercase output).
 */
public final class Hex {

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    private Hex() {}

    /**
     * Encodes {@code b} as a lowercase hexadecimal string.
     * Output length = 2 * b.length. No "0x" prefix.
     */
    public static String encode(byte[] b) {
        if (b == null) {
            throw new IllegalArgumentException("Byte array cannot be null");
        }
        char[] out = new char[b.length * 2];
        for (int i = 0; i < b.length; i++) {
            int v = b[i] & 0xFF;
            out[i * 2] = HEX_CHARS[v >>> 4];
            out[i * 2 + 1] = HEX_CHARS[v & 0x0F];
        }
        return new String(out);
    }

    /**
     * Decodes a hexadecimal string to bytes.
     * Strict decoder: accepts only [0-9a-f] (rejects uppercase),
     * length must be even, null throws IllegalArgumentException.
     */
    public static byte[] decode(String s) {
        if (s == null) {
            throw new IllegalArgumentException("Hex string cannot be null");
        }
        if (s.length() % 2 != 0) {
            throw new IllegalArgumentException("Hex string must have an even length, got: " + s.length());
        }
        int len = s.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int hi = decodeNibble(s.charAt(i));
            int lo = decodeNibble(s.charAt(i + 1));
            out[i / 2] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    private static int decodeNibble(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        throw new IllegalArgumentException("Invalid hex character (only lowercase 0-9 and a-f allowed): '" + c + "'");
    }
}
