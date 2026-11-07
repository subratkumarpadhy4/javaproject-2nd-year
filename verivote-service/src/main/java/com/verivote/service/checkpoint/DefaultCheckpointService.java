package com.verivote.service.checkpoint;

import com.verivote.core.encoding.Base64Codec;
import com.verivote.core.encoding.Hex;
import com.verivote.core.ledger.Block;
import com.verivote.core.ledger.LedgerService;
import com.verivote.data.repo.CheckpointRepo;
import com.verivote.service.checkpoint.sink.ExternalSink;
import com.verivote.service.checkpoint.sink.PrintSink;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class DefaultCheckpointService implements CheckpointService {

    private final LedgerService ledger;
    private final CheckpointRepo checkpointRepo;
    private final List<ExternalSink> sinks;
    private final int minExternalSinks;
    private final PrivateKey ed25519PrivateKey;
    private final PublicKey ed25519PublicKey;
    private final int minBlocks;
    private final int minSecondsWithNewBlocks;

    @Autowired
    public DefaultCheckpointService(
            LedgerService ledger,
            CheckpointRepo checkpointRepo,
            @Autowired(required = false) List<ExternalSink> sinks,
            @Value("${verivote.checkpoint.min-external-sinks:1}") int minExternalSinks,
            @Value("${verivote.checkpoint.ed25519-priv-key:}") String ed25519PrivKeyB64,
            @Value("${verivote.checkpoint.min-blocks:10}") int minBlocks,
            @Value("${verivote.checkpoint.min-seconds-with-new-blocks:60}") int minSecondsWithNewBlocks
    ) {
        this.ledger = ledger;
        this.checkpointRepo = checkpointRepo;
        this.sinks = (sinks != null) ? new ArrayList<>(sinks) : new ArrayList<>();
        this.minExternalSinks = minExternalSinks;
        this.minBlocks = minBlocks;
        this.minSecondsWithNewBlocks = minSecondsWithNewBlocks;

        if (ed25519PrivKeyB64 != null && !ed25519PrivKeyB64.isBlank()) {
            this.ed25519PrivateKey = CheckpointSigner.decodePrivateKey(ed25519PrivKeyB64.trim());
            this.ed25519PublicKey = null;
        } else {
            KeyPair kp = CheckpointSigner.generateKeyPair();
            this.ed25519PrivateKey = kp.getPrivate();
            this.ed25519PublicKey = kp.getPublic();
        }
    }

    public DefaultCheckpointService(
            LedgerService ledger,
            CheckpointRepo checkpointRepo,
            List<ExternalSink> sinks,
            int minExternalSinks,
            PrivateKey privateKey,
            int minBlocks,
            int minSecondsWithNewBlocks
    ) {
        this.ledger = ledger;
        this.checkpointRepo = checkpointRepo;
        this.sinks = (sinks != null) ? new ArrayList<>(sinks) : new ArrayList<>();
        this.minExternalSinks = minExternalSinks;
        this.minBlocks = minBlocks;
        this.minSecondsWithNewBlocks = minSecondsWithNewBlocks;
        this.ed25519PrivateKey = privateKey;
        this.ed25519PublicKey = null;
    }

    public PrivateKey getPrivateKey() {
        return ed25519PrivateKey;
    }

    public PublicKey getPublicKey() {
        return ed25519PublicKey;
    }

    public List<ExternalSink> getSinks() {
        return sinks;
    }

    @Override
    public synchronized Optional<Checkpoint> forceNow() {
        long currentSize = ledger.blockCount();
        if (currentSize == 0) {
            return Optional.empty();
        }

        Optional<com.verivote.data.entity.Checkpoint> latestEntity = checkpointRepo.findLatest();
        if (latestEntity.isPresent()) {
            Checkpoint latest = toDto(latestEntity.get());
            if (latest.treeSize() == currentSize) {
                return Optional.of(latest);
            }
            if (latest.treeSize() > currentSize) {
                throw new IllegalStateException("chain diverged: ledger truncated");
            }
            String headAtLatest = ledger.at(latest.treeSize() - 1)
                    .map(Block::blockHash)
                    .orElse(null);
            if (!latest.headHash().equals(headAtLatest)) {
                throw new IllegalStateException("chain diverged from last checkpoint");
            }
        }

        byte[] root = ledger.rootAt(currentSize);
        Block last = ledger.at(currentSize - 1)
                .orElseThrow(() -> new IllegalStateException("Missing last block at index " + (currentSize - 1)));

        byte[] sig = CheckpointSigner.sign(
                currentSize,
                root,
                Hex.decode(last.blockHash()),
                ed25519PrivateKey
        );

        Checkpoint ckpt = new Checkpoint(
                currentSize,
                Hex.encode(root),
                last.blockHash(),
                Base64Codec.encode(sig),
                System.currentTimeMillis() / 1000
        );

        // 9. Write to ALL sinks FIRST (before DB insert).
        int externalSuccessCount = 0;
        ExternalSink.SinkException lastSinkException = null;
        for (ExternalSink sink : sinks) {
            try {
                sink.write(ckpt);
                if (sink.isExternal() && !(sink instanceof PrintSink)) {
                    externalSuccessCount++;
                }
            } catch (ExternalSink.SinkException e) {
                lastSinkException = e;
            } catch (Exception e) {
                lastSinkException = new ExternalSink.SinkException("Sink failed: " + sink.name(), e);
            }
        }

        if (externalSuccessCount < minExternalSinks) {
            throw new ExternalSink.SinkException(
                    "Insufficient external sinks succeeded: " + externalSuccessCount + " < " + minExternalSinks,
                    lastSinkException
            );
        }

        // 10. Insert DB row (checkpointRepo.save)
        com.verivote.data.entity.Checkpoint newEntity = new com.verivote.data.entity.Checkpoint(
                (int) ckpt.treeSize(),
                ckpt.merkleRoot(),
                ckpt.headHash(),
                ckpt.signature()
        );
        newEntity.setCreatedAt(Instant.ofEpochSecond(ckpt.createdAtSec()));
        checkpointRepo.save(newEntity);

        return Optional.of(ckpt);
    }

    @Scheduled(fixedRate = 5000)
    public void timer() {
        maybeCheckpoint();
    }

    @Override
    public synchronized void maybeCheckpoint() {
        long currentSize = ledger.blockCount();
        if (currentSize == 0) {
            return;
        }

        Optional<Checkpoint> latestOpt = latest();
        if (latestOpt.isEmpty()) {
            if (currentSize >= minBlocks) {
                forceNow();
            }
        } else {
            Checkpoint latest = latestOpt.get();
            long diff = currentSize - latest.treeSize();
            long elapsedSec = (System.currentTimeMillis() / 1000) - latest.createdAtSec();
            if (diff >= minBlocks || (diff > 0 && elapsedSec >= minSecondsWithNewBlocks)) {
                forceNow();
            }
        }
    }

    @Override
    public Optional<Checkpoint> latest() {
        return checkpointRepo.findLatest().map(this::toDto);
    }

    @Override
    public List<Checkpoint> all() {
        return checkpointRepo.findAll(Sort.by(Sort.Direction.ASC, "treeSize"))
                .stream()
                .map(this::toDto)
                .toList();
    }

    private Checkpoint toDto(com.verivote.data.entity.Checkpoint e) {
        long createdSec = e.getCreatedAt() != null ? e.getCreatedAt().getEpochSecond() : 0L;
        return new Checkpoint(
                e.getTreeSize(),
                e.getMerkleRoot(),
                e.getHeadHash(),
                e.getSignature(),
                createdSec
        );
    }
}
