package com.verivote.service.audit;

import com.verivote.core.crypto.MerkleTree;
import com.verivote.core.crypto.Sha256;
import com.verivote.core.domain.DomainStrings;
import com.verivote.core.encoding.Base64Codec;
import com.verivote.core.encoding.BigEndian;
import com.verivote.core.encoding.Hex;
import com.verivote.core.ledger.Block;
import com.verivote.core.ledger.BlockHash;
import com.verivote.core.ledger.LedgerFileParser;
import com.verivote.data.entity.IssuanceCounter;
import com.verivote.data.entity.IssuancePublished;
import com.verivote.data.entity.RecoveryEvent;
import com.verivote.data.entity.Vote;
import com.verivote.data.repo.IssuanceCounterRepo;
import com.verivote.data.repo.IssuancePublishedRepo;
import com.verivote.data.repo.RecoveryEventRepo;
import com.verivote.data.repo.SpentTokenRepo;
import com.verivote.data.repo.VoteRepo;
import com.verivote.service.checkpoint.CheckpointService;
import com.verivote.service.checkpoint.CheckpointSigner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PublicKey;
import java.security.Signature;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DefaultAuditService implements AuditService {

    private static final Logger log = LoggerFactory.getLogger(DefaultAuditService.class);

    private final Path ledgerPath;
    private final VoteRepo voteRepo;
    private final SpentTokenRepo spentTokenRepo;
    private final RecoveryEventRepo recoveryEventRepo;
    private final IssuanceCounterRepo issuanceCounterRepo;
    private final IssuancePublishedRepo issuancePublishedRepo;
    private final CheckpointReader checkpointReader;
    private final PublicKey ed25519PublicKey;
    private final Map<String, PublicKey> recoveryOperatorPublicKeys;

    @Autowired
    public DefaultAuditService(
            @Value("${verivote.ledger.path:./ledger/blocks.ndjson}") String ledgerPathStr,
            VoteRepo voteRepo,
            SpentTokenRepo spentTokenRepo,
            RecoveryEventRepo recoveryEventRepo,
            IssuanceCounterRepo issuanceCounterRepo,
            IssuancePublishedRepo issuancePublishedRepo,
            @Autowired(required = false) CheckpointReader checkpointReader,
            @Autowired(required = false) PublicKey ed25519PublicKey,
            @Autowired(required = false) Map<String, PublicKey> recoveryOperatorPublicKeys
    ) {
        this(
                Paths.get(ledgerPathStr),
                voteRepo,
                spentTokenRepo,
                recoveryEventRepo,
                issuanceCounterRepo,
                issuancePublishedRepo,
                checkpointReader,
                ed25519PublicKey,
                recoveryOperatorPublicKeys
        );
    }

    public DefaultAuditService(
            Path ledgerPath,
            VoteRepo voteRepo,
            SpentTokenRepo spentTokenRepo,
            RecoveryEventRepo recoveryEventRepo,
            IssuanceCounterRepo issuanceCounterRepo,
            IssuancePublishedRepo issuancePublishedRepo,
            CheckpointReader checkpointReader,
            PublicKey ed25519PublicKey,
            Map<String, PublicKey> recoveryOperatorPublicKeys
    ) {
        this.ledgerPath = ledgerPath;
        this.voteRepo = voteRepo;
        this.spentTokenRepo = spentTokenRepo;
        this.recoveryEventRepo = recoveryEventRepo;
        this.issuanceCounterRepo = issuanceCounterRepo;
        this.issuancePublishedRepo = issuancePublishedRepo;
        this.checkpointReader = checkpointReader;
        this.ed25519PublicKey = ed25519PublicKey;
        this.recoveryOperatorPublicKeys = (recoveryOperatorPublicKeys != null)
                ? new HashMap<>(recoveryOperatorPublicKeys)
                : Collections.emptyMap();
    }

    @Override
    public TamperReport audit() {
        long now = System.currentTimeMillis() / 1000;
        List<Finding> findings = new ArrayList<>();

        // 1. Parse ledger from disk via LedgerFileParser.parse(ledgerPath)
        LedgerFileParser.ParseResult parseResult = LedgerFileParser.parse(ledgerPath);
        if (parseResult.isFatal()) {
            FindingCode code = FindingCode.LEDGER_UNREADABLE;
            String fatalMsg = parseResult.fatal();
            if (fatalMsg != null) {
                if (fatalMsg.contains("MALFORMED") || fatalMsg.contains("JSON")) {
                    code = FindingCode.MALFORMED_LINE;
                } else if (fatalMsg.contains("CHAIN_HASH")) {
                    code = FindingCode.CHAIN_HASH_MISMATCH;
                } else if (fatalMsg.contains("CHAIN_LINK")) {
                    code = FindingCode.CHAIN_LINK_BROKEN;
                }
            }
            findings.add(new Finding(Severity.RED, code, fatalMsg, null, null, "Restore ledger or repair torn write"));
            long dbCount = (voteRepo != null) ? voteRepo.count() : 0;
            return new TamperReport(false, now, parseResult.blocks().size(), 0, 0, dbCount, findings);
        }

        if (parseResult.hasTornTail()) {
            findings.add(new Finding(Severity.WARN, FindingCode.MALFORMED_LINE, "Torn tail encountered and repaired",
                    null, null, "Inspect ledger tail"));
        }

        List<Block> blocks = parseResult.blocks();
        long ledgerBlockCount = blocks.size();

        // 2. Validate individual blocks and chain linkages
        for (int i = 0; i < blocks.size(); i++) {
            Block b = blocks.get(i);
            // a. Verify BlockHash.compute(b) == b.blockHash()
            String computedHash = Hex.encode(BlockHash.compute(b));
            if (!computedHash.equalsIgnoreCase(b.blockHash())) {
                findings.add(new Finding(Severity.RED, FindingCode.CHAIN_HASH_MISMATCH,
                        "Computed hash " + computedHash + " does not match blockHash " + b.blockHash() + " at height " + b.height(),
                        b.height(), null, "Ledger block hash tampering detected"));
            }

            // b. Verify b.prevHash == previous.blockHash (or ZERO for genesis)
            String expectedPrev = (i == 0) ? Block.ZERO : blocks.get(i - 1).blockHash();
            if (!expectedPrev.equalsIgnoreCase(b.prevHash())) {
                findings.add(new Finding(Severity.RED, FindingCode.CHAIN_LINK_BROKEN,
                        "Expected prevHash " + expectedPrev + " but got " + b.prevHash() + " at height " + b.height(),
                        b.height(), null, "Ledger block chain link broken"));
            }

            // c. For VOTE blocks: verify SHA-256(base64decode(ct)) == voteHash
            if (Block.VOTE.equals(b.type())) {
                if (b.ciphertextB64() != null) {
                    try {
                        byte[] ctBytes = Base64Codec.decode(b.ciphertextB64());
                        String ctHashHex = Hex.encode(Sha256.hash(ctBytes));
                        if (!ctHashHex.equalsIgnoreCase(b.voteHash())) {
                            findings.add(new Finding(Severity.RED, FindingCode.CIPHERTEXT_MISMATCH,
                                    "Ciphertext hash " + ctHashHex + " does not match voteHash " + b.voteHash() + " at height " + b.height(),
                                    b.height(), null, "Ciphertext modified in block"));
                        }
                    } catch (Exception e) {
                        findings.add(new Finding(Severity.RED, FindingCode.CIPHERTEXT_MISMATCH,
                                "Failed to decode ciphertextB64 at height " + b.height(),
                                b.height(), null, "Ciphertext decoding failure"));
                    }
                }
            }
        }

        // 3. Compute Merkle root over blocks[0..N] leaves
        List<byte[]> leaves = new ArrayList<>();
        Set<Long> orphanedHeights = new HashSet<>();
        long orphanCount = 0;

        for (Block b : blocks) {
            if (Block.ORPHAN.equals(b.type())) {
                orphanCount++;
                orphanedHeights.add(b.refHeight());
                byte[] prefix = DomainStrings.ORPHAN_PREFIX.getBytes(StandardCharsets.US_ASCII);
                byte[] refBytes = BigEndian.u64be(b.refHeight());
                byte[] orphanData = new byte[prefix.length + refBytes.length];
                System.arraycopy(prefix, 0, orphanData, 0, prefix.length);
                System.arraycopy(refBytes, 0, orphanData, prefix.length, refBytes.length);
                leaves.add(MerkleTree.leafHash(orphanData));
            } else {
                leaves.add(MerkleTree.leafHash(Hex.decode(b.voteHash())));
            }
        }

        // Adjust realVoteCount to exclude orphaned votes
        long realVoteCount = blocks.stream()
                .filter(b -> Block.VOTE.equals(b.type()) && !orphanedHeights.contains(b.height()))
                .count();

        if (!blocks.isEmpty()) {
            byte[] computedRoot = MerkleTree.rootAt(leaves, leaves.size());
            String computedRootHex = Hex.encode(computedRoot);
            Block lastBlock = blocks.get(blocks.size() - 1);
            if (!computedRootHex.equalsIgnoreCase(lastBlock.merkleRoot())) {
                findings.add(new Finding(Severity.RED, FindingCode.MERKLE_ROOT_MISMATCH,
                        "Computed Merkle root " + computedRootHex + " does not match last block's merkleRoot " + lastBlock.merkleRoot(),
                        lastBlock.height(), (long) blocks.size(), "Merkle root calculation mismatch"));
            }
        }

        // 4. Load checkpoints from sinks
        if (checkpointReader != null) {
            List<CheckpointService.Checkpoint> checkpoints = checkpointReader.loadAll();
            Map<String, Finding> dedupedCheckpoints = new LinkedHashMap<>();
            boolean hasCheckpointErrors = false;

            for (CheckpointService.Checkpoint ckpt : checkpoints) {
                long treeSize = ckpt.treeSize();
                // a. Verify signature
                if (ed25519PublicKey != null) {
                    byte[] sigBytes = Base64Codec.decode(ckpt.signature());
                    byte[] rootBytes = Hex.decode(ckpt.merkleRoot());
                    byte[] headBytes = Hex.decode(ckpt.headHash());
                    boolean valid = CheckpointSigner.verify(treeSize, rootBytes, headBytes, sigBytes, ed25519PublicKey);
                    if (!valid) {
                        hasCheckpointErrors = true;
                        String key = FindingCode.CHAIN_TRUNCATED.name() + ":" + treeSize;
                        dedupedCheckpoints.putIfAbsent(key, new Finding(Severity.RED, FindingCode.CHAIN_TRUNCATED,
                                "Checkpoint signature invalid at treeSize " + treeSize, null, treeSize, "Verify checkpoint signer key"));
                    }
                }

                // b. If treeSize > blocks.size() -> RED CHAIN_TRUNCATED
                if (treeSize > blocks.size()) {
                    hasCheckpointErrors = true;
                    String key = FindingCode.CHAIN_TRUNCATED.name() + ":" + treeSize;
                    dedupedCheckpoints.putIfAbsent(key, new Finding(Severity.RED, FindingCode.CHAIN_TRUNCATED,
                            "Checkpoint claims treeSize " + treeSize + " but ledger only has " + blocks.size() + " blocks",
                            null, treeSize, "Ledger was truncated after checkpoint was published"));
                } else if (treeSize <= blocks.size() && treeSize > 0) {
                    // c. Verify headAtSize and rootAtSize
                    Block blockAtSize = blocks.get((int) treeSize - 1);
                    if (!blockAtSize.blockHash().equalsIgnoreCase(ckpt.headHash())) {
                        hasCheckpointErrors = true;
                        String key = FindingCode.CHAIN_TRUNCATED.name() + ":" + treeSize;
                        dedupedCheckpoints.putIfAbsent(key, new Finding(Severity.RED, FindingCode.CHAIN_TRUNCATED,
                                "Head hash at treeSize " + treeSize + " (" + blockAtSize.blockHash() + ") does not match checkpoint (" + ckpt.headHash() + ")",
                                blockAtSize.height(), treeSize, "Ledger chain diverged from checkpoint"));
                    }
                    byte[] rootAtSize = MerkleTree.rootAt(leaves, treeSize);
                    if (!Hex.encode(rootAtSize).equalsIgnoreCase(ckpt.merkleRoot())) {
                        hasCheckpointErrors = true;
                        String key = FindingCode.MERKLE_ROOT_MISMATCH.name() + ":" + treeSize;
                        dedupedCheckpoints.putIfAbsent(key, new Finding(Severity.RED, FindingCode.MERKLE_ROOT_MISMATCH,
                                "Merkle root at treeSize " + treeSize + " does not match checkpoint",
                                blockAtSize.height(), treeSize, "Ledger tree root diverged from checkpoint"));
                    }
                }
            }

            findings.addAll(dedupedCheckpoints.values());
            if (!checkpoints.isEmpty() && !hasCheckpointErrors) {
                findings.add(new Finding(Severity.INFO, FindingCode.CHECKPOINTS_VERIFIED,
                        "Successfully verified " + checkpoints.size() + " checkpoints", null, (long) blocks.size(), null));
            }
        }

        // 5. Build Map<tokenHashHex, List<Block>> for all non-orphan VOTE blocks
        Map<String, List<Block>> tokenBlocks = new HashMap<>();
        for (Block b : blocks) {
            if (Block.VOTE.equals(b.type()) && !orphanedHeights.contains(b.height())) {
                tokenBlocks.computeIfAbsent(b.tokenHash(), k -> new ArrayList<>()).add(b);
            }
        }

        for (Map.Entry<String, List<Block>> entry : tokenBlocks.entrySet()) {
            if (entry.getValue().size() > 1) {
                findings.add(new Finding(Severity.RED, FindingCode.DUPLICATE_TOKEN_HASH,
                        "Duplicate tokenHash in ledger: " + entry.getKey(),
                        entry.getValue().get(1).height(), null, "Double spend occurred in ledger"));
            }
        }

        // 6. For every non-orphan VOTE block, check DB has a vote row
        if (voteRepo != null) {
            for (Block b : blocks) {
                if (Block.VOTE.equals(b.type()) && !orphanedHeights.contains(b.height())) {
                    Optional<Vote> voteOpt = voteRepo.findByBlockHeight(b.height());
                    if (voteOpt.isEmpty()) {
                        boolean spentTokenExists = (spentTokenRepo != null) && spentTokenRepo.existsById(b.tokenHash());
                        boolean retryCommitted = false;
                        if (spentTokenExists) {
                            List<Vote> allVotes = voteRepo.findAll();
                            retryCommitted = allVotes.stream()
                                    .anyMatch(v -> v.getTokenHash().equals(b.tokenHash()) && !v.getBlockHeight().equals(b.height()));
                        }

                        if (!spentTokenExists) {
                            findings.add(new Finding(Severity.WARN, FindingCode.BLOCK_WITHOUT_VOTE_ROW,
                                    "no spent_token -> crash before commit -> ACCEPT_AS_ORPHAN (two operators)",
                                    b.height(), null, "ACCEPT_AS_ORPHAN"));
                        } else if (retryCommitted) {
                            findings.add(new Finding(Severity.WARN, FindingCode.BLOCK_WITHOUT_VOTE_ROW,
                                    "committed retry -> ACCEPT_AS_ORPHAN (retry case)",
                                    b.height(), null, "ACCEPT_AS_ORPHAN"));
                        } else {
                            findings.add(new Finding(Severity.ALERT, FindingCode.BLOCK_WITHOUT_VOTE_ROW,
                                    "spent_token EXISTS and no retry committed -> RECOVER_FROM_LEDGER or investigate deletion",
                                    b.height(), null, "RECOVER_FROM_LEDGER"));
                        }
                    }
                }
            }

            // 7. Reconcile vote rows in DB that have NO corresponding ledger block
            List<Vote> dbVotes = voteRepo.findAll();
            for (Vote v : dbVotes) {
                long h = v.getBlockHeight();
                if (h >= blocks.size()) {
                    findings.add(new Finding(Severity.RED, FindingCode.BLOCK_WITHOUT_VOTE_ROW,
                            "DB vote at height " + h + " exceeds ledger length " + blocks.size(),
                            h, null, "DB contains vote not present in ledger"));
                } else {
                    Block b = blocks.get((int) h);
                    if (!b.tokenHash().equalsIgnoreCase(v.getTokenHash()) || !b.receiptHash().equalsIgnoreCase(v.getReceiptHash())) {
                        findings.add(new Finding(Severity.RED, FindingCode.CHAIN_HASH_MISMATCH,
                                "DB vote metadata at height " + h + " does not match ledger block",
                                h, null, "Database vote row metadata tampering"));
                    }
                }
            }

            // 8. Ciphertext mismatch at DB level
            for (Vote v : dbVotes) {
                long h = v.getBlockHeight();
                if (h < blocks.size()) {
                    Block b = blocks.get((int) h);
                    try {
                        byte[] dbCtBytes = Base64Codec.decode(v.getEncryptedVote());
                        String dbVoteHash = Hex.encode(Sha256.hash(dbCtBytes));
                        if (!dbVoteHash.equalsIgnoreCase(b.voteHash())) {
                            findings.add(new Finding(Severity.RED, FindingCode.DB_CIPHERTEXT_MISMATCH,
                                    "DB encrypted_vote hash " + dbVoteHash + " does not match ledger voteHash " + b.voteHash() + " at height " + h,
                                    h, null, "Restore DB vote row from ledger block"));
                        }
                    } catch (Exception e) {
                        findings.add(new Finding(Severity.RED, FindingCode.DB_CIPHERTEXT_MISMATCH,
                                "Failed to decode DB encrypted_vote at height " + h,
                                h, null, "Restore DB vote row from ledger"));
                    }
                }
            }
        }

        // 9. Issuance counter check
        if (issuanceCounterRepo != null && issuancePublishedRepo != null) {
            List<IssuanceCounter> counters = issuanceCounterRepo.findAll();
            for (IssuanceCounter counter : counters) {
                long eid = counter.getElectionId();
                Optional<IssuancePublished> pubOpt = issuancePublishedRepo.findFirstByElectionIdOrderByPublishedAtDesc(eid);
                if (pubOpt.isPresent()) {
                    IssuancePublished pub = pubOpt.get();
                    if (counter.getIssued() < pub.getIssued()) {
                        findings.add(new Finding(Severity.RED, FindingCode.ISSUANCE_REGRESSED,
                                "Issuance counter regressed: current " + counter.getIssued() + " < published " + pub.getIssued(),
                                null, null, "Investigate database rollback or tampering"));
                    }
                }
                if (counter.getIssued() > 0 && realVoteCount > counter.getIssued()) {
                    findings.add(new Finding(Severity.RED, FindingCode.VOTES_EXCEED_ISSUED,
                            "Real votes (" + realVoteCount + ") exceed issued tokens (" + counter.getIssued() + ")",
                            null, null, "Halt election; votes exceed token issuance"));
                }
            }
        }

        // 10. Recovery event signature verification
        if (recoveryEventRepo != null && !recoveryOperatorPublicKeys.isEmpty()) {
            List<RecoveryEvent> recoveryEvents = recoveryEventRepo.findAll();
            for (RecoveryEvent ev : recoveryEvents) {
                boolean valid = verifyRecoveryEvent(ev);
                if (!valid) {
                    findings.add(new Finding(Severity.WARN, FindingCode.RECOVERY_SIGNATURE_INVALID,
                            "Recovery event signature invalid at height " + ev.getBlockHeight(),
                            ev.getBlockHeight(), null, "Inspect operator credentials"));
                }
            }
        }

        // 11. ORPHAN block presence
        if (orphanCount > 0) {
            findings.add(new Finding(Severity.INFO, FindingCode.ORPHAN_BLOCK_PRESENT,
                    "Orphan blocks present in ledger: " + orphanCount, null, null, null));
        }

        long dbVoteCount = (voteRepo != null) ? voteRepo.count() : 0;
        boolean clean = findings.stream().noneMatch(f -> f.severity() == Severity.RED || f.severity() == Severity.ALERT);

        return new TamperReport(clean, now, ledgerBlockCount, realVoteCount, orphanCount, dbVoteCount, findings);
    }

    private boolean verifyRecoveryEvent(RecoveryEvent ev) {
        PublicKey pk1 = recoveryOperatorPublicKeys.get(ev.getOperator1());
        if (pk1 == null) {
            return false;
        }

        byte[] preimage = buildRecoveryPreimage(
                ev.getBlockHeight(),
                ev.getAction().name(),
                ev.getBlockHash(),
                ev.getTokenHash(),
                ev.getReason()
        );

        if (!verifyEd25519(preimage, ev.getSignature1(), pk1)) {
            return false;
        }

        if (ev.getAction() == RecoveryEvent.Action.ACCEPT_AS_ORPHAN) {
            if (ev.getOperator2() == null || ev.getSignature2() == null) {
                return false;
            }
            PublicKey pk2 = recoveryOperatorPublicKeys.get(ev.getOperator2());
            if (pk2 == null || !verifyEd25519(preimage, ev.getSignature2(), pk2)) {
                return false;
            }
        }
        return true;
    }

    public static byte[] buildRecoveryPreimage(long height, String action, String blockHash, String tokenHash, String reason) {
        byte[] prefix = DomainStrings.RECOVERY_PREFIX.getBytes(StandardCharsets.US_ASCII);
        byte[] heightBytes = BigEndian.u64be(height);
        byte[] actionBytes = action.getBytes(StandardCharsets.US_ASCII);
        byte[] nullSep = new byte[]{0x00};
        byte[] blockHashBytes = Hex.decode(blockHash);
        byte[] tokenHashBytes = Hex.decode(tokenHash);
        byte[] reasonBytes = reason.getBytes(StandardCharsets.UTF_8);

        byte[] preimage = new byte[prefix.length + heightBytes.length + actionBytes.length + 1 +
                blockHashBytes.length + tokenHashBytes.length + reasonBytes.length];
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

    private boolean verifyEd25519(byte[] preimage, String sigB64, PublicKey pubKey) {
        try {
            Signature sig = Signature.getInstance("Ed25519");
            sig.initVerify(pubKey);
            sig.update(preimage);
            return sig.verify(Base64Codec.decode(sigB64));
        } catch (Exception e) {
            return false;
        }
    }
}
