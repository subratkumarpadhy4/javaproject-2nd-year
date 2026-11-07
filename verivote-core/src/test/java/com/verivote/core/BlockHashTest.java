package com.verivote.core;

import com.verivote.core.crypto.Sha256;
import com.verivote.core.encoding.BigEndian;
import com.verivote.core.encoding.Hex;
import com.verivote.core.ledger.Block;
import com.verivote.core.ledger.BlockHash;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BlockHashTest {

    private static final String ZERO = "0".repeat(64);

    private static String hex32(int seed) {
        byte[] b = new byte[32];
        for (int i = 0; i < 32; i++) {
            b[i] = (byte) (seed + i);
        }
        return Hex.encode(b);
    }

    private static Block voteBlock(
            long height, String prevHash, long refHeight,
            String voteHash, String tokenHash, String receiptHash,
            String ciphertextB64, long timestampSec, String merkleRoot
    ) {
        return new Block(
                height, prevHash, Block.VOTE, refHeight,
                voteHash, tokenHash, receiptHash,
                ciphertextB64, timestampSec, merkleRoot, ""
        );
    }

    private static Block orphanBlock(long height, String prevHash, long refHeight, long timestampSec, String merkleRoot) {
        return new Block(
                height, prevHash, Block.ORPHAN, refHeight,
                ZERO, ZERO, ZERO,
                null, timestampSec, merkleRoot, ""
        );
    }

    // ── 1. Golden vector — VOTE block ───────────────────────────────────────

    @Test
    void goldenVectorVoteBlock() throws IOException {
        Block block = voteBlock(
                1L, ZERO, -1L,
                hex32(0x10), hex32(0x30), hex32(0x50),
                "AAAA", 1700000000L, hex32(0x70)
        );

        // Independently assembled byte concatenation for the preimage
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write("VERIVOTE-BLOCK-v1".getBytes(StandardCharsets.UTF_8));
        baos.write(1); // TYPE_BYTE_VOTE
        baos.write(BigEndian.u64be(1L));
        baos.write(BigEndian.u64be(-1L));
        baos.write(Hex.decode(ZERO));
        baos.write(Hex.decode(hex32(0x10)));
        baos.write(Hex.decode(hex32(0x30)));
        baos.write(Hex.decode(hex32(0x50)));
        baos.write(BigEndian.u64be(1700000000L));
        baos.write(Hex.decode(hex32(0x70)));
        byte[] expected = Sha256.hash(baos.toByteArray());

        byte[] actual = BlockHash.compute(block);
        assertEquals(32, actual.length);
        assertArrayEquals(expected, actual);
        // Golden hash hex verification: a673fa95f7223477c566fbb92c0fe84d04ecafdb8ac8ac9870047453af882e90
        assertEquals("a673fa95f7223477c566fbb92c0fe84d04ecafdb8ac8ac9870047453af882e90", Hex.encode(actual));
    }

    // ── 2. Field sensitivity ────────────────────────────────────────────────

    @Test
    void fieldSensitivity() {
        Block base = voteBlock(
                1L, ZERO, -1L,
                hex32(0x10), hex32(0x30), hex32(0x50),
                "AAAA", 1700000000L, hex32(0x70)
        );
        byte[] baseHash = BlockHash.compute(base);

        Block modHeight = voteBlock(
                2L, ZERO, -1L,
                hex32(0x10), hex32(0x30), hex32(0x50),
                "AAAA", 1700000000L, hex32(0x70)
        );
        assertFalse(Arrays.equals(baseHash, BlockHash.compute(modHeight)));

        Block modRefHeight = voteBlock(
                1L, ZERO, -2L,
                hex32(0x10), hex32(0x30), hex32(0x50),
                "AAAA", 1700000000L, hex32(0x70)
        );
        assertFalse(Arrays.equals(baseHash, BlockHash.compute(modRefHeight)));

        Block modTimestamp = voteBlock(
                1L, ZERO, -1L,
                hex32(0x10), hex32(0x30), hex32(0x50),
                "AAAA", 1700000001L, hex32(0x70)
        );
        assertFalse(Arrays.equals(baseHash, BlockHash.compute(modTimestamp)));

        Block modPrevHash = voteBlock(
                1L, hex32(0x01), -1L,
                hex32(0x10), hex32(0x30), hex32(0x50),
                "AAAA", 1700000000L, hex32(0x70)
        );
        assertFalse(Arrays.equals(baseHash, BlockHash.compute(modPrevHash)));

        Block modVoteHash = voteBlock(
                1L, ZERO, -1L,
                hex32(0x11), hex32(0x30), hex32(0x50),
                "AAAA", 1700000000L, hex32(0x70)
        );
        assertFalse(Arrays.equals(baseHash, BlockHash.compute(modVoteHash)));

        Block modTokenHash = voteBlock(
                1L, ZERO, -1L,
                hex32(0x10), hex32(0x31), hex32(0x50),
                "AAAA", 1700000000L, hex32(0x70)
        );
        assertFalse(Arrays.equals(baseHash, BlockHash.compute(modTokenHash)));

        Block modReceiptHash = voteBlock(
                1L, ZERO, -1L,
                hex32(0x10), hex32(0x30), hex32(0x51),
                "AAAA", 1700000000L, hex32(0x70)
        );
        assertFalse(Arrays.equals(baseHash, BlockHash.compute(modReceiptHash)));

        Block modMerkleRoot = voteBlock(
                1L, ZERO, -1L,
                hex32(0x10), hex32(0x30), hex32(0x50),
                "AAAA", 1700000000L, hex32(0x71)
        );
        assertFalse(Arrays.equals(baseHash, BlockHash.compute(modMerkleRoot)));

        // Ensure all 9 hashes (base + 8 modified) are mutually unique
        Set<String> uniqueHashes = new HashSet<>();
        uniqueHashes.add(Hex.encode(baseHash));
        uniqueHashes.add(Hex.encode(BlockHash.compute(modHeight)));
        uniqueHashes.add(Hex.encode(BlockHash.compute(modRefHeight)));
        uniqueHashes.add(Hex.encode(BlockHash.compute(modTimestamp)));
        uniqueHashes.add(Hex.encode(BlockHash.compute(modPrevHash)));
        uniqueHashes.add(Hex.encode(BlockHash.compute(modVoteHash)));
        uniqueHashes.add(Hex.encode(BlockHash.compute(modTokenHash)));
        uniqueHashes.add(Hex.encode(BlockHash.compute(modReceiptHash)));
        uniqueHashes.add(Hex.encode(BlockHash.compute(modMerkleRoot)));
        assertEquals(9, uniqueHashes.size());
    }

    // ── 3. Type byte isolation ──────────────────────────────────────────────

    @Test
    void typeByteIsolation() {
        Block vote = new Block(
                1L, ZERO, Block.VOTE, -1L,
                hex32(0x10), hex32(0x30), hex32(0x50),
                "AAAA", 1700000000L, hex32(0x70), ""
        );
        Block orphanWithSameFields = new Block(
                1L, ZERO, Block.ORPHAN, -1L,
                hex32(0x10), hex32(0x30), hex32(0x50),
                "AAAA", 1700000000L, hex32(0x70), ""
        );

        byte[] voteHash = BlockHash.compute(vote);
        byte[] orphanHash = BlockHash.compute(orphanWithSameFields);
        assertFalse(Arrays.equals(voteHash, orphanHash));
    }

    // ── 4. ciphertextB64 is NOT in the hash ──────────────────────────────────

    @Test
    void ciphertextB64NotInHash() {
        Block blockA = voteBlock(
                1L, ZERO, -1L,
                hex32(0x10), hex32(0x30), hex32(0x50),
                "AAAA", 1700000000L, hex32(0x70)
        );
        Block blockB = voteBlock(
                1L, ZERO, -1L,
                hex32(0x10), hex32(0x30), hex32(0x50),
                "BBBBB", 1700000000L, hex32(0x70)
        );

        assertArrayEquals(BlockHash.compute(blockA), BlockHash.compute(blockB));
    }

    // ── 5. ORPHAN block with ZERO hashes ────────────────────────────────────

    @Test
    void orphanBlockWithZeroHashes() {
        Block orphan = orphanBlock(
                5L, hex32(0x01), 3L, 1700000000L, hex32(0x70)
        );
        byte[] hash1 = BlockHash.compute(orphan);
        byte[] hash2 = BlockHash.compute(orphan);

        assertEquals(32, hash1.length);
        assertArrayEquals(hash1, hash2);
    }

    // ── 6. Malformed input ──────────────────────────────────────────────────

    @Test
    void malformedInput() {
        assertThrows(IllegalArgumentException.class, () -> BlockHash.compute(null));

        Block invalidType = new Block(
                1L, ZERO, "GENESIS", -1L,
                hex32(0x10), hex32(0x30), hex32(0x50),
                "AAAA", 1700000000L, hex32(0x70), ""
        );
        assertThrows(IllegalArgumentException.class, () -> BlockHash.compute(invalidType));

        Block nullType = new Block(
                1L, ZERO, null, -1L,
                hex32(0x10), hex32(0x30), hex32(0x50),
                "AAAA", 1700000000L, hex32(0x70), ""
        );
        assertThrows(IllegalArgumentException.class, () -> BlockHash.compute(nullType));

        Block nonHexPrev = new Block(
                1L, "zz".repeat(32), Block.VOTE, -1L,
                hex32(0x10), hex32(0x30), hex32(0x50),
                "AAAA", 1700000000L, hex32(0x70), ""
        );
        assertThrows(IllegalArgumentException.class, () -> BlockHash.compute(nonHexPrev));

        Block oddLenVote = new Block(
                1L, ZERO, Block.VOTE, -1L,
                "abc", hex32(0x30), hex32(0x50),
                "AAAA", 1700000000L, hex32(0x70), ""
        );
        assertThrows(IllegalArgumentException.class, () -> BlockHash.compute(oddLenVote));

        String hex31Bytes = "00".repeat(31); // 62 characters = 31 bytes
        Block shortMerkle = new Block(
                1L, ZERO, Block.VOTE, -1L,
                hex32(0x10), hex32(0x30), hex32(0x50),
                "AAAA", 1700000000L, hex31Bytes, ""
        );
        assertThrows(IllegalArgumentException.class, () -> BlockHash.compute(shortMerkle));
    }

    // ── 7. Determinism ──────────────────────────────────────────────────────

    @Test
    void determinism100Runs() {
        Block block = voteBlock(
                1L, ZERO, -1L,
                hex32(0x10), hex32(0x30), hex32(0x50),
                "AAAA", 1700000000L, hex32(0x70)
        );
        byte[] expected = BlockHash.compute(block);
        for (int i = 0; i < 100; i++) {
            assertArrayEquals(expected, BlockHash.compute(block));
        }
    }
}
