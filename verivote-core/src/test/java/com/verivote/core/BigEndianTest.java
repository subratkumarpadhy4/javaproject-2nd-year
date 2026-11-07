package com.verivote.core;

import com.verivote.core.encoding.BigEndian;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BigEndianTest {

    @Test
    void u64beOne() {
        byte[] expected = new byte[] {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01};
        assertArrayEquals(expected, BigEndian.u64be(1L));
        assertEquals(8, BigEndian.u64be(1L).length);
    }

    @Test
    void u64beZero() {
        byte[] expected = new byte[] {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        assertArrayEquals(expected, BigEndian.u64be(0L));
        assertEquals(8, BigEndian.u64be(0L).length);
    }

    @Test
    void u64beMinusOne() {
        byte[] expected = new byte[] {
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF
        };
        assertArrayEquals(expected, BigEndian.u64be(-1L));
        assertEquals(8, BigEndian.u64be(-1L).length);
    }

    @Test
    void u64beSequentialPattern() {
        byte[] expected = new byte[] {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08};
        assertArrayEquals(expected, BigEndian.u64be(0x0102030405060708L));
        assertEquals(8, BigEndian.u64be(0x0102030405060708L).length);
    }

    @Test
    void u32beOne() {
        byte[] expected = new byte[] {0x00, 0x00, 0x00, 0x01};
        assertArrayEquals(expected, BigEndian.u32be(1L));
        assertEquals(4, BigEndian.u32be(1L).length);
    }

    @Test
    void u32beAllOnes() {
        byte[] expected = new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
        assertArrayEquals(expected, BigEndian.u32be(0xFFFFFFFFL));
        assertEquals(4, BigEndian.u32be(0xFFFFFFFFL).length);
    }

    @Test
    void u32beMaskingHighBits() {
        byte[] expected = new byte[] {0x00, 0x00, 0x00, 0x00};
        assertArrayEquals(expected, BigEndian.u32be(0x1_0000_0000L));
        assertEquals(4, BigEndian.u32be(0x1_0000_0000L).length);
    }

    @Test
    void int64beEqualsU64beForMinusOne() {
        assertArrayEquals(BigEndian.u64be(-1L), BigEndian.int64be(-1L));
        assertEquals(8, BigEndian.int64be(-1L).length);
    }

    @Test
    void int64beEqualsU64beForPattern() {
        long val = 0x0102030405060708L;
        assertArrayEquals(BigEndian.u64be(val), BigEndian.int64be(val));
        assertEquals(8, BigEndian.int64be(val).length);
    }
}
