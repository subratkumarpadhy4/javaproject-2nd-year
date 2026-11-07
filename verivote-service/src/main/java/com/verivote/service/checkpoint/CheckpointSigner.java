package com.verivote.service.checkpoint;

import com.verivote.core.domain.DomainStrings;
import com.verivote.core.encoding.Base64Codec;
import com.verivote.core.encoding.BigEndian;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

public final class CheckpointSigner {

    private CheckpointSigner() {}

    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
            return kpg.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Ed25519 algorithm not available", e);
        }
    }

    public static String encodePrivateKey(PrivateKey privateKey) {
        return Base64Codec.encode(privateKey.getEncoded());
    }

    public static String encodePrivateKey(KeyPair keyPair) {
        return encodePrivateKey(keyPair.getPrivate());
    }

    public static PrivateKey decodePrivateKey(String b64) {
        try {
            byte[] bytes = Base64Codec.decode(b64);
            KeyFactory kf = KeyFactory.getInstance("Ed25519");
            return kf.generatePrivate(new PKCS8EncodedKeySpec(bytes));
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to decode Ed25519 private key", e);
        }
    }

    public static String encodePublicKey(PublicKey publicKey) {
        return Base64Codec.encode(publicKey.getEncoded());
    }

    public static String encodePublicKey(KeyPair keyPair) {
        return encodePublicKey(keyPair.getPublic());
    }

    public static PublicKey decodePublicKey(String b64) {
        try {
            byte[] bytes = Base64Codec.decode(b64);
            KeyFactory kf = KeyFactory.getInstance("Ed25519");
            return kf.generatePublic(new X509EncodedKeySpec(bytes));
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to decode Ed25519 public key", e);
        }
    }

    public static byte[] buildPreimage(long treeSize, byte[] root, byte[] head) {
        if (root == null || root.length != 32) {
            throw new IllegalArgumentException("root must be 32 bytes");
        }
        if (head == null || head.length != 32) {
            throw new IllegalArgumentException("head must be 32 bytes");
        }
        byte[] prefix = DomainStrings.CKPT_PREFIX.getBytes(StandardCharsets.US_ASCII);
        byte[] sizeBytes = BigEndian.u32be(treeSize);
        byte[] preimage = new byte[prefix.length + sizeBytes.length + root.length + head.length];
        System.arraycopy(prefix, 0, preimage, 0, prefix.length);
        System.arraycopy(sizeBytes, 0, preimage, prefix.length, sizeBytes.length);
        System.arraycopy(root, 0, preimage, prefix.length + sizeBytes.length, root.length);
        System.arraycopy(head, 0, preimage, prefix.length + sizeBytes.length + root.length, head.length);
        return preimage;
    }

    public static byte[] sign(long treeSize, byte[] root, byte[] head, PrivateKey privateKey) {
        byte[] preimage = buildPreimage(treeSize, root, head);
        try {
            Signature sig = Signature.getInstance("Ed25519");
            sig.initSign(privateKey);
            sig.update(preimage);
            return sig.sign();
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign checkpoint preimage", e);
        }
    }

    public static boolean verify(long treeSize, byte[] root, byte[] head, byte[] signature, PublicKey publicKey) {
        try {
            byte[] preimage = buildPreimage(treeSize, root, head);
            Signature sig = Signature.getInstance("Ed25519");
            sig.initVerify(publicKey);
            sig.update(preimage);
            return sig.verify(signature);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean verify(byte[] signature, PublicKey publicKey, long treeSize, byte[] root, byte[] head) {
        return verify(treeSize, root, head, signature, publicKey);
    }
}
