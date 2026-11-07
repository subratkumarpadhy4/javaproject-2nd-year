package com.verivote.core;

import com.verivote.core.encoding.Hex;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class HexTest {

    @Test
    void encodeEmpty() {
        assertEquals("", Hex.encode(new byte[0]));
    }

    @Test
    void encodeKnownBytes() {
        byte[] bytes = new byte[] {0x00, 0x0f, (byte) 0xde, (byte) 0xad};
        assertEquals("000fdead", Hex.encode(bytes));
    }

    @Test
    void encodeEnforcesLowercase() {
        byte[] bytes = new byte[] {(byte) 0xff};
        assertEquals("ff", Hex.encode(bytes));
    }

    @Test
    void decodeKnownString() {
        byte[] expected = new byte[] {0x00, 0x0f, (byte) 0xde, (byte) 0xad};
        assertArrayEquals(expected, Hex.decode("000fdead"));
    }

    @Test
    void decodeEmpty() {
        assertArrayEquals(new byte[0], Hex.decode(""));
    }

    @Test
    void decodeRejectsUppercase() {
        assertThrows(IllegalArgumentException.class, () -> Hex.decode("DEADBEEF"));
    }

    @Test
    void decodeRejectsOddLength() {
        assertThrows(IllegalArgumentException.class, () -> Hex.decode("abc"));
    }

    @Test
    void decodeRejectsInvalidChar() {
        assertThrows(IllegalArgumentException.class, () -> Hex.decode("zz"));
    }

    @Test
    void decodeRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> Hex.decode(null));
    }

    @Test
    void encodeRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> Hex.encode(null));
    }

    @Test
    void roundTripRandomByteArrays() {
        Random rnd = new Random(42);
        for (int i = 0; i < 100; i++) {
            int len = rnd.nextInt(65);
            byte[] original = new byte[len];
            rnd.nextBytes(original);
            String encoded = Hex.encode(original);
            assertEquals(len * 2, encoded.length());
            byte[] decoded = Hex.decode(encoded);
            assertArrayEquals(original, decoded);
        }
    }
}
