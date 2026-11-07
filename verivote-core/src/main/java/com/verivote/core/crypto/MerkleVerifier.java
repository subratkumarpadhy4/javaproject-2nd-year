package com.verivote.core.crypto;

import java.util.List;

/**
 * Merkle inclusion-proof verifier.
 * Implementation deferred to Part 3.
 */
public final class MerkleVerifier {

    private MerkleVerifier() {}

    /**
     * Verifies that {@code leaf} at position {@code index} in a tree of
     * {@code treeSize} leaves is consistent with the given {@code proof} and
     * {@code root}.
     */
    public static boolean verify(byte[] leaf, long index, long treeSize,
                                 List<byte[]> proof, byte[] root) {
        throw new UnsupportedOperationException("Part 3");
    }
}
