package com.verivote.core.crypto;

import java.util.List;

/**
 * RFC 6962-style Merkle tree operations.
 * Implementation deferred to Part 3.
 */
public final class MerkleTree {

    private MerkleTree() {}

    /**
     * The root hash of an empty tree: SHA-256(0x02).
     * Value will be computed in Part 3; declared here as a placeholder.
     */
    public static final byte[] EMPTY_ROOT = null; // Part 3: = Sha256.hash(new byte[]{0x02})

    /**
     * Returns the Merkle root over {@code leaves} as of the given {@code treeSize}.
     */
    public static byte[] rootAt(List<byte[]> leaves, long treeSize) {
        throw new UnsupportedOperationException("Part 3");
    }

    /**
     * Returns the inclusion proof for the leaf at {@code index} given {@code treeSize}.
     */
    public static List<byte[]> proof(long index, long treeSize, List<byte[]> leaves) {
        throw new UnsupportedOperationException("Part 3");
    }

    /** Returns the RFC 6962 leaf hash: SHA-256(0x00 || data). */
    public static byte[] leafHash(byte[] data) {
        throw new UnsupportedOperationException("Part 3");
    }

    /** Returns the RFC 6962 node hash: SHA-256(0x01 || left || right). */
    public static byte[] nodeHash(byte[] left, byte[] right) {
        throw new UnsupportedOperationException("Part 3");
    }
}
