package com.verivote.core;

import com.verivote.core.crypto.Shamir;
import com.verivote.core.crypto.Shamir.Share;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShamirTest {

    @Test
    @DisplayName("1. 2-of-3 reconstruction: any 2 of 3 shares reconstruct original secret")
    void twoOfThreeReconstruction() {
        byte[] secret = "hello world 1234".getBytes(StandardCharsets.UTF_8);
        List<Share> shares = Shamir.split(secret, 3, 2, new SecureRandom());
        assertEquals(3, shares.size());

        for (int i = 0; i < 3; i++) {
            for (int j = i + 1; j < 3; j++) {
                byte[] reconstructed = Shamir.reconstruct(List.of(shares.get(i), shares.get(j)));
                assertArrayEquals(secret, reconstructed, "Pair (" + i + ", " + j + ") must reconstruct the secret");
            }
        }
    }

    @Test
    @DisplayName("2. 1 share is insufficient: reconstruct throws IllegalArgumentException")
    void oneShareIsInsufficient() {
        byte[] secret = "hello world 1234".getBytes(StandardCharsets.UTF_8);
        List<Share> shares = Shamir.split(secret, 3, 2, new SecureRandom());
        for (Share s : shares) {
            assertThrows(IllegalArgumentException.class, () -> Shamir.reconstruct(List.of(s)));
        }
    }

    @Test
    @DisplayName("3. 3 shares still work")
    void threeSharesStillWork() {
        byte[] secret = "hello world 1234".getBytes(StandardCharsets.UTF_8);
        List<Share> shares = Shamir.split(secret, 3, 2, new SecureRandom());
        byte[] reconstructed = Shamir.reconstruct(shares);
        assertArrayEquals(secret, reconstructed);
    }

    @Test
    @DisplayName("4. Different secrets -> different shares")
    void differentSecretsDifferentShares() {
        byte[] secretA = "hello world 1234".getBytes(StandardCharsets.UTF_8);
        byte[] secretB = "secret world 567".getBytes(StandardCharsets.UTF_8);
        List<Share> sharesA = Shamir.split(secretA, 3, 2, new SecureRandom());
        List<Share> sharesB = Shamir.split(secretB, 3, 2, new SecureRandom());

        for (int i = 0; i < 3; i++) {
            assertFalse(Arrays.equals(sharesA.get(i).value(), sharesB.get(i).value()),
                    "Share " + i + " must differ between different secrets");
        }
    }

    @Test
    @DisplayName("5. Rerandomize: different randomness -> different shares")
    void rerandomizeDifferentShares() {
        byte[] secret = "hello world 1234".getBytes(StandardCharsets.UTF_8);
        SecureRandom rndA = new SecureRandom(new byte[]{1, 2, 3, 4});
        SecureRandom rndB = new SecureRandom(new byte[]{5, 6, 7, 8});
        List<Share> sharesA = Shamir.split(secret, 3, 2, rndA);
        List<Share> sharesB = Shamir.split(secret, 3, 2, rndB);

        for (int i = 0; i < 3; i++) {
            assertFalse(Arrays.equals(sharesA.get(i).value(), sharesB.get(i).value()),
                    "Share " + i + " must differ when generated with different randomness");
        }
    }

    @Test
    @DisplayName("6. Invalid arguments rejected")
    void invalidArguments() {
        byte[] secret = "hello world 1234".getBytes(StandardCharsets.UTF_8);
        SecureRandom rnd = new SecureRandom();

        // k > n
        assertThrows(IllegalArgumentException.class, () -> Shamir.split(secret, 2, 3, rnd));
        // k <= 1
        assertThrows(IllegalArgumentException.class, () -> Shamir.split(secret, 3, 1, rnd));
        // n <= 0
        assertThrows(IllegalArgumentException.class, () -> Shamir.split(secret, 0, 2, rnd));
        // empty secret
        assertThrows(IllegalArgumentException.class, () -> Shamir.split(new byte[0], 3, 2, rnd));
        // null secret
        assertThrows(IllegalArgumentException.class, () -> Shamir.split(null, 3, 2, rnd));

        // reconstruct empty
        assertThrows(IllegalArgumentException.class, () -> Shamir.reconstruct(List.of()));
        // reconstruct single share
        Share s1 = new Share(1, new byte[]{1, 2, 3});
        assertThrows(IllegalArgumentException.class, () -> Shamir.reconstruct(List.of(s1)));
        // duplicate indices
        Share s2 = new Share(1, new byte[]{4, 5, 6});
        assertThrows(IllegalArgumentException.class, () -> Shamir.reconstruct(List.of(s1, s2)));
        // shares with different lengths
        Share s3 = new Share(2, new byte[]{1, 2});
        assertThrows(IllegalArgumentException.class, () -> Shamir.reconstruct(List.of(s1, s3)));
    }
}
