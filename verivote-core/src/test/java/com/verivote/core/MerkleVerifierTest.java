package com.verivote.core;

import com.verivote.core.crypto.MerkleTree;
import com.verivote.core.crypto.MerkleVerifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class MerkleVerifierTest {

    private static byte[] leafBytes(int n) {
        return new byte[] {(byte) n};
    }

    private static byte[] L(int n) {
        return MerkleTree.leafHash(leafBytes(n));
    }

    private static List<byte[]> generateLeaves(int count) {
        List<byte[]> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(L(i));
        }
        return list;
    }

    // ── Full round-trip ─────────────────────────────────────────────────────

    @Test
    void fullRoundTripN1To16() {
        for (int n = 1; n <= 16; n++) {
            List<byte[]> leaves = generateLeaves(n);
            byte[] root = MerkleTree.rootAt(leaves, n);
            for (int i = 0; i < n; i++) {
                List<byte[]> proof = MerkleTree.proof(leaves, i, n);
                assertTrue(
                        MerkleVerifier.verify(leaves.get(i), i, n, proof, root),
                        "Verify failed for n=" + n + ", index=" + i
                );
            }
        }
    }

    // ── Rejections ──────────────────────────────────────────────────────────

    @Test
    void rejections() {
        for (int n : List.of(2, 4, 8)) {
            List<byte[]> leaves = generateLeaves(n + 1);
            byte[] root = MerkleTree.rootAt(leaves, n);
            byte[] rootN1 = MerkleTree.rootAt(leaves, n + 1);

            for (int i = 0; i < n; i++) {
                List<byte[]> proof = MerkleTree.proof(leaves, i, n);
                byte[] leaf = leaves.get(i);

                // Wrong leaf
                byte[] wrongLeaf = leaves.get((i + 1) % n);
                assertFalse(MerkleVerifier.verify(wrongLeaf, i, n, proof, root));

                // Wrong index
                int wrongIndex = (i + 1) % n;
                assertFalse(MerkleVerifier.verify(leaf, wrongIndex, n, proof, root));

                // Wrong tree size (against current root and size-n+1 root)
                assertFalse(MerkleVerifier.verify(leaf, i, n + 1, proof, root));
                assertFalse(MerkleVerifier.verify(leaf, i, n + 1, proof, rootN1));

                // Wrong root
                byte[] flippedRoot = root.clone();
                flippedRoot[0] ^= (byte) 0xFF;
                assertFalse(MerkleVerifier.verify(leaf, i, n, proof, flippedRoot));

                // Tampered proof element
                if (!proof.isEmpty()) {
                    List<byte[]> tamperedProof = new ArrayList<>();
                    for (byte[] p : proof) {
                        tamperedProof.add(p.clone());
                    }
                    tamperedProof.get(0)[0] ^= (byte) 0xFF;
                    assertFalse(MerkleVerifier.verify(leaf, i, n, tamperedProof, root));

                    // Truncated proof
                    List<byte[]> truncated = new ArrayList<>(proof.subList(0, proof.size() - 1));
                    assertFalse(MerkleVerifier.verify(leaf, i, n, truncated, root));
                }

                // Extended proof
                List<byte[]> extended = new ArrayList<>(proof);
                extended.add(new byte[32]);
                assertFalse(MerkleVerifier.verify(leaf, i, n, extended, root));
            }
        }
    }

    // ── Structural rejections (IllegalArgumentException) ────────────────────

    @Test
    void structuralRejections() {
        byte[] leaf = L(0);
        byte[] root = L(0);
        List<byte[]> proof = List.of();

        assertThrows(IllegalArgumentException.class, () -> MerkleVerifier.verify(leaf, -1, 5, proof, root));
        assertThrows(IllegalArgumentException.class, () -> MerkleVerifier.verify(leaf, 5, 5, proof, root));
        assertThrows(IllegalArgumentException.class, () -> MerkleVerifier.verify(leaf, 0, 0, proof, root));
        assertThrows(IllegalArgumentException.class, () -> MerkleVerifier.verify(null, 0, 1, proof, root));
        assertThrows(IllegalArgumentException.class, () -> MerkleVerifier.verify(leaf, 0, 1, null, root));
        assertThrows(IllegalArgumentException.class, () -> MerkleVerifier.verify(leaf, 0, 1, proof, null));

        List<byte[]> oversizedProof = new ArrayList<>(65);
        for (int i = 0; i < 65; i++) {
            oversizedProof.add(new byte[32]);
        }
        assertThrows(IllegalArgumentException.class, () -> MerkleVerifier.verify(leaf, 0, 1, oversizedProof, root));
    }

    // ── Property test (deterministic 100 iterations) ────────────────────────

    @Test
    void propertyTestRandomTrees() {
        Random rnd = new Random(42);
        for (int iter = 0; iter < 100; iter++) {
            int n = 1 + rnd.nextInt(20);
            List<byte[]> leaves = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                byte[] raw = new byte[1 + rnd.nextInt(32)];
                rnd.nextBytes(raw);
                leaves.add(MerkleTree.leafHash(raw));
            }
            byte[] root = MerkleTree.rootAt(leaves, n);

            for (int i = 0; i < n; i++) {
                byte[] leaf = leaves.get(i);
                List<byte[]> proof = MerkleTree.proof(leaves, i, n);

                // Correct proof passes
                assertTrue(
                        MerkleVerifier.verify(leaf, i, n, proof, root),
                        "Iter " + iter + ": valid proof must pass"
                );

                // Flipped bit in leaf fails
                byte[] corruptedLeaf = leaf.clone();
                corruptedLeaf[rnd.nextInt(32)] ^= (byte) (1 << rnd.nextInt(8));
                assertFalse(
                        MerkleVerifier.verify(corruptedLeaf, i, n, proof, root),
                        "Iter " + iter + ": corrupted leaf must fail"
                );
            }
        }
    }
}
