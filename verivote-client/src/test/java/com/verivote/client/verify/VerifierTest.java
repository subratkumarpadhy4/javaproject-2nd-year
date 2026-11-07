package com.verivote.client.verify;

import com.verivote.client.crypto.ClientCrypto;
import com.verivote.core.crypto.MerkleTree;
import com.verivote.core.crypto.MerkleVerifier;
import com.verivote.core.domain.DomainStrings;
import com.verivote.core.encoding.BigEndian;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VerifierTest {

    private byte[] makeLeaf(int i) {
        return MerkleTree.leafHash(("vote-leaf-" + i).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("1. Build 5-leaf Merkle tree, verify proof against root -> true")
    void verifyFiveLeafTree() {
        List<byte[]> leaves = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            leaves.add(makeLeaf(i));
        }

        byte[] root = MerkleTree.rootAt(leaves, 5);

        for (int i = 0; i < 5; i++) {
            List<byte[]> proof = MerkleTree.proof(leaves, i, 5);
            boolean valid = MerkleVerifier.verify(leaves.get(i), i, 5, proof, root);
            assertTrue(valid, "Proof must verify for leaf index " + i);
        }
    }

    @Test
    @DisplayName("2. Tampered proof fails verification -> false")
    void tamperedProofFails() {
        List<byte[]> leaves = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            leaves.add(makeLeaf(i));
        }
        byte[] root = MerkleTree.rootAt(leaves, 5);
        List<byte[]> proof = MerkleTree.proof(leaves, 2, 5);

        List<byte[]> tampered = new ArrayList<>();
        for (byte[] p : proof) {
            tampered.add(p.clone());
        }
        tampered.get(0)[0] ^= 0x01;

        assertFalse(MerkleVerifier.verify(leaves.get(2), 2, 5, tampered, root));
    }

    @Test
    @DisplayName("3. Wrong index fails verification -> false")
    void wrongIndexFails() {
        List<byte[]> leaves = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            leaves.add(makeLeaf(i));
        }
        byte[] root = MerkleTree.rootAt(leaves, 5);
        List<byte[]> proof = MerkleTree.proof(leaves, 2, 5);

        assertFalse(MerkleVerifier.verify(leaves.get(2), 3, 5, proof, root));
    }

    @Test
    @DisplayName("4. Wrong root fails verification -> false")
    void wrongRootFails() {
        List<byte[]> leaves = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            leaves.add(makeLeaf(i));
        }
        byte[] root = MerkleTree.rootAt(leaves, 5);
        byte[] wrongRoot = root.clone();
        wrongRoot[0] ^= 0xFF;

        List<byte[]> proof = MerkleTree.proof(leaves, 2, 5);
        assertFalse(MerkleVerifier.verify(leaves.get(2), 2, 5, proof, wrongRoot));
    }

    @Test
    @DisplayName("5. Empty proof with tree size 1 verifies single leaf -> true")
    void singleLeafEmptyProof() {
        byte[] leaf = makeLeaf(0);
        byte[] root = MerkleTree.rootAt(List.of(leaf), 1);
        List<byte[]> emptyProof = MerkleTree.proof(List.of(leaf), 0, 1);

        assertTrue(emptyProof.isEmpty());
        assertTrue(MerkleVerifier.verify(leaf, 0, 1, emptyProof, root));
    }

    @Test
    @DisplayName("6. Checkpoint Ed25519 signature verification passes for valid signature")
    void checkpointSignatureVerification() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = kpg.generateKeyPair();

        long treeSize = 42L;
        byte[] root = ClientCrypto.randomBytes32();
        byte[] head = ClientCrypto.randomBytes32();

        byte[] prefix = DomainStrings.CKPT_PREFIX.getBytes(StandardCharsets.US_ASCII);
        byte[] sizeBytes = BigEndian.u32be(treeSize);
        byte[] preimage = new byte[prefix.length + sizeBytes.length + root.length + head.length];
        System.arraycopy(prefix, 0, preimage, 0, prefix.length);
        System.arraycopy(sizeBytes, 0, preimage, prefix.length, sizeBytes.length);
        System.arraycopy(root, 0, preimage, prefix.length + sizeBytes.length, root.length);
        System.arraycopy(head, 0, preimage, prefix.length + sizeBytes.length + root.length, head.length);

        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(kp.getPrivate());
        signer.update(preimage);
        byte[] signature = signer.sign();

        boolean ok = ClientCrypto.verifyCheckpointSignature(treeSize, root, head, signature, kp.getPublic());
        assertTrue(ok, "Checkpoint signature must verify");

        // Tampered signature fails
        byte[] badSig = signature.clone();
        badSig[0] ^= 0x01;
        assertFalse(ClientCrypto.verifyCheckpointSignature(treeSize, root, head, badSig, kp.getPublic()));
    }
}
