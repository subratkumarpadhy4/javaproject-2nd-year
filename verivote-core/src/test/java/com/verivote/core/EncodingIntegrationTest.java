package com.verivote.core;

import com.verivote.core.crypto.Sha256;
import com.verivote.core.encoding.Base64Codec;
import com.verivote.core.encoding.BigEndian;
import com.verivote.core.encoding.Hex;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class EncodingIntegrationTest {

    @Test
    void hexSha256CrossPrimitive() {
        byte[] hash = Sha256.hash("abc".getBytes(StandardCharsets.UTF_8));
        String hex = Hex.encode(hash);
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hex);
    }

    @Test
    void preimageHashMatchesHandComputedHex() {
        byte[] prefix = "VERIVOTE-TOKEN-v1".getBytes(StandardCharsets.UTF_8);
        byte[] eidBytes = BigEndian.u64be(12345L);
        byte[] tokenPlain = "sample-token-data".getBytes(StandardCharsets.UTF_8);

        byte[] hash = Sha256.hash(prefix, eidBytes, tokenPlain);
        String hex = Hex.encode(hash);
        assertEquals("a17d3c9f44a837527ecb45336a1a2654ba533022f53e3eed7807168f568932bc", hex);
    }

    @Test
    void base64Sha256RoundTrip() {
        byte[] hash = Sha256.hash("abc".getBytes(StandardCharsets.UTF_8));
        String b64 = Base64Codec.encode(hash);
        byte[] decoded = Base64Codec.decode(b64);
        assertArrayEquals(hash, decoded);
    }

    @Test
    void randomBytesRoundTripLoop() {
        Random rnd = new Random(12345);
        for (int i = 0; i < 100; i++) {
            byte[] data = new byte[rnd.nextInt(128)];
            rnd.nextBytes(data);

            byte[] h1 = Sha256.hash(data);
            String hex1 = Hex.encode(h1);
            byte[] decoded = Hex.decode(hex1);
            assertArrayEquals(h1, decoded);

            byte[] h2 = Sha256.hash(decoded);
            String hex2 = Hex.encode(h2);

            byte[] h1Again = Sha256.hash(data);
            assertEquals(Hex.encode(h1Again), hex1);
            assertEquals(32, decoded.length);
        }
    }
}
