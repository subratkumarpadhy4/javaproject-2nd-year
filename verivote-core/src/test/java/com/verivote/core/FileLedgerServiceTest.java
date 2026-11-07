package com.verivote.core;

import com.verivote.core.crypto.MerkleTree;
import com.verivote.core.crypto.MerkleVerifier;
import com.verivote.core.crypto.Sha256;
import com.verivote.core.encoding.Base64Codec;
import com.verivote.core.encoding.Hex;
import com.verivote.core.ledger.Block;
import com.verivote.core.ledger.FileLedgerService;
import com.verivote.core.ledger.LedgerFileParser;
import com.verivote.core.ledger.LedgerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class FileLedgerServiceTest {

    @TempDir
    Path tempDir;

    private static byte[] hash(int val) {
        return Sha256.hash(new byte[]{(byte) val});
    }

    @Test
    @DisplayName("1. New empty file -> blockCount()==0, headHash()==ZERO")
    void newEmptyFile() {
        Path ledgerPath = tempDir.resolve("ledger1.ndjson");
        LedgerService service = new FileLedgerService(ledgerPath);

        assertEquals(0, service.blockCount());
        assertEquals(0, service.realVoteCount());
        assertEquals(0, service.orphanCount());
        assertEquals(Block.ZERO, service.headHash());
    }

    @Test
    @DisplayName("2. append 3 votes -> blockCount()==3, each block links correctly, file on disk contains 3 lines")
    void appendThreeVotes() throws IOException {
        Path ledgerPath = tempDir.resolve("ledger2.ndjson");
        LedgerService service = new FileLedgerService(ledgerPath);

        byte[] ct1 = "cipher1".getBytes(StandardCharsets.UTF_8);
        byte[] ct2 = "cipher2".getBytes(StandardCharsets.UTF_8);
        byte[] ct3 = "cipher3".getBytes(StandardCharsets.UTF_8);

        LedgerService.Appended a0 = service.append(ct1, hash(1), hash(10));
        LedgerService.Appended a1 = service.append(ct2, hash(2), hash(20));
        LedgerService.Appended a2 = service.append(ct3, hash(3), hash(30));

        assertEquals(0L, a0.height());
        assertEquals(1L, a1.height());
        assertEquals(2L, a2.height());
        assertEquals(3L, service.blockCount());

        Block b0 = service.at(0).orElseThrow();
        Block b1 = service.at(1).orElseThrow();
        Block b2 = service.at(2).orElseThrow();

        assertEquals(Block.ZERO, b0.prevHash());
        assertEquals(b0.blockHash(), b1.prevHash());
        assertEquals(b1.blockHash(), b2.prevHash());

        List<String> lines = Files.readAllLines(ledgerPath, StandardCharsets.UTF_8);
        assertEquals(3, lines.size());
    }

    @Test
    @DisplayName("3. kill/restart simulation: build a new FileLedgerService on the same file -> loads 3 blocks, state preserved")
    void restartSimulation() {
        Path ledgerPath = tempDir.resolve("ledger3.ndjson");
        LedgerService service1 = new FileLedgerService(ledgerPath);

        service1.append("c1".getBytes(StandardCharsets.UTF_8), hash(1), hash(10));
        service1.append("c2".getBytes(StandardCharsets.UTF_8), hash(2), hash(20));
        service1.append("c3".getBytes(StandardCharsets.UTF_8), hash(3), hash(30));

        String headBefore = service1.headHash();
        assertEquals(3L, service1.blockCount());

        // Restart with fresh service instance
        LedgerService service2 = new FileLedgerService(ledgerPath);
        assertEquals(3L, service2.blockCount());
        assertEquals(headBefore, service2.headHash());
        assertEquals(service1.at(0).get(), service2.at(0).get());
        assertEquals(service1.at(1).get(), service2.at(1).get());
        assertEquals(service1.at(2).get(), service2.at(2).get());
    }

    @Test
    @DisplayName("4. append then markOrphan(height=1) -> blockCount()==4, realVoteCount()==2, orphanCount()==1")
    void appendAndMarkOrphan() {
        Path ledgerPath = tempDir.resolve("ledger4.ndjson");
        LedgerService service = new FileLedgerService(ledgerPath);

        service.append("c0".getBytes(StandardCharsets.UTF_8), hash(0), hash(10));
        service.append("c1".getBytes(StandardCharsets.UTF_8), hash(1), hash(11));
        service.append("c2".getBytes(StandardCharsets.UTF_8), hash(2), hash(12));

        service.markOrphan(1L);

        assertEquals(4L, service.blockCount());
        assertEquals(2L, service.realVoteCount());
        assertEquals(1L, service.orphanCount());
        assertTrue(service.isOrphaned(1L));
        assertFalse(service.isOrphaned(0L));
        assertFalse(service.isOrphaned(2L));

        Block orphanBlock = service.at(3).orElseThrow();
        assertEquals(Block.ORPHAN, orphanBlock.type());
        assertEquals(1L, orphanBlock.refHeight());
        assertEquals(Block.ZERO, orphanBlock.voteHash());
    }

    @Test
    @DisplayName("5. markOrphan idempotent: call twice, blockCount()==4 (only one ORPHAN block added), no error")
    void markOrphanIdempotent() {
        Path ledgerPath = tempDir.resolve("ledger5.ndjson");
        LedgerService service = new FileLedgerService(ledgerPath);

        service.append("c0".getBytes(StandardCharsets.UTF_8), hash(0), hash(10));
        service.append("c1".getBytes(StandardCharsets.UTF_8), hash(1), hash(11));
        service.append("c2".getBytes(StandardCharsets.UTF_8), hash(2), hash(12));

        service.markOrphan(1L);
        service.markOrphan(1L);

        assertEquals(4L, service.blockCount());
        assertEquals(2L, service.realVoteCount());
        assertEquals(1L, service.orphanCount());
    }

    @Test
    @DisplayName("6. rootAt(0) == EMPTY_ROOT")
    void rootAtZero() {
        Path ledgerPath = tempDir.resolve("ledger6.ndjson");
        LedgerService service = new FileLedgerService(ledgerPath);

        assertArrayEquals(MerkleTree.EMPTY_ROOT, service.rootAt(0));
    }

    @Test
    @DisplayName("7. rootAt(3) == head.merkleRoot at height 2 (3-block state)")
    void rootAtThree() {
        Path ledgerPath = tempDir.resolve("ledger7.ndjson");
        LedgerService service = new FileLedgerService(ledgerPath);

        service.append("c0".getBytes(StandardCharsets.UTF_8), hash(0), hash(10));
        service.append("c1".getBytes(StandardCharsets.UTF_8), hash(1), hash(11));
        service.append("c2".getBytes(StandardCharsets.UTF_8), hash(2), hash(12));

        Block b2 = service.at(2).orElseThrow();
        byte[] expectedRoot = Hex.decode(b2.merkleRoot());
        byte[] actualRoot = service.rootAt(3);

        assertArrayEquals(expectedRoot, actualRoot);
    }

    @Test
    @DisplayName("8. proof for block 0 in 3-block tree verifies against rootAt(3)")
    void proofForBlockZero() {
        Path ledgerPath = tempDir.resolve("ledger8.ndjson");
        LedgerService service = new FileLedgerService(ledgerPath);

        service.append("c0".getBytes(StandardCharsets.UTF_8), hash(0), hash(10));
        service.append("c1".getBytes(StandardCharsets.UTF_8), hash(1), hash(11));
        service.append("c2".getBytes(StandardCharsets.UTF_8), hash(2), hash(12));

        Block b0 = service.at(0).orElseThrow();
        byte[] leaf0 = MerkleTree.leafHash(Hex.decode(b0.voteHash()));
        byte[] root3 = service.rootAt(3);
        List<byte[]> proof = service.proof(0, 3);

        assertTrue(MerkleVerifier.verify(leaf0, 0, 3, proof, root3));
    }

    @Test
    @DisplayName("9. Concurrency test: 20 threads append simultaneously -> 20 sequential heights 0..19, no corruption, file has 20 valid lines, all hashes correct")
    void concurrentAppends() throws InterruptedException, IOException {
        Path ledgerPath = tempDir.resolve("ledger9.ndjson");
        LedgerService service = new FileLedgerService(ledgerPath);

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger errors = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    byte[] ct = ("cipher-" + idx).getBytes(StandardCharsets.UTF_8);
                    byte[] tok = hash(idx);
                    byte[] rcpt = hash(idx + 100);
                    service.append(ct, tok, rcpt);
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        assertEquals(0, errors.get());
        assertEquals(20L, service.blockCount());

        // Verify sequential heights 0..19
        for (int i = 0; i < 20; i++) {
            assertEquals((long) i, service.at(i).orElseThrow().height());
        }

        // Verify file on disk
        List<String> lines = Files.readAllLines(ledgerPath, StandardCharsets.UTF_8);
        assertEquals(20, lines.size());

        // Parse with LedgerFileParser
        LedgerFileParser.ParseResult parsed = LedgerFileParser.parse(ledgerPath);
        assertFalse(parsed.isFatal());
        assertEquals(20, parsed.blocks().size());
    }

    @Test
    @DisplayName("10. Ciphertext mismatch on load: append a vote, manually corrupt ciphertextB64 in file, restart -> throws IllegalStateException")
    void ciphertextMismatchOnLoad() throws IOException {
        Path ledgerPath = tempDir.resolve("ledger10.ndjson");
        LedgerService service = new FileLedgerService(ledgerPath);

        service.append("valid ciphertext".getBytes(StandardCharsets.UTF_8), hash(1), hash(2));
        Block b0 = service.at(0).orElseThrow();

        // Corrupt ciphertextB64 on disk while keeping voteHash and blockHash
        String corruptedCtB64 = Base64Codec.encode("tampered ciphertext".getBytes(StandardCharsets.UTF_8));
        Block corruptedBlock = new Block(
                b0.height(), b0.prevHash(), b0.type(), b0.refHeight(),
                b0.voteHash(), b0.tokenHash(), b0.receiptHash(),
                corruptedCtB64, b0.timestampSec(), b0.merkleRoot(), b0.blockHash()
        );

        Files.writeString(ledgerPath, LedgerFileParser.toJson(corruptedBlock) + "\n", StandardCharsets.UTF_8);

        // Restarting FileLedgerService must detect the mismatch and throw IllegalStateException
        assertThrows(IllegalStateException.class, () -> new FileLedgerService(ledgerPath));
    }
}
