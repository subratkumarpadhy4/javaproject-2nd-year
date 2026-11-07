package com.verivote.client.crypto;

import com.verivote.core.crypto.Sha256;
import com.verivote.core.domain.DomainStrings;
import com.verivote.core.encoding.BigEndian;

import javax.crypto.Cipher;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;

public final class ClientCrypto {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private ClientCrypto() {}

    /**
     * Encrypt vote with server's tally public key. Returns 256 bytes.
     */
    public static byte[] encryptVote(byte[] tallyPubSpki, long electionId, int candidateId, byte[] ballotId)
            throws GeneralSecurityException {
        if (tallyPubSpki == null || tallyPubSpki.length == 0) {
            throw new IllegalArgumentException("tallyPubSpki must not be empty");
        }
        KeyFactory kf = KeyFactory.getInstance("RSA");
        PublicKey pubKey = kf.generatePublic(new X509EncodedKeySpec(tallyPubSpki));
        return encryptVote(pubKey, electionId, candidateId, ballotId);
    }

    public static byte[] encryptVote(PublicKey pubKey, long electionId, int candidateId, byte[] ballotId)
            throws GeneralSecurityException {
        if (ballotId == null || ballotId.length != 32) {
            throw new IllegalArgumentException("ballotId must be exactly 32 bytes");
        }
        ByteBuffer bb = ByteBuffer.allocate(44);
        bb.putLong(electionId);
        bb.putInt(candidateId);
        bb.put(ballotId);

        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, pubKey);
        return cipher.doFinal(bb.array());
    }

    /**
     * Build token preimage: "VERIVOTE-TOKEN-v1" || int64be(eid) || tokenPlain.
     */
    public static byte[] buildTokenPreimage(long electionId, byte[] tokenPlain) {
        if (tokenPlain == null) {
            throw new IllegalArgumentException("tokenPlain must not be null");
        }
        byte[] prefix = DomainStrings.TOKEN_PREFIX.getBytes(StandardCharsets.US_ASCII);
        byte[] eid = BigEndian.u64be(electionId);
        byte[] preimage = new byte[prefix.length + eid.length + tokenPlain.length];
        System.arraycopy(prefix, 0, preimage, 0, prefix.length);
        System.arraycopy(eid, 0, preimage, prefix.length, eid.length);
        System.arraycopy(tokenPlain, 0, preimage, prefix.length + eid.length, tokenPlain.length);
        return preimage;
    }

    /**
     * Build spend hash: SHA-256("VERIVOTE-SPEND-v1" || int64be(eid) || tokenPlain).
     */
    public static byte[] computeSpendHash(long electionId, byte[] tokenPlain) {
        byte[] prefix = DomainStrings.SPEND_PREFIX.getBytes(StandardCharsets.US_ASCII);
        byte[] eid = BigEndian.u64be(electionId);
        return Sha256.hash(prefix, eid, tokenPlain);
    }

    /**
     * Build receipt hash: SHA-256("VERIVOTE-RECEIPT-v1" || int64be(eid) || nonce || tokenHash).
     */
    public static byte[] computeReceiptHash(long electionId, byte[] nonce, byte[] tokenHash) {
        byte[] prefix = DomainStrings.RECEIPT_PREFIX.getBytes(StandardCharsets.US_ASCII);
        byte[] eid = BigEndian.u64be(electionId);
        return Sha256.hash(prefix, eid, nonce, tokenHash);
    }

    /**
     * Generates a random 32-byte array.
     */
    public static byte[] randomBytes32() {
        byte[] b = new byte[32];
        SECURE_RANDOM.nextBytes(b);
        return b;
    }

    /**
     * Verifies an Ed25519 signature over a checkpoint preimage.
     */
    public static boolean verifyCheckpointSignature(long treeSize, byte[] root, byte[] head, byte[] signature, PublicKey ckptPub) {
        if (ckptPub == null || signature == null || root == null || head == null) {
            return false;
        }
        try {
            byte[] prefix = DomainStrings.CKPT_PREFIX.getBytes(StandardCharsets.US_ASCII);
            byte[] sizeBytes = BigEndian.u32be(treeSize);
            byte[] preimage = new byte[prefix.length + sizeBytes.length + root.length + head.length];
            System.arraycopy(prefix, 0, preimage, 0, prefix.length);
            System.arraycopy(sizeBytes, 0, preimage, prefix.length, sizeBytes.length);
            System.arraycopy(root, 0, preimage, prefix.length + sizeBytes.length, root.length);
            System.arraycopy(head, 0, preimage, prefix.length + sizeBytes.length + root.length, head.length);

            Signature sig = Signature.getInstance("Ed25519");
            sig.initVerify(ckptPub);
            sig.update(preimage);
            return sig.verify(signature);
        } catch (Exception e) {
            return false;
        }
    }
}
