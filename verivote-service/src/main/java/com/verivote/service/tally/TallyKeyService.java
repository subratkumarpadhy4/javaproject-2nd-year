package com.verivote.service.tally;

import com.verivote.core.crypto.Shamir;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import java.nio.ByteBuffer;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.List;

@Service
public class TallyKeyService {

    private java.security.KeyPair tallyKeyPair;
    private List<Shamir.Share> shares;

    public TallyKeyService() {
        init();
    }

    @PostConstruct
    public void init() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            this.tallyKeyPair = kpg.generateKeyPair();

            byte[] pkcs8 = tallyKeyPair.getPrivate().getEncoded();
            this.shares = Shamir.split(pkcs8, 3, 2, new SecureRandom());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("RSA algorithm unavailable", e);
        }
    }

    public byte[] getPublicKeySpki() {
        return tallyKeyPair.getPublic().getEncoded();
    }

    public PublicKey getPublicKey() {
        return tallyKeyPair.getPublic();
    }

    public List<Shamir.Share> getShares() {
        return shares;
    }

    public PrivateKey reconstructAndGetPrivateKey(List<Shamir.Share> providedShares) {
        byte[] pkcs8 = Shamir.reconstruct(providedShares);
        try {
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(pkcs8);
            return KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (Exception e) {
            throw new RuntimeException("Failed to reconstruct RSA private key from shares", e);
        } finally {
            // Best-effort zeroize: JVM retains BigInteger copies internally (L3 limitation)
            java.util.Arrays.fill(pkcs8, (byte) 0);
        }
    }

    public static byte[] encryptVote(PublicKey pubKey, long electionId, int candidateId, byte[] ballotId) {
        if (ballotId == null || ballotId.length != 32) {
            throw new IllegalArgumentException("ballotId must be 32 bytes");
        }
        ByteBuffer bb = ByteBuffer.allocate(44);
        bb.putLong(electionId);
        bb.putInt(candidateId);
        bb.put(ballotId);

        try {
            Cipher c = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            c.init(Cipher.ENCRYPT_MODE, pubKey);
            return c.doFinal(bb.array());
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt vote", e);
        }
    }
}
