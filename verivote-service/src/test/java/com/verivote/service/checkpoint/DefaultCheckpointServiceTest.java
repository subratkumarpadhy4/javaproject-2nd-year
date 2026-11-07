package com.verivote.service.checkpoint;

import com.verivote.core.encoding.Base64Codec;
import com.verivote.core.encoding.Hex;
import com.verivote.core.ledger.Block;
import com.verivote.core.ledger.FileLedgerService;
import com.verivote.core.ledger.LedgerService;
import com.verivote.data.repo.CheckpointRepo;
import com.verivote.service.ServiceTestApplication;
import com.verivote.service.checkpoint.sink.ExternalSink;
import com.verivote.service.checkpoint.sink.FileSink;
import com.verivote.service.checkpoint.sink.PrintSink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = ServiceTestApplication.class)
@TestPropertySource(locations = "classpath:application-service.properties")
class DefaultCheckpointServiceTest {

    @Autowired
    private CheckpointRepo checkpointRepo;

    @TempDir
    Path tempDir;

    private LedgerService ledger;
    private Path ledgerFile;
    private FileSink fileSink;
    private KeyPair keyPair;
    private DefaultCheckpointService service;

    private static final int MIN_BLOCKS = 10;
    private static final int MIN_SECONDS = 60;

    @BeforeEach
    void setUp() throws IOException {
        checkpointRepo.deleteAll();

        ledgerFile = tempDir.resolve("test_ledger.ndjson");
        ledger = new FileLedgerService(ledgerFile);

        Path sinkDir = tempDir.resolve("sinks");
        fileSink = new FileSink(sinkDir);

        keyPair = CheckpointSigner.generateKeyPair();
        service = new DefaultCheckpointService(
                ledger,
                checkpointRepo,
                List.of(fileSink),
                1,
                keyPair.getPrivate(),
                MIN_BLOCKS,
                MIN_SECONDS
        );
    }

    private void appendBlocks(LedgerService l, int count) {
        for (int i = 0; i < count; i++) {
            byte[] ct = new byte[32];
            byte[] th = new byte[32];
            byte[] rh = new byte[32];
            ct[0] = (byte) i;
            th[0] = (byte) (i + 1);
            rh[0] = (byte) (i + 2);
            l.append(ct, th, rh);
        }
    }

    @Test
    @DisplayName("1. forceNow on empty ledger -> Optional.empty()")
    void forceNowEmptyLedger() {
        Optional<CheckpointService.Checkpoint> ckpt = service.forceNow();
        assertTrue(ckpt.isEmpty(), "forceNow on empty ledger must return Optional.empty()");
        assertEquals(0, checkpointRepo.count());
    }

    @Test
    @DisplayName("2. forceNow with 1 block -> checkpoint created, treeSize=1")
    void forceNowWithOneBlock() {
        appendBlocks(ledger, 1);
        Optional<CheckpointService.Checkpoint> ckpt = service.forceNow();

        assertTrue(ckpt.isPresent());
        assertEquals(1L, ckpt.get().treeSize());
        assertNotNull(ckpt.get().merkleRoot());
        assertNotNull(ckpt.get().headHash());
        assertNotNull(ckpt.get().signature());
        assertEquals(1, checkpointRepo.count());
    }

    @Test
    @DisplayName("3. forceNow again unchanged -> returns latest, no new DB row")
    void forceNowUnchangedReturnsLatest() {
        appendBlocks(ledger, 1);
        Optional<CheckpointService.Checkpoint> first = service.forceNow();
        assertTrue(first.isPresent());
        long dbCountBefore = checkpointRepo.count();

        Optional<CheckpointService.Checkpoint> second = service.forceNow();
        assertTrue(second.isPresent());
        assertEquals(first.get().treeSize(), second.get().treeSize());
        assertEquals(first.get().merkleRoot(), second.get().merkleRoot());
        assertEquals(dbCountBefore, checkpointRepo.count(), "No new DB row inserted when unchanged");
    }

    @Test
    @DisplayName("4. append 2 more -> forceNow -> new checkpoint, treeSize=3")
    void appendMoreCreatesNewCheckpoint() {
        appendBlocks(ledger, 1);
        service.forceNow();

        appendBlocks(ledger, 2);
        Optional<CheckpointService.Checkpoint> next = service.forceNow();

        assertTrue(next.isPresent());
        assertEquals(3L, next.get().treeSize());
        assertEquals(2, checkpointRepo.count());
    }

    @Test
    @DisplayName("5. Divergence detection: truncate ledger file, reload, forceNow -> throws")
    void divergenceDetectionThrows() throws IOException {
        appendBlocks(ledger, 2);
        service.forceNow();

        // Overwrite ledger with diverged chain (different first block)
        Files.deleteIfExists(ledgerFile);
        LedgerService divergedLedger = new FileLedgerService(ledgerFile);
        byte[] divergedCt = new byte[32];
        divergedCt[0] = (byte) 0xFF;
        divergedLedger.append(divergedCt, new byte[32], new byte[32]);

        DefaultCheckpointService divergedService = new DefaultCheckpointService(
                divergedLedger,
                checkpointRepo,
                List.of(fileSink),
                1,
                keyPair.getPrivate(),
                MIN_BLOCKS,
                MIN_SECONDS
        );

        assertThrows(IllegalStateException.class, divergedService::forceNow);
    }

