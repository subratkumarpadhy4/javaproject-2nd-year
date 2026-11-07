package com.verivote.client.crypto;

import com.verivote.core.crypto.Sha256;
import com.verivote.core.domain.DomainStrings;
import com.verivote.core.encoding.BigEndian;
import com.verivote.core.encoding.Hex;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class ClientCryptoTest {

    @Test
    @DisplayName("encryptVote produces 256-byte output for RSA-2048 pubkey")
    void encryptVoteProduces256Bytes() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        byte[] ballotId = ClientCrypto.randomBytes32();
        byte[] ct = ClientCrypto.encryptVote(kp.getPublic().getEncoded(), 1L, 1, ballotId);

        assertNotNull(ct);
        assertEquals(256, ct.length);
    }

    @Test
    @DisplayName("Decryption round-trip recovers electionId, candidateId, and ballotId")
    void decryptRoundTrip() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        long electionId = 42L;
        int candidateId = 2;
        byte[] ballotId = ClientCrypto.randomBytes32();

        byte[] ct = ClientCrypto.encryptVote(kp.getPublic(), electionId, candidateId, ballotId);
        assertEquals(256, ct.length);

        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.DECRYPT_MODE, kp.getPrivate());
        byte[] pt = cipher.doFinal(ct);

        assertEquals(44, pt.length);
        ByteBuffer bb = ByteBuffer.wrap(pt);
        assertEquals(electionId, bb.getLong());
        assertEquals(candidateId, bb.getInt());
        byte[] recoveredBallotId = new byte[32];
        bb.get(recoveredBallotId);
        assertArrayEquals(ballotId, recoveredBallotId);
    }

    @Test
    @DisplayName("buildTokenPreimage matches expected byte layout")
    void buildTokenPreimageMatchesLayout() {
        byte[] token = new byte[32];
        Arrays.fill(token, (byte) 0x7A);
        long eid = 100L;

        byte[] preimage = ClientCrypto.buildTokenPreimage(eid, token);
        byte[] prefix = DomainStrings.TOKEN_PREFIX.getBytes(StandardCharsets.US_ASCII);
        byte[] eidBytes = BigEndian.u64be(eid);

        assertEquals(prefix.length + 8 + 32, preimage.length);
        assertArrayEquals(prefix, Arrays.copyOfRange(preimage, 0, prefix.length));
        assertArrayEquals(eidBytes, Arrays.copyOfRange(preimage, prefix.length, prefix.length + 8));
        assertArrayEquals(token, Arrays.copyOfRange(preimage, prefix.length + 8, preimage.length));
    }

    @Test
    @DisplayName("computeSpendHash matches expected SHA-256")
    void computeSpendHashMatchesExpected() {
        byte[] token = ClientCrypto.randomBytes32();
        long eid = 1L;

        byte[] expected = Sha256.hash(
                DomainStrings.SPEND_PREFIX.getBytes(StandardCharsets.US_ASCII),
                BigEndian.u64be(eid),
                token
        );
        byte[] actual = ClientCrypto.computeSpendHash(eid, token);

        assertEquals(Hex.encode(expected), Hex.encode(actual));
    }

    @Test
    @DisplayName("computeReceiptHash matches expected SHA-256")
    void computeReceiptHashMatchesExpected() {
        byte[] nonce = ClientCrypto.randomBytes32();
        byte[] tokenHash = ClientCrypto.randomBytes32();
        long eid = 1L;

        byte[] expected = Sha256.hash(
                DomainStrings.RECEIPT_PREFIX.getBytes(StandardCharsets.US_ASCII),
                BigEndian.u64be(eid),
                nonce,
                tokenHash
        );
        byte[] actual = ClientCrypto.computeReceiptHash(eid, nonce, tokenHash);

        assertEquals(Hex.encode(expected), Hex.encode(actual));
    }

    @Test
    @DisplayName("randomBytes32 returns 32 bytes distinct on consecutive calls")
    void randomBytes32Distinct() {
        byte[] b1 = ClientCrypto.randomBytes32();
        byte[] b2 = ClientCrypto.randomBytes32();

        assertNotNull(b1);
        assertNotNull(b2);
        assertEquals(32, b1.length);
        assertEquals(32, b2.length);
        assertFalse(Arrays.equals(b1, b2));
    }
}
