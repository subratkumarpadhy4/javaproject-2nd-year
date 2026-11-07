package com.verivote.client.crypto;

import com.verivote.client.ApplicationConfig;
import com.verivote.core.encoding.Base64Codec;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

public final class PinnedKeys {

    private final PublicKey tallyPub;
    private final PublicKey ckptPub;

    public PinnedKeys(String tallySpkiB64, String ckptSpkiB64) throws Exception {
        if (tallySpkiB64 != null && !tallySpkiB64.isBlank()) {
            byte[] spki = Base64Codec.decode(tallySpkiB64.trim());
            this.tallyPub = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(spki));
        } else {
            this.tallyPub = null;
        }

        if (ckptSpkiB64 != null && !ckptSpkiB64.isBlank()) {
            byte[] spki = Base64Codec.decode(ckptSpkiB64.trim());
            this.ckptPub = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(spki));
        } else {
            this.ckptPub = null;
        }
    }

    public PublicKey tallyPub() {
        return tallyPub;
    }

    public PublicKey ckptPub() {
        return ckptPub;
    }

    public static PinnedKeys fromConfig(ApplicationConfig cfg) throws Exception {
        String tallyB64 = ApplicationConfig.get("verivote.pinned.tally-pub", "");
        String ckptB64 = ApplicationConfig.get("verivote.pinned.ckpt-pub", "");
        return new PinnedKeys(tallyB64, ckptB64);
    }
}
