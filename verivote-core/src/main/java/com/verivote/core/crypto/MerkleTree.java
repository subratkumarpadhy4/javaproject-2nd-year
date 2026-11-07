package com.verivote.core.crypto;

import com.verivote.core.domain.DomainStrings;
import com.verivote.core.encoding.Hex;

import java.util.ArrayList;
import java.util.List;

/**
 * RFC 6962-style Merkle tree operations.
 */
public final class MerkleTree {

    private MerkleTree() {}

    /**
     * The root hash of an empty tree: H(0x02) frozen constant
     * "6e340b9cffb37a989ca544e6bb780a2c78901d3fb33738768511a30617afa01d".
     */
    public static final byte[] EMPTY_ROOT = Hex.decode("6e340b9cffb37a989ca544e6bb780a2c78901d3fb33738768511a30617afa01d");

    /**
     * Returns the Merkle root over the first {@code treeSize} leaves in {@code leaves}.
     *
     * @param leaves   list of 32-byte leaf hashes
     * @param treeSize number of leaves to include in the tree calculation
     * @return 32-byte Merkle tree root hash
     * @throws IllegalArgumentException if {@code leaves} is null, {@code treeSize < 0},
     *                                  or {@code treeSize > leaves.size()}
     */
    public static byte[] rootAt(List<byte[]> leaves, long treeSize) {
        if (leaves == null) {
            throw new IllegalArgumentException("Leaves list cannot be null");
        }
        if (treeSize < 0) {
            throw new IllegalArgumentException("Tree size cannot be negative: " + treeSize);
        }
        if (treeSize > leaves.size()) {
            throw new IllegalArgumentException("Tree size " + treeSize + " exceeds leaves count " + leaves.size());
        }
        if (treeSize == 0) {
            return EMPTY_ROOT;
        }
        return computeRoot(leaves, 0, (int) treeSize);
    }

    /**
     * Returns the inclusion proof for the leaf at {@code index} in a tree of {@code treeSize} leaves.
     * Proof is a list of 32-byte sibling hashes in leaf-to-root order.
     *
     * @param leaves   list of 32-byte leaf hashes
     * @param index    0-based index of the leaf
     * @param treeSize number of leaves in the tree snapshot
     * @return list of sibling hashes from leaf level up to the root
     * @throws IllegalArgumentException if {@code leaves} is null, {@code treeSize <= 0},
     *                                  {@code treeSize > leaves.size()}, or {@code index < 0 || index >= treeSize}
     */
    public static List<byte[]> proof(List<byte[]> leaves, long index, long treeSize) {
        if (leaves == null) {
            throw new IllegalArgumentException("Leaves list cannot be null");
        }
        if (treeSize <= 0) {
            throw new IllegalArgumentException("Tree size must be positive: " + treeSize);
        }
        if (treeSize > leaves.size()) {
            throw new IllegalArgumentException("Tree size " + treeSize + " exceeds leaves count " + leaves.size());
        }
        if (index < 0 || index >= treeSize) {
            throw new IllegalArgumentException("Index out of bounds: " + index + ", treeSize: " + treeSize);
        }

        List<byte[]> result = new ArrayList<>();
        buildProof(leaves, (int) index, 0, (int) treeSize, result);
        return result;
    }

    /**
     * Returns the RFC 6962 leaf hash: SHA-256(0x00 || data).
     *
     * @param data raw leaf preimage bytes
     * @return 32-byte leaf hash
     * @throws IllegalArgumentException if {@code data} is null
     */
    public static byte[] leafHash(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("Leaf data cannot be null");
        }
        return Sha256.hash(new byte[]{DomainStrings.LEAF_DOMAIN}, data);
    }

    /**
     * Returns the RFC 6962 node hash: SHA-256(0x01 || left || right).
     *
     * @param left  32-byte left child hash
     * @param right 32-byte right child hash
     * @return 32-byte parent node hash
     * @throws IllegalArgumentException if {@code left} or {@code right} is null
     */
    public static byte[] nodeHash(byte[] left, byte[] right) {
        if (left == null || right == null) {
            throw new IllegalArgumentException("Node hashes cannot be null");
        }
        return Sha256.hash(new byte[]{DomainStrings.NODE_DOMAIN}, left, right);
    }

    // ── Internal recursive helpers ──────────────────────────────────────────

    private static byte[] computeRoot(List<byte[]> leaves, int from, int to) {
        int m = to - from;
        if (m == 1) {
            return leaves.get(from);
        }
        int k = Integer.highestOneBit(m - 1);
        byte[] left = computeRoot(leaves, from, from + k);
        byte[] right = computeRoot(leaves, from + k, to);
        return nodeHash(left, right);
    }

    private static void buildProof(List<byte[]> leaves, int index, int from, int to, List<byte[]> result) {
        int m = to - from;
        if (m <= 1) {
            return;
        }
        int k = Integer.highestOneBit(m - 1);
        int split = from + k;
        if (index < split) {
            buildProof(leaves, index, from, split, result);
            result.add(computeRoot(leaves, split, to));
        } else {
            buildProof(leaves, index, split, to, result);
            result.add(computeRoot(leaves, from, split));
        }
    }
}
