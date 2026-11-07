package com.verivote.service.recovery;

import com.verivote.core.domain.DomainStrings;
import com.verivote.core.encoding.Base64Codec;
import com.verivote.core.encoding.BigEndian;
import com.verivote.core.encoding.Hex;
import com.verivote.core.ledger.Block;
import com.verivote.core.ledger.LedgerService;
import com.verivote.data.RecoveryData;
import com.verivote.data.entity.Election;
import com.verivote.data.entity.Operator;
import com.verivote.data.entity.Vote;
import com.verivote.data.repo.ElectionRepo;
import com.verivote.data.repo.SpentTokenRepo;
import com.verivote.data.repo.VoteRepo;
import com.verivote.service.checkpoint.CheckpointSigner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.PublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class DefaultRecoveryService implements RecoveryService {

    private final LedgerService ledger;
    private final RecoveryData recoveryData;
    private final SpentTokenRepo spentTokenRepo;
    private final VoteRepo voteRepo;
    private final OperatorService operatorService;
    private final ElectionRepo electionRepo;
    private final TransactionTemplate txTemplate;
    private final Path fallbackFile;
    private final Path recoverySidecarFile;

    @Autowired
    public DefaultRecoveryService(
            LedgerService ledger,
            RecoveryData recoveryData,
            SpentTokenRepo spentTokenRepo,
            VoteRepo voteRepo,
            OperatorService operatorService,
            ElectionRepo electionRepo,
            PlatformTransactionManager txManager,
            @Value("${verivote.ledger.path:./ledger/blocks.ndjson}") String ledgerPathStr
    ) {
        this.ledger = ledger;
        this.recoveryData = recoveryData;
        this.spentTokenRepo = spentTokenRepo;
        this.voteRepo = voteRepo;
        this.operatorService = operatorService;
        this.electionRepo = electionRepo;
        this.txTemplate = new TransactionTemplate(txManager);

        Path lp = Paths.get(ledgerPathStr);
        Path parent = (lp.getParent() != null) ? lp.getParent() : Paths.get(".");
        this.fallbackFile = parent.resolve("orphans.fallback.ndjson");
        this.recoverySidecarFile = parent.resolve("recovery.ndjson");
    }

    public DefaultRecoveryService(
            LedgerService ledger,
            RecoveryData recoveryData,
            SpentTokenRepo spentTokenRepo,
            VoteRepo voteRepo,
            OperatorService operatorService,
            ElectionRepo electionRepo,
            PlatformTransactionManager txManager,
            Path fallbackFile,
            Path recoverySidecarFile
    ) {
        this.ledger = ledger;
        this.recoveryData = recoveryData;
        this.spentTokenRepo = spentTokenRepo;
        this.voteRepo = voteRepo;
        this.operatorService = operatorService;
        this.electionRepo = electionRepo;
        this.txTemplate = new TransactionTemplate(txManager);
        this.fallbackFile = fallbackFile;
        this.recoverySidecarFile = recoverySidecarFile;
    }

    @Override
    public RecoveryResult execute(RecoveryRequest req) {
        if (req == null) {
            throw new RecoveryException(RecoveryException.Code.BAD_REQUEST, "RecoveryRequest cannot be null");
        }

        // F1: EVENT-FIRST CHECK — run BEFORE signature validation
        Optional<RecoveryData.EventRow> existing = recoveryData.eventAt(req.blockHeight());
        if (existing.isPresent()) {
            RecoveryData.EventRow ev = existing.get();
            if (ev.action().equals(req.action().name())
                    && req.action() == RecoveryRequest.Action.ACCEPT_AS_ORPHAN
                    && !ledger.isOrphaned(req.blockHeight())) {
                try {
                    ledger.markOrphan(req.blockHeight());
                } catch (Exception e) {
                    writeFallback(req.blockHeight());
                    throw e;
                }
                return new RecoveryResult(ev.id(), req.blockHeight(), req.action());
            }
            throw new RecoveryException(RecoveryException.Code.ALREADY_RECOVERED, "height " + req.blockHeight());
        }

        // Normal path with full validation
        RecoveryResult res = txTemplate.execute(status -> {
            validatePreconditions(req);
            Block b = ledger.at(req.blockHeight())
                    .orElseThrow(() -> new RecoveryException(RecoveryException.Code.BLOCK_MISSING, "Block missing"));
            validateSignatures(req, b);

            List<Election> elections = electionRepo.findAll();
            long electionId = elections.get(0).getId();

            if (req.action() == RecoveryRequest.Action.RECOVER_FROM_LEDGER) {
                recoveryData.insertVote(
                        electionId,
                        b.tokenHash(),
                        b.receiptHash(),
                        b.ciphertextB64(),
                        b.height(),
                        b.blockHash()
                );
            }

            RecoveryData.EventRow ev = new RecoveryData.EventRow(
                    0L,
                    req.blockHeight(),
                    req.action().name(),
                    b.blockHash(),
                    b.tokenHash(),
                    req.reason(),
                    req.operator1(),
                    Base64Codec.encode(req.signature1()),
                    req.operator2(),
                    req.signature2() == null ? null : Base64Codec.encode(req.signature2())
            );

            long id = recoveryData.insertEvent(ev);
            sidecarAppend(ev);
            return new RecoveryResult(id, req.blockHeight(), req.action());
        });

        // markOrphan OUTSIDE transaction, idempotent
        if (req.action() == RecoveryRequest.Action.ACCEPT_AS_ORPHAN) {
            try {
                ledger.markOrphan(req.blockHeight());
            } catch (Exception e) {
                writeFallback(req.blockHeight());
                throw e;
            }
        }

        return res;
    }

    private void validatePreconditions(RecoveryRequest req) {
        // 1. Election status must be CLOSED
        List<Election> elections = electionRepo.findAll();
        if (elections.isEmpty() || elections.get(0).getStatus() != Election.Status.CLOSED) {
            throw new RecoveryException(RecoveryException.Code.ELECTION_NOT_CLOSED, "Election is not closed");
        }

        // 2. Block at req.blockHeight exists in ledger
        Optional<Block> blockOpt = ledger.at(req.blockHeight());
        if (blockOpt.isEmpty()) {
            throw new RecoveryException(RecoveryException.Code.BLOCK_MISSING, "Block does not exist at height " + req.blockHeight());
        }
        Block b = blockOpt.get();

        // 3. Block is VOTE (not ORPHAN)
        if (Block.ORPHAN.equals(b.type())) {
            throw new RecoveryException(RecoveryException.Code.BLOCK_NOT_VOTE, "Block is an orphan, not a vote");
        }

        // 4. !ledger.isOrphaned(req.blockHeight())
        if (ledger.isOrphaned(req.blockHeight())) {
            throw new RecoveryException(RecoveryException.Code.BLOCK_ALREADY_ORPHANED, "Block is already orphaned");
        }

        // 5. Operator role and activity checks
        if (req.action() == RecoveryRequest.Action.ACCEPT_AS_ORPHAN) {
            if (req.operator1() != null && req.operator1().equals(req.operator2())) {
                throw new RecoveryException(RecoveryException.Code.SAME_OPERATOR, "Operators must be distinct");
            }
            checkOperator(req.operator1());
            checkOperator(req.operator2());

            boolean spentExists = spentTokenRepo.existsById(b.tokenHash());
            boolean retryCommitted = voteRepo.findAll().stream()
                    .anyMatch(v -> v.getTokenHash().equals(b.tokenHash()) && !v.getBlockHeight().equals(b.height()));
            if (spentExists && !retryCommitted) {
                throw new RecoveryException(RecoveryException.Code.PRECONDITION_FAILED,
                        "spent_token exists and no retry committed");
            }
        } else if (req.action() == RecoveryRequest.Action.RECOVER_FROM_LEDGER) {
            checkOperator(req.operator1());

            boolean spentExists = spentTokenRepo.existsById(b.tokenHash());
            if (!spentExists) {
                throw new RecoveryException(RecoveryException.Code.PRECONDITION_FAILED, "spent_token does not exist");
            }

            boolean retryCommitted = voteRepo.findAll().stream()
                    .anyMatch(v -> v.getTokenHash().equals(b.tokenHash()) && !v.getBlockHeight().equals(b.height()));
            if (retryCommitted) {
                throw new RecoveryException(RecoveryException.Code.PRECONDITION_FAILED, "retry already committed");
            }
        }
    }

    private Operator checkOperator(String name) {
        if (name == null || name.isBlank()) {
            throw new RecoveryException(RecoveryException.Code.OPERATOR_UNKNOWN, "Operator name is missing");
        }
        Operator op = operatorService.findByName(name)
                .orElseThrow(() -> new RecoveryException(RecoveryException.Code.OPERATOR_UNKNOWN, "Unknown operator: " + name));

        Instant now = Instant.now();
        if (op.getActiveFrom() == null || op.getActiveFrom().isAfter(now)
                || (op.getActiveTo() != null && !op.getActiveTo().isAfter(now))) {
            throw new RecoveryException(RecoveryException.Code.OPERATOR_INACTIVE, "Operator is inactive: " + name);
        }

        if (op.getRole() != Operator.Role.AUDIT) {
            throw new RecoveryException(RecoveryException.Code.OPERATOR_ROLE_MISMATCH, "Operator role is not AUDIT: " + op.getRole());
        }

        return op;
    }

    private void validateSignatures(RecoveryRequest req, Block b) {
        String blockHashHex = (req.blockHash() != null) ? req.blockHash() : b.blockHash();
        String tokenHashHex = (req.tokenHash() != null) ? req.tokenHash() : b.tokenHash();

        byte[] preimage = buildSigningPreimage(
                req.blockHeight(),
                req.action(),
                blockHashHex,
                tokenHashHex,
                req.reason()
        );

        Operator op1 = operatorService.findByName(req.operator1())
                .orElseThrow(() -> new RecoveryException(RecoveryException.Code.OPERATOR_UNKNOWN, "Unknown operator 1"));
        PublicKey pk1 = CheckpointSigner.decodePublicKey(op1.getEd25519Pub());
        if (!RecoveryOperatorSigner.verify(preimage, req.signature1(), pk1)) {
            throw new RecoveryException(RecoveryException.Code.SIGNATURE_INVALID, "Invalid signature for operator 1");
        }

        if (req.action() == RecoveryRequest.Action.ACCEPT_AS_ORPHAN) {
            Operator op2 = operatorService.findByName(req.operator2())
                    .orElseThrow(() -> new RecoveryException(RecoveryException.Code.OPERATOR_UNKNOWN, "Unknown operator 2"));
            PublicKey pk2 = CheckpointSigner.decodePublicKey(op2.getEd25519Pub());
            if (!RecoveryOperatorSigner.verify(preimage, req.signature2(), pk2)) {
                throw new RecoveryException(RecoveryException.Code.SIGNATURE_INVALID, "Invalid signature for operator 2");
            }
        }
    }

    public static byte[] buildSigningPreimage(long height, RecoveryRequest.Action action, String blockHash, String tokenHash, String reason) {
        byte[] prefix = DomainStrings.RECOVERY_PREFIX.getBytes(StandardCharsets.US_ASCII);
        byte[] heightBytes = BigEndian.u64be(height);
        byte[] actionBytes = action.name().getBytes(StandardCharsets.US_ASCII);
        byte[] nullSep = new byte[]{0x00};
        byte[] blockHashBytes = Hex.decode(blockHash);
        byte[] tokenHashBytes = Hex.decode(tokenHash);
        byte[] reasonBytes = (reason != null) ? reason.getBytes(StandardCharsets.UTF_8) : new byte[0];

        byte[] preimage = new byte[prefix.length + heightBytes.length + actionBytes.length + 1
                + blockHashBytes.length + tokenHashBytes.length + reasonBytes.length];
        int pos = 0;
        System.arraycopy(prefix, 0, preimage, pos, prefix.length); pos += prefix.length;
        System.arraycopy(heightBytes, 0, preimage, pos, heightBytes.length); pos += heightBytes.length;
        System.arraycopy(actionBytes, 0, preimage, pos, actionBytes.length); pos += actionBytes.length;
        System.arraycopy(nullSep, 0, preimage, pos, 1); pos += 1;
        System.arraycopy(blockHashBytes, 0, preimage, pos, blockHashBytes.length); pos += blockHashBytes.length;
        System.arraycopy(tokenHashBytes, 0, preimage, pos, tokenHashBytes.length); pos += tokenHashBytes.length;
        System.arraycopy(reasonBytes, 0, preimage, pos, reasonBytes.length);

        return preimage;
    }

    private void sidecarAppend(RecoveryData.EventRow ev) {
        try {
            if (recoverySidecarFile.getParent() != null) {
                Files.createDirectories(recoverySidecarFile.getParent());
            }
            String line = "{\"id\":" + ev.id() +
                    ",\"blockHeight\":" + ev.blockHeight() +
                    ",\"action\":\"" + ev.action() + "\"" +
                    ",\"blockHash\":\"" + ev.blockHash() + "\"" +
                    ",\"tokenHash\":\"" + ev.tokenHash() + "\"" +
                    ",\"reason\":\"" + ev.reason() + "\"" +
                    ",\"operator1\":\"" + ev.operator1() + "\"" +
                    ",\"signature1\":\"" + ev.signature1() + "\"" +
                    (ev.operator2() != null ? ",\"operator2\":\"" + ev.operator2() + "\"" : "") +
                    (ev.signature2() != null ? ",\"signature2\":\"" + ev.signature2() + "\"" : "") +
                    "}\n";
            byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
            try (FileChannel ch = FileChannel.open(
                    recoverySidecarFile,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND
            )) {
                ch.write(ByteBuffer.wrap(bytes));
                ch.force(true);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to append to recovery sidecar file", e);
        }
    }

    private void writeFallback(long height) {
        try {
            if (fallbackFile.getParent() != null) {
                Files.createDirectories(fallbackFile.getParent());
            }
            String line = "{\"refHeight\":" + height + ",\"timestamp\":" + (System.currentTimeMillis() / 1000) + "}\n";
            byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
            try (FileChannel ch = FileChannel.open(
                    fallbackFile,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND
            )) {
                ch.write(ByteBuffer.wrap(bytes));
                ch.force(true);
            }
        } catch (IOException ignored) {}
    }
}
