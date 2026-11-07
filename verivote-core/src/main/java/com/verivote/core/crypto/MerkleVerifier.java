package com.verivote.core.crypto;

import java.util.Arrays;
import java.util.List;

/**
 * Merkle inclusion-proof verifier implementing RFC 6962.
 */
public final class MerkleVerifier {

    private MerkleVerifier() {}

    /**
     * Verifies that {@code leaf} (the raw leaf hash, i.e. {@code MerkleTree.leafHash(data)})
     * is the leaf at {@code index} in a tree of {@code treeSize} leaves with root {@code root},
     * given the inclusion {@code proof}.
     *
     * @param leaf     32-byte hash of the leaf entry
     * @param index    0-based position of the leaf
     * @param treeSize number of leaves in the tree snapshot
     * @param proof    list of sibling hashes from leaf level up to the root
     * @param root     expected 32-byte Merkle tree root hash
     * @return true iff the proof is valid and evaluates to {@code root}
     * @throws IllegalArgumentException if structural preconditions are violated:
     *                                  {@code leaf == null}, {@code root == null},
     *                                  {@code proof == null}, {@code treeSize <= 0},
     *                                  {@code index < 0 || index >= treeSize}, or
     *                                  {@code proof.size() > 64}
     */
    public static boolean verify(byte[] leaf, long index, long treeSize,
                                 List<byte[]> proof, byte[] root) {
        if (leaf == null) {
            throw new IllegalArgumentException("Leaf cannot be null");
        }
        if (root == null) {
            throw new IllegalArgumentException("Root cannot be null");
        }
        if (proof == null) {
            throw new IllegalArgumentException("Proof cannot be null");
        }
        if (treeSize <= 0) {
            throw new IllegalArgumentException("Tree size must be positive: " + treeSize);
        }
        if (index < 0 || index >= treeSize) {
            throw new IllegalArgumentException("Index out of bounds: " + index + ", treeSize: " + treeSize);
        }
        if (proof.size() > 64) {
            throw new IllegalArgumentException("Proof size exceeds maximum depth of 64: " + proof.size());
        }

        long fn = index;
        long sn = treeSize - 1;
        byte[] r = leaf;

        for (byte[] p : proof) {
            if (p == null || p.length != 32) {
                return false;
            }
            if (sn == 0) {
                return false; // proof longer than expected
            }
            if ((fn & 1) == 1 || fn == sn) {
                r = MerkleTree.nodeHash(p, r);
                if ((fn & 1) == 0) {
                    // fn == sn and fn is even: skip the alignment loop
                    while ((fn & 1) == 0 && fn != 0) {
                        fn >>= 1;
                        sn >>= 1;
                    }
                }
            } else {
                r = MerkleTree.nodeHash(r, p);
            }
            fn >>= 1;
            sn >>= 1;
        }

        // Public data verification; timing-safe comparison not required
        return sn == 0 && Arrays.equals(r, root);
    }
}
