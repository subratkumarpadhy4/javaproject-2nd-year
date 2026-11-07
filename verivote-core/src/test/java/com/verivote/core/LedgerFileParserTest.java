package com.verivote.core;

import com.verivote.core.crypto.MerkleTree;
import com.verivote.core.crypto.Sha256;
import com.verivote.core.encoding.Base64Codec;
import com.verivote.core.encoding.Hex;
import com.verivote.core.ledger.Block;
import com.verivote.core.ledger.BlockHash;
import com.verivote.core.ledger.LedgerFileParser;
import com.verivote.core.ledger.LedgerFileParser.ParseResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LedgerFileParserTest {

    @TempDir
    Path tempDir;

    private Block createVoteBlock(long height, String prevHash, byte[] ciphertext, List<byte[]> leaves) {
        byte[] voteHash = Sha256.hash(ciphertext);
        String voteHashHex = Hex.encode(voteHash);
        String tokenHashHex = Hex.encode(Sha256.hash(new byte[]{(byte) height}));
        String receiptHashHex = Hex.encode(Sha256.hash(new byte[]{(byte) (height + 100)}));
        String ciphertextB64 = Base64Codec.encode(ciphertext);
        long timestampSec = 1700000000L + height;

        byte[] leaf = MerkleTree.leafHash(voteHash);
        leaves.add(leaf);
        String merkleRootHex = Hex.encode(MerkleTree.rootAt(leaves, leaves.size()));

        Block withoutHash = new Block(
                height, prevHash, Block.VOTE, -1L,
                voteHashHex, tokenHashHex, receiptHashHex,
                ciphertextB64, timestampSec, merkleRootHex, ""
        );
        String hash = Hex.encode(BlockHash.compute(withoutHash));
        return new Block(
                height, prevHash, Block.VOTE, -1L,
                voteHashHex, tokenHashHex, receiptHashHex,
                ciphertextB64, timestampSec, merkleRootHex, hash
        );
    }

    private void writeBlocksToFile(Path file, List<Block> blocks, boolean trailingNewline) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < blocks.size(); i++) {
            sb.append(LedgerFileParser.toJson(blocks.get(i)));
            if (i < blocks.size() - 1 || trailingNewline) {
                sb.append("\n");
            }
        }
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("1. Empty file -> empty ParseResult, no fatal, no torn")
    void emptyFile() throws IOException {
        Path emptyFile = tempDir.resolve("empty.ndjson");
        Files.createFile(emptyFile);

        ParseResult result = LedgerFileParser.parse(emptyFile);
        assertFalse(result.isFatal());
        assertFalse(result.hasTornTail());
        assertTrue(result.blocks().isEmpty());
    }

    @Test
    @DisplayName("2. Valid 3-block file -> parsed correctly, block hashes verified, links verified")
    void validThreeBlockFile() throws IOException {
        Path ledger = tempDir.resolve("ledger.ndjson");
        List<byte[]> leaves = new ArrayList<>();
        List<Block> blocks = new ArrayList<>();

        Block b0 = createVoteBlock(0, Block.ZERO, new byte[]{1, 2, 3}, leaves);
        blocks.add(b0);
        Block b1 = createVoteBlock(1, b0.blockHash(), new byte[]{4, 5, 6}, leaves);
        blocks.add(b1);
        Block b2 = createVoteBlock(2, b1.blockHash(), new byte[]{7, 8, 9}, leaves);
        blocks.add(b2);

        writeBlocksToFile(ledger, blocks, true);

        ParseResult result = LedgerFileParser.parse(ledger);
        assertFalse(result.isFatal(), "Parse must not be fatal");
        assertFalse(result.hasTornTail());
        assertEquals(3, result.blocks().size());
        assertEquals(b0.blockHash(), result.blocks().get(0).blockHash());
        assertEquals(b1.blockHash(), result.blocks().get(1).blockHash());
        assertEquals(b2.blockHash(), result.blocks().get(2).blockHash());
    }

    @Test
    @DisplayName("3. Trailing garbage no newline -> torn tail repaired, prefix preserved, .torn file created")
    void trailingGarbageNoNewline() throws IOException {
        Path ledger = tempDir.resolve("torn_single.ndjson");
        String tornGarbage = "{\"height\":0,\"prevHash\":\"broken-json-no-newline";
        Files.writeString(ledger, tornGarbage, StandardCharsets.UTF_8);

        ParseResult result = LedgerFileParser.parse(ledger);
        assertFalse(result.isFatal());
        assertTrue(result.hasTornTail());
        assertTrue(result.blocks().isEmpty());

        Path tornFile = tempDir.resolve("torn_single.ndjson.torn");
        assertTrue(Files.exists(tornFile), ".torn file must be created");
        assertEquals(tornGarbage, Files.readString(tornFile, StandardCharsets.UTF_8));
        assertEquals(0, Files.size(ledger), "Original file should be truncated to 0");
    }

    @Test
    @DisplayName("4. Newline-terminated garbage -> fatal")
    void newlineTerminatedGarbage() throws IOException {
        Path ledger = tempDir.resolve("fatal_garbage.ndjson");
        Files.writeString(ledger, "{\"garbage\":true}\n", StandardCharsets.UTF_8);

        ParseResult result = LedgerFileParser.parse(ledger);
        assertTrue(result.isFatal(), "Newline-terminated garbage must be fatal");
        assertNotNull(result.fatal());
    }

    @Test
    @DisplayName("5. Chain hash mismatch (flip a block's blockHash) -> fatal")
    void chainHashMismatch() throws IOException {
        Path ledger = tempDir.resolve("hash_mismatch.ndjson");
        List<byte[]> leaves = new ArrayList<>();
        Block b0 = createVoteBlock(0, Block.ZERO, new byte[]{1, 2, 3}, leaves);

        // Corrupt blockHash
        String badHash = (b0.blockHash().startsWith("a") ? "b" : "a") + b0.blockHash().substring(1);
        Block badB0 = new Block(
                b0.height(), b0.prevHash(), b0.type(), b0.refHeight(),
                b0.voteHash(), b0.tokenHash(), b0.receiptHash(),
                b0.ciphertextB64(), b0.timestampSec(), b0.merkleRoot(), badHash
        );

        writeBlocksToFile(ledger, List.of(badB0), true);

        ParseResult result = LedgerFileParser.parse(ledger);
        assertTrue(result.isFatal(), "Tampered blockHash must be fatal");
        assertTrue(result.fatal().contains("CHAIN_HASH_MISMATCH"));
    }

    @Test
    @DisplayName("6. Chain link broken (flip a block's prevHash) -> fatal")
    void chainLinkBroken() throws IOException {
        Path ledger = tempDir.resolve("chain_broken.ndjson");
        List<byte[]> leaves = new ArrayList<>();
        Block b0 = createVoteBlock(0, Block.ZERO, new byte[]{1, 2, 3}, leaves);

        // b1 with invalid prevHash
        String badPrev = "f".repeat(64);
        Block b1 = createVoteBlock(1, badPrev, new byte[]{4, 5, 6}, leaves);

        writeBlocksToFile(ledger, List.of(b0, b1), true);

        ParseResult result = LedgerFileParser.parse(ledger);
        assertTrue(result.isFatal(), "Broken chain link must be fatal");
        assertTrue(result.fatal().contains("CHAIN_LINK_BROKEN"));
    }

    @Test
    @DisplayName("7. Genesis prevHash != ZERO -> fatal")
    void genesisPrevHashNonZero() throws IOException {
        Path ledger = tempDir.resolve("bad_genesis.ndjson");
        List<byte[]> leaves = new ArrayList<>();
        String nonZeroPrev = "1".repeat(64);
        Block b0 = createVoteBlock(0, nonZeroPrev, new byte[]{1, 2, 3}, leaves);

        writeBlocksToFile(ledger, List.of(b0), true);

        ParseResult result = LedgerFileParser.parse(ledger);
        assertTrue(result.isFatal(), "Genesis prevHash != ZERO must be fatal");
        assertTrue(result.fatal().contains("GENESIS_PREV_HASH_NON_ZERO"));
    }

    @Test
    @DisplayName("8. Mixed: 2 valid blocks + torn tail -> 2 blocks preserved, torn cleaned")
    void mixedValidBlocksAndTornTail() throws IOException {
        Path ledger = tempDir.resolve("mixed.ndjson");
        List<byte[]> leaves = new ArrayList<>();
        List<Block> blocks = new ArrayList<>();

        Block b0 = createVoteBlock(0, Block.ZERO, new byte[]{1, 2, 3}, leaves);
        blocks.add(b0);
        Block b1 = createVoteBlock(1, b0.blockHash(), new byte[]{4, 5, 6}, leaves);
        blocks.add(b1);

        String validContent = LedgerFileParser.toJson(b0) + "\n" + LedgerFileParser.toJson(b1) + "\n";
        String tornTail = "{\"height\":2,\"prevHash\":\"incomplete...";
        Files.writeString(ledger, validContent + tornTail, StandardCharsets.UTF_8);

        ParseResult result = LedgerFileParser.parse(ledger);
        assertFalse(result.isFatal());
        assertTrue(result.hasTornTail());
        assertEquals(2, result.blocks().size());
        assertEquals(b0.blockHash(), result.blocks().get(0).blockHash());
        assertEquals(b1.blockHash(), result.blocks().get(1).blockHash());

        Path tornFile = tempDir.resolve("mixed.ndjson.torn");
        assertTrue(Files.exists(tornFile));
        assertEquals(tornTail, Files.readString(tornFile, StandardCharsets.UTF_8));
        assertEquals(validContent, Files.readString(ledger, StandardCharsets.UTF_8));
    }
}
