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

    /**
     * Load the tally key pair from {@code keyFile} (PKCS#8 private key) if it exists,
     * otherwise persist the current in-memory key there. Without this, every restart
     * generates a fresh key and ballots stored in a persistent database become
     * undecryptable. Demo-grade: the private key is stored unencrypted on disk.
     */
    public synchronized void loadOrPersist(java.nio.file.Path keyFile) {
        try {
            if (java.nio.file.Files.exists(keyFile)) {
                byte[] pkcs8 = java.nio.file.Files.readAllBytes(keyFile);
                KeyFactory kf = KeyFactory.getInstance("RSA");
                java.security.interfaces.RSAPrivateCrtKey priv =
                        (java.security.interfaces.RSAPrivateCrtKey) kf.generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
                PublicKey pub = kf.generatePublic(
                        new java.security.spec.RSAPublicKeySpec(priv.getModulus(), priv.getPublicExponent()));
                this.tallyKeyPair = new java.security.KeyPair(pub, priv);
                this.shares = Shamir.split(pkcs8, 3, 2, new SecureRandom());
                java.util.Arrays.fill(pkcs8, (byte) 0);
            } else {
                if (keyFile.getParent() != null) {
                    java.nio.file.Files.createDirectories(keyFile.getParent());
                }
                java.nio.file.Files.write(keyFile, tallyKeyPair.getPrivate().getEncoded());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load or persist tally key at " + keyFile, e);
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
