package com.verivote.core.crypto;

import java.security.SecureRandom;
import java.util.List;

/**
 * Shamir's Secret Sharing utilities.
 * Implementation deferred to Part 5.
 */
public final class Shamir {

    private Shamir() {}

    /**
     * A single Shamir share.
     *
     * @param index 1-based share index
     * @param value share value bytes
     */
    public record Share(int index, byte[] value) {}

    /**
     * Splits {@code secret} into {@code n} shares with threshold {@code k}.
     */
    public static List<Share> split(byte[] secret, int n, int k, SecureRandom rnd) {
        throw new UnsupportedOperationException("Part 5");
    }

    /** Reconstructs the secret from at least {@code k} shares. */
    public static byte[] reconstruct(List<Share> shares) {
        throw new UnsupportedOperationException("Part 5");
    }
}
