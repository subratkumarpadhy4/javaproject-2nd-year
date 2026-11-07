package com.verivote.core;

import com.verivote.core.crypto.Hmac;
import com.verivote.core.encoding.Hex;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class HmacTest {

    private static byte[] repeatByte(byte b, int length) {
        byte[] arr = new byte[length];
        Arrays.fill(arr, b);
        return arr;
    }

    @Test
    void testCase1() {
        byte[] key = repeatByte((byte) 0x0b, 20);
        byte[] data = "Hi There".getBytes(StandardCharsets.UTF_8);
        byte[] result = Hmac.sha256(key, data);
        assertEquals(32, result.length);
        assertEquals("b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7", Hex.encode(result));
    }

    @Test
    void testCase2() {
        byte[] key = "Jefe".getBytes(StandardCharsets.UTF_8);
        byte[] data = "what do ya want for nothing?".getBytes(StandardCharsets.UTF_8);
        byte[] result = Hmac.sha256(key, data);
        assertEquals(32, result.length);
        assertEquals("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843", Hex.encode(result));
    }

    @Test
    void testCase3() {
        byte[] key = repeatByte((byte) 0xaa, 20);
        byte[] data = repeatByte((byte) 0xdd, 50);
        byte[] result = Hmac.sha256(key, data);
        assertEquals(32, result.length);
        assertEquals("773ea91e36800e46854db8ebd09181a72959098b3ef8c122d9635514ced565fe", Hex.encode(result));
    }

    @Test
    void testCase4() {
        byte[] key = new byte[25];
        for (int i = 0; i < 25; i++) {
            key[i] = (byte) (i + 1);
        }
        byte[] data = repeatByte((byte) 0xcd, 50);
        byte[] result = Hmac.sha256(key, data);
        assertEquals(32, result.length);
        assertEquals("82558a389a443c0ea4cc819899f2083a85f0faa3e578f8077a2e3ff46729665b", Hex.encode(result));
    }

    @Test
    void testCase5() {
        byte[] key = repeatByte((byte) 0x0c, 20);
        byte[] data = "Test With Truncation".getBytes(StandardCharsets.UTF_8);
        byte[] result = Hmac.sha256(key, data);
        assertEquals(32, result.length);
        String hex = Hex.encode(result);
        assertTrue(hex.startsWith("a3b6167473100ee06e0c796c2955552b"));
    }

    @Test
    void testCase6() {
        byte[] key = repeatByte((byte) 0xaa, 131);
        byte[] data = "Test Using Larger Than Block-Size Key - Hash Key First".getBytes(StandardCharsets.UTF_8);
        byte[] result = Hmac.sha256(key, data);
        assertEquals(32, result.length);
        assertEquals("60e431591ee0b67f0d8a26aacbf5b77f8e0bc6213728c5140546040f0ee37f54", Hex.encode(result));
    }

    @Test
    void testCase7() {
        byte[] key = repeatByte((byte) 0xaa, 131);
        byte[] data = "This is a test using a larger than block-size key and a larger than block-size data. The key needs to be hashed before being used by the HMAC algorithm.".getBytes(StandardCharsets.UTF_8);
        byte[] result = Hmac.sha256(key, data);
        assertEquals(32, result.length);
        assertEquals("9b09ffa71b942fcb27635fbcd5b0e944bfdc63644f0713938a7f51535c3a35e2", Hex.encode(result));
    }

    @Test
    void nullKeyThrows() {
        byte[] data = "message".getBytes(StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> Hmac.sha256(null, data));
    }

    @Test
    void nullMessageThrows() {
        byte[] key = "key".getBytes(StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> Hmac.sha256(key, null));
    }

    @Test
    void emptyKeyDoesNotThrow() {
        byte[] key = new byte[0];
        byte[] data = "message".getBytes(StandardCharsets.UTF_8);
        byte[] result = Hmac.sha256(key, data);
        assertNotNull(result);
        assertEquals(32, result.length);
    }
}