    @Test
    @DisplayName("6. Sink failure: use a failing test sink, forceNow -> throws, NO DB row written")
    void sinkFailureRollsBackDb() {
        ExternalSink failingSink = new ExternalSink() {
            @Override
            public void write(CheckpointService.Checkpoint ckpt) {
                throw new SinkException("Disk full simulation");
            }

            @Override
            public String name() {
                return "FailingSink";
            }
        };

        DefaultCheckpointService failService = new DefaultCheckpointService(
                ledger,
                checkpointRepo,
                List.of(failingSink),
                1,
                keyPair.getPrivate(),
                MIN_BLOCKS,
                MIN_SECONDS
        );

        appendBlocks(ledger, 1);
        long countBefore = checkpointRepo.count();

        assertThrows(ExternalSink.SinkException.class, failService::forceNow);
        assertEquals(countBefore, checkpointRepo.count(), "DB row must NOT be written when sinks fail");
    }

    @Test
    @DisplayName("7. minExternalSinks enforcement: forceNow with 1 FileSink and minExternalSinks=1 -> succeeds; with failing sink -> throws")
    void minExternalSinksEnforcement() {
        appendBlocks(ledger, 1);

        // 1 FileSink and minExternalSinks=1 -> succeeds
        Optional<CheckpointService.Checkpoint> ok = service.forceNow();
        assertTrue(ok.isPresent());

        // With failing sink needing 2 external sinks -> throws
        ExternalSink failingSink = new ExternalSink() {
            @Override
            public void write(CheckpointService.Checkpoint ckpt) {
                throw new SinkException("Network timeout");
            }

            @Override
            public String name() {
                return "FailingSink";
            }
        };

        DefaultCheckpointService strictService = new DefaultCheckpointService(
                ledger,
                checkpointRepo,
                List.of(fileSink, failingSink),
                2,
                keyPair.getPrivate(),
                MIN_BLOCKS,
                MIN_SECONDS
        );

        appendBlocks(ledger, 1);
        assertThrows(ExternalSink.SinkException.class, strictService::forceNow);

        // PrintSink does NOT count towards external sinks
        DefaultCheckpointService printOnlyService = new DefaultCheckpointService(
                ledger,
                checkpointRepo,
                List.of(new PrintSink()),
                1,
                keyPair.getPrivate(),
                MIN_BLOCKS,
                MIN_SECONDS
        );

        appendBlocks(ledger, 1);
        assertThrows(ExternalSink.SinkException.class, printOnlyService::forceNow);
    }

    @Test
    @DisplayName("8. maybeCheckpoint with < min-blocks -> no-op")
    void maybeCheckpointBelowMinBlocks() {
        appendBlocks(ledger, MIN_BLOCKS - 1);
        service.maybeCheckpoint();

        assertTrue(service.latest().isEmpty(), "No checkpoint when below minBlocks");
        assertEquals(0, checkpointRepo.count());
    }

    @Test
    @DisplayName("9. maybeCheckpoint with >= min-blocks -> creates checkpoint")
    void maybeCheckpointAtMinBlocks() {
        appendBlocks(ledger, MIN_BLOCKS);
        service.maybeCheckpoint();

        assertTrue(service.latest().isPresent(), "Checkpoint must be created when >= minBlocks");
        assertEquals(MIN_BLOCKS, service.latest().get().treeSize());
        assertEquals(1, checkpointRepo.count());
    }

    @Test
    @DisplayName("10. Signature verifies: recompute root/head at treeSize, verify sig against ed25519 public key")
    void signatureVerifiesCorrectly() {
        appendBlocks(ledger, 3);
        Optional<CheckpointService.Checkpoint> ckptOpt = service.forceNow();
        assertTrue(ckptOpt.isPresent());

        CheckpointService.Checkpoint ckpt = ckptOpt.get();
        byte[] expectedRoot = ledger.rootAt(ckpt.treeSize());
        Block lastBlock = ledger.at(ckpt.treeSize() - 1).orElseThrow();

        assertEquals(Hex.encode(expectedRoot), ckpt.merkleRoot());
        assertEquals(lastBlock.blockHash(), ckpt.headHash());

        byte[] sigBytes = Base64Codec.decode(ckpt.signature());
        boolean verified = CheckpointSigner.verify(
                ckpt.treeSize(),
                expectedRoot,
                Hex.decode(lastBlock.blockHash()),
                sigBytes,
                keyPair.getPublic()
        );
        assertTrue(verified, "Checkpoint signature must verify against public key");
    }
}
