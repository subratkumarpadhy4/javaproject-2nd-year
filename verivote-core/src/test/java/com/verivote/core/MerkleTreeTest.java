package com.verivote.core;

import com.verivote.core.crypto.MerkleTree;
import com.verivote.core.encoding.Hex;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MerkleTreeTest {

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

    // ── Empty tree ──────────────────────────────────────────────────────────

    @Test
    void emptyRootProperties() {
        assertEquals(32, MerkleTree.EMPTY_ROOT.length);
        byte[] expected = Hex.decode("6e340b9cffb37a989ca544e6bb780a2c78901d3fb33738768511a30617afa01d");
        assertArrayEquals(expected, MerkleTree.EMPTY_ROOT);
        assertSame(MerkleTree.EMPTY_ROOT, MerkleTree.rootAt(List.of(), 0));
        assertSame(MerkleTree.EMPTY_ROOT, MerkleTree.rootAt(List.of(L(0)), 0));
    }

    // ── Leaf and Node primitives ────────────────────────────────────────────

    @Test
    void leafAndNodePrimitives() {
        byte[] l0 = MerkleTree.leafHash(new byte[] {0x00});
        byte[] l1 = MerkleTree.leafHash(new byte[] {0x01});
        assertEquals(32, l0.length);
        assertEquals(32, l1.length);
        assertFalse(java.util.Arrays.equals(l0, l1));

        byte[] n1 = MerkleTree.nodeHash(l0, l1);
        byte[] n2 = MerkleTree.nodeHash(l1, l0);
        assertEquals(32, n1.length);
        assertEquals(32, n2.length);
        assertFalse(java.util.Arrays.equals(n1, n2));
    }

    // ── Roots composition ───────────────────────────────────────────────────

    @Test
    void rootsComposition() {
        List<byte[]> leaves = generateLeaves(9);

        // 1 leaf: root = L(0)
        assertArrayEquals(L(0), MerkleTree.rootAt(leaves, 1));

        // 2 leaves: nodeHash(L(0), L(1))
        byte[] root2 = MerkleTree.nodeHash(L(0), L(1));
        assertArrayEquals(root2, MerkleTree.rootAt(leaves, 2));

        // 3 leaves: nodeHash(nodeHash(L(0), L(1)), L(2))
        byte[] root3 = MerkleTree.nodeHash(root2, L(2));
        assertArrayEquals(root3, MerkleTree.rootAt(leaves, 3));

        // 5 leaves: nodeHash(nodeHash(nodeHash(L(0), L(1)), nodeHash(L(2), L(3))), L(4))
        byte[] node01 = MerkleTree.nodeHash(L(0), L(1));
        byte[] node23 = MerkleTree.nodeHash(L(2), L(3));
        byte[] node0123 = MerkleTree.nodeHash(node01, node23);
        byte[] root5 = MerkleTree.nodeHash(node0123, L(4));
        assertArrayEquals(root5, MerkleTree.rootAt(leaves, 5));

        // 8 leaves: nodeHash(nodeHash(nodeHash(L0,L1),nodeHash(L2,L3)), nodeHash(nodeHash(L4,L5),nodeHash(L6,L7)))
        byte[] node45 = MerkleTree.nodeHash(L(4), L(5));
        byte[] node67 = MerkleTree.nodeHash(L(6), L(7));
        byte[] node4567 = MerkleTree.nodeHash(node45, node67);
        byte[] root8 = MerkleTree.nodeHash(node0123, node4567);
        assertArrayEquals(root8, MerkleTree.rootAt(leaves, 8));

        // 9 leaves: nodeHash(<root of 8>, L(8))
        byte[] root9 = MerkleTree.nodeHash(root8, L(8));
        assertArrayEquals(root9, MerkleTree.rootAt(leaves, 9));

        // Cross-check: all 9 roots are distinct
        Set<String> distinctRoots = new HashSet<>();
        for (int k = 1; k <= 9; k++) {
            distinctRoots.add(Hex.encode(MerkleTree.rootAt(leaves, k)));
        }
        assertEquals(9, distinctRoots.size());
    }

    // ── Prefix roots ────────────────────────────────────────────────────────

    @Test
    void prefixRootsMatch() {
        List<byte[]> leaves9 = generateLeaves(9);
        List<byte[]> first3 = leaves9.subList(0, 3);
        assertArrayEquals(MerkleTree.rootAt(first3, 3), MerkleTree.rootAt(leaves9, 3));

        List<byte[]> first5 = leaves9.subList(0, 5);
        assertArrayEquals(MerkleTree.rootAt(first5, 5), MerkleTree.rootAt(leaves9, 5));
    }

    // ── Proof generation sanity ─────────────────────────────────────────────

    @Test
    void proofGenerationSanity() {
        List<byte[]> leaves1 = generateLeaves(1);
        List<byte[]> proofSingle = MerkleTree.proof(leaves1, 0, 1);
        assertTrue(proofSingle.isEmpty());

        for (int n = 1; n <= 8; n++) {
            List<byte[]> leaves = generateLeaves(n);
            int maxExpected = (n == 1) ? 0 : (int) Math.ceil(Math.log(n) / Math.log(2));
            for (int i = 0; i < n; i++) {
                List<byte[]> proof = MerkleTree.proof(leaves, i, n);
                assertTrue(proof.size() <= maxExpected, "Proof size " + proof.size() + " exceeds max " + maxExpected);
                for (byte[] sibling : proof) {
                    assertEquals(32, sibling.length);
                }
            }
        }
    }

    // ── Bad arguments ───────────────────────────────────────────────────────

    @Test
    void badArgumentsForRootAt() {
        assertThrows(IllegalArgumentException.class, () -> MerkleTree.rootAt(null, 0));
        List<byte[]> leaves = generateLeaves(3);
        assertThrows(IllegalArgumentException.class, () -> MerkleTree.rootAt(leaves, -1));
        assertThrows(IllegalArgumentException.class, () -> MerkleTree.rootAt(leaves, 4));
    }

    @Test
    void badArgumentsForProof() {
        List<byte[]> leaves = generateLeaves(5);
        assertThrows(IllegalArgumentException.class, () -> MerkleTree.proof(null, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> MerkleTree.proof(leaves, -1, 5));
        assertThrows(IllegalArgumentException.class, () -> MerkleTree.proof(leaves, 5, 5));
        assertThrows(IllegalArgumentException.class, () -> MerkleTree.proof(leaves, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> MerkleTree.proof(leaves, 0, 6));
    }
}
