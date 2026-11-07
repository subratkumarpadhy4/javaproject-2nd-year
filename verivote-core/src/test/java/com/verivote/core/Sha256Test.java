package com.verivote.core;

import com.verivote.core.crypto.Sha256;
import com.verivote.core.encoding.BigEndian;
import com.verivote.core.encoding.Hex;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class Sha256Test {

    @Test
    void hashEmptyString() {
        byte[] hash = Sha256.hash("".getBytes(StandardCharsets.UTF_8));
        assertEquals(32, hash.length);
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Hex.encode(hash));
    }

    @Test
    void hashEmptyByteArrayEqualsEmptyString() {
        byte[] hash1 = Sha256.hash(new byte[0]);
        byte[] hash2 = Sha256.hash("".getBytes(StandardCharsets.UTF_8));
        assertArrayEquals(hash1, hash2);
    }

    @Test
    void hashAbc() {
        byte[] hash = Sha256.hash("abc".getBytes(StandardCharsets.UTF_8));
        assertEquals(32, hash.length);
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Hex.encode(hash));
    }

    @Test
    void hashLongStringNist() {
        byte[] hash = Sha256.hash("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".getBytes(StandardCharsets.UTF_8));
        assertEquals(32, hash.length);
        assertEquals("248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1", Hex.encode(hash));
    }

    @Test
    void hashVarargsConcatenation() throws IOException {
        byte[] part1 = "VERIVOTE-TOKEN-v1".getBytes(StandardCharsets.UTF_8);
        byte[] part2 = BigEndian.u64be(1L);
        byte[] part3 = "token".getBytes(StandardCharsets.UTF_8);

        byte[] varargsHash = Sha256.hash(part1, part2, part3);
        assertEquals(32, varargsHash.length);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(part1);
        baos.write(part2);
        baos.write(part3);
        byte[] combinedHash = Sha256.hash(baos.toByteArray());

        assertArrayEquals(combinedHash, varargsHash);
    }

    @Test
    void hashVarargsEmptyReturnsEmptyHash() {
        byte[] hash = Sha256.hash();
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Hex.encode(hash));
    }

    @Test
    void hashNullInputThrows() {
        assertThrows(IllegalArgumentException.class, () -> Sha256.hash((byte[]) null));
    }

    @Test
    void hashVarargsNullArrayThrows() {
        assertThrows(IllegalArgumentException.class, () -> Sha256.hash((byte[][]) null));
    }

    @Test
    void hashVarargsNullElementThrows() {
        byte[] valid = "part".getBytes(StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> Sha256.hash(valid, null));
    }
}
