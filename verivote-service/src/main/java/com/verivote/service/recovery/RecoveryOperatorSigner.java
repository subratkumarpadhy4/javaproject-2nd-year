package com.verivote.service.recovery;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;

public final class RecoveryOperatorSigner {

    private RecoveryOperatorSigner() {}

    public static byte[] sign(byte[] preimage, PrivateKey operatorPriv) {
        if (preimage == null || operatorPriv == null) {
            throw new IllegalArgumentException("Preimage and private key cannot be null");
        }
        try {
            Signature sig = Signature.getInstance("Ed25519");
            sig.initSign(operatorPriv);
            sig.update(preimage);
            return sig.sign();
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign recovery preimage", e);
        }
    }

    public static boolean verify(byte[] preimage, byte[] sig, PublicKey operatorPub) {
        if (preimage == null || sig == null || operatorPub == null) {
            return false;
        }
        try {
            Signature s = Signature.getInstance("Ed25519");
            s.initVerify(operatorPub);
            s.update(preimage);
            return s.verify(sig);
        } catch (Exception e) {
            return false;
        }
    }
}
