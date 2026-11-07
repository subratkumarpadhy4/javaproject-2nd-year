package com.verivote.core.crypto;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * Shamir's Secret Sharing utilities over GF(256), k-of-n.
 */
public final class Shamir {

    private static final int[] EXP = new int[512];
    private static final int[] LOG = new int[256];

    static {
        int x = 1;
        for (int i = 0; i < 255; i++) {
            EXP[i] = x;
            EXP[i + 255] = x;
            LOG[x] = i;
            // AES irreducible polynomial: x^8 + x^4 + x^3 + x + 1 (0x11B)
            // Generator: 3
            int x2 = (x << 1);
            if ((x & 0x80) != 0) {
                x2 ^= 0x11B;
            }
            x = x2 ^ x;
        }
    }

    private Shamir() {}

    private static int mul(int a, int b) {
        if (a == 0 || b == 0) {
            return 0;
        }
        return EXP[LOG[a] + LOG[b]];
    }

    private static int div(int a, int b) {
        if (b == 0) {
            throw new ArithmeticException("Division by zero in GF(256)");
        }
        if (a == 0) {
            return 0;
        }
        return EXP[(LOG[a] - LOG[b] + 255) % 255];
    }

    /**
     * A single Shamir share.
     *
     * @param index 1-based share index
     * @param value share value bytes
     */
    public record Share(int index, byte[] value) {}

    /**
     * Splits {@code secret} into {@code n} shares with threshold {@code k}.
     *
     * @param secret non-empty secret bytes
     * @param n      number of shares (1 <= n <= 255)
     * @param k      reconstruction threshold (2 <= k <= n)
     * @param rnd    secure random generator
     * @return exactly n shares
     */
    public static List<Share> split(byte[] secret, int n, int k, SecureRandom rnd) {
        if (secret == null || secret.length == 0) {
            throw new IllegalArgumentException("Secret must be non-empty");
        }
        if (k < 2 || k > n || n <= 0 || n > 255) {
            throw new IllegalArgumentException("Invalid threshold k or share count n (must satisfy 2 <= k <= n <= 255)");
        }
        if (rnd == null) {
            throw new IllegalArgumentException("SecureRandom cannot be null");
        }

        int len = secret.length;
        List<byte[]> shareValues = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            shareValues.add(new byte[len]);
        }

        int[] coeffs = new int[k];
        for (int b = 0; b < len; b++) {
            coeffs[0] = secret[b] & 0xFF;
            for (int j = 1; j < k; j++) {
                coeffs[j] = rnd.nextInt(256);
            }
            for (int i = 1; i <= n; i++) {
                int res = 0;
                for (int j = k - 1; j >= 0; j--) {
                    res = mul(res, i) ^ coeffs[j];
                }
                shareValues.get(i - 1)[b] = (byte) res;
            }
        }

        List<Share> shares = new ArrayList<>(n);
        for (int i = 1; i <= n; i++) {
            shares.add(new Share(i, shareValues.get(i - 1)));
        }
        return shares;
    }

    /**
     * Reconstructs the secret from at least {@code k} shares using Lagrange interpolation at x=0.
     *
     * @param shares list of at least 2 shares
     * @return reconstructed secret bytes
     */
    public static byte[] reconstruct(List<Share> shares) {
        if (shares == null || shares.size() < 2) {
            throw new IllegalArgumentException("At least 2 shares required for reconstruction");
        }
        if (shares.get(0).value() == null || shares.get(0).value().length == 0) {
            throw new IllegalArgumentException("Secret value in shares cannot be empty");
        }

        int len = shares.get(0).value().length;
        boolean[] seen = new boolean[256];
        for (Share s : shares) {
            if (s == null || s.value() == null) {
                throw new IllegalArgumentException("Share or share value cannot be null");
            }
            if (s.index() <= 0 || s.index() > 255) {
                throw new IllegalArgumentException("Share index out of valid range [1, 255]: " + s.index());
            }
            if (seen[s.index()]) {
                throw new IllegalArgumentException("Duplicate share index: " + s.index());
            }
            seen[s.index()] = true;
            if (s.value().length != len) {
                throw new IllegalArgumentException("All shares must have the same length");
            }
        }

        int m = shares.size();
        int[] l = new int[m];
        for (int j = 0; j < m; j++) {
            int xj = shares.get(j).index();
            int num = 1;
            int den = 1;
            for (int p = 0; p < m; p++) {
                if (p == j) {
                    continue;
                }
                int xp = shares.get(p).index();
                num = mul(num, xp);
                den = mul(den, xj ^ xp);
            }
            l[j] = div(num, den);
        }

        byte[] secret = new byte[len];
        for (int b = 0; b < len; b++) {
            int val = 0;
            for (int j = 0; j < m; j++) {
                int yj = shares.get(j).value()[b] & 0xFF;
                val ^= mul(yj, l[j]);
            }
            secret[b] = (byte) val;
        }
        return secret;
    }
}
