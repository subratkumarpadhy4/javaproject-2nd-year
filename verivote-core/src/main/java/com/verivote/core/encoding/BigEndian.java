package com.verivote.core.encoding;

/**
 * Fixed-width big-endian byte-array encoders.
 */
public final class BigEndian {

    private BigEndian() {}

    /**
     * Encodes {@code x} as 4 unsigned big-endian bytes.
     * Masked to low 32 bits.
     */
    public static byte[] u32be(long x) {
        long v = x & 0xFFFFFFFFL;
        return new byte[] {
            (byte) ((v >>> 24) & 0xFF),
            (byte) ((v >>> 16) & 0xFF),
            (byte) ((v >>> 8) & 0xFF),
            (byte) (v & 0xFF)
        };
    }

    /**
     * Encodes {@code x} as 8 unsigned big-endian bytes.
     * Input long is treated as unsigned (raw bits).
     */
    public static byte[] u64be(long x) {
        return new byte[] {
            (byte) ((x >>> 56) & 0xFF),
            (byte) ((x >>> 48) & 0xFF),
            (byte) ((x >>> 40) & 0xFF),
            (byte) ((x >>> 32) & 0xFF),
            (byte) ((x >>> 24) & 0xFF),
            (byte) ((x >>> 16) & 0xFF),
            (byte) ((x >>> 8) & 0xFF),
            (byte) (x & 0xFF)
        };
    }

    /**
     * Encodes {@code x} as 8 signed big-endian bytes (two's complement).
     * Byte-identical to {@link #u64be(long)}.
     */
    public static byte[] int64be(long x) {
        return u64be(x);
    }
}
