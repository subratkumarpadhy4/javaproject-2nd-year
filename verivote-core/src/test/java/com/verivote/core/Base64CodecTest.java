package com.verivote.core;

import com.verivote.core.encoding.Base64Codec;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class Base64CodecTest {

    @Test
    void encodeKnownString() {
        assertEquals("SGk=", Base64Codec.encode("Hi".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void encodeEmpty() {
        assertEquals("", Base64Codec.encode(new byte[0]));
    }

    @Test
    void decodeKnownString() {
        assertArrayEquals("Hi".getBytes(StandardCharsets.UTF_8), Base64Codec.decode("SGk="));
    }

    @Test
    void decodeRejectsInvalidBase64() {
        assertThrows(IllegalArgumentException.class, () -> Base64Codec.decode("!!!"));
    }

    @Test
    void decodeRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> Base64Codec.decode(null));
    }

    @Test
    void encodeRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> Base64Codec.encode(null));
    }

    @Test
    void roundTripRandomByteArrays() {
        Random rnd = new Random(42);
        for (int i = 0; i < 100; i++) {
            int len = rnd.nextInt(65);
            byte[] original = new byte[len];
            rnd.nextBytes(original);
            String encoded = Base64Codec.encode(original);
            byte[] decoded = Base64Codec.decode(encoded);
            assertArrayEquals(original, decoded);
        }
    }
}
