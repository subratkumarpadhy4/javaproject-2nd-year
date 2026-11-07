package com.verivote.service.tally;

import com.verivote.core.crypto.Sha256;
import com.verivote.core.crypto.Shamir;
import com.verivote.core.encoding.Base64Codec;
import com.verivote.core.encoding.Hex;
import com.verivote.core.ledger.Block;
import com.verivote.core.ledger.LedgerService;
import com.verivote.data.entity.Candidate;
import com.verivote.data.entity.Election;
import com.verivote.data.repo.CandidateRepo;
import com.verivote.data.repo.ElectionRepo;
import com.verivote.service.audit.AuditService;
import com.verivote.service.audit.TamperReport;
import com.verivote.service.checkpoint.CheckpointService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.Cipher;
import java.nio.ByteBuffer;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class DefaultTallyService implements TallyService {

    private static final Map<Long, TallyResult> LATEST_TALLIES = new ConcurrentHashMap<>();

    private final LedgerService ledger;
    private final AuditService auditService;
    private final CheckpointService checkpointService;
    private final ElectionRepo electionRepo;
    private final CandidateRepo candidateRepo;
    private final TallyKeyService tallyKeyService;
    private final TransactionTemplate txTemplate;
    private final SecureRandom rnd = new SecureRandom();

    @Autowired
    public DefaultTallyService(
            LedgerService ledger,
            AuditService auditService,
            CheckpointService checkpointService,
            ElectionRepo electionRepo,
            CandidateRepo candidateRepo,
            TallyKeyService tallyKeyService,
            PlatformTransactionManager txManager
    ) {
        this.ledger = ledger;
        this.auditService = auditService;
        this.checkpointService = checkpointService;
        this.electionRepo = electionRepo;
        this.candidateRepo = candidateRepo;
        this.tallyKeyService = tallyKeyService;
        this.txTemplate = new TransactionTemplate(txManager);
    }

    @Override
    public TallyResult tally(long electionId, List<Shamir.Share> keyShares) {
        // 1. SELECT election FOR UPDATE; status must become CLOSED
        txTemplate.executeWithoutResult(status -> {
            Election election = electionRepo.findByIdForUpdate(electionId)
                    .orElseGet(() -> electionRepo.findById(electionId)
                            .orElseThrow(() -> new IllegalStateException("Election not found: " + electionId)));
            if (election.getStatus() == Election.Status.OPEN) {
                election.setStatus(Election.Status.CLOSED);
                electionRepo.saveAndFlush(election);
            } else if (election.getStatus() != Election.Status.CLOSED) {
                throw new IllegalStateException("Election status must be OPEN or CLOSED to tally, got: " + election.getStatus());
            }
        });

        // 2. checkpointService.forceNow() -> anchor closeHeadHash
        checkpointService.forceNow();
        String closeHeadHash = ledger.headHash();
        long closeBlockHeight = ledger.blockCount();

        // 3. Audit ledger — if blocksTally -> exception
        TamperReport audit = auditService.audit();
        if (audit.blocksTally()) {
            throw new TallyPreconditionException(audit);
        }

        // 4. Collect non-orphan VOTE blocks from ledger
        List<Block> voteBlocks = new ArrayList<>();
        Set<String> seenTokenHashes = new HashSet<>();
        for (long h = 0; h < closeBlockHeight; h++) {
            Optional<Block> opt = ledger.at(h);
            if (opt.isPresent()) {
                Block b = opt.get();
                if (Block.VOTE.equals(b.type()) && !ledger.isOrphaned(b.height())) {
                    // verify SHA-256(base64decode(ct)) == voteHash
                    byte[] ctBytes = Base64Codec.decode(b.ciphertextB64());
                    String hash = Hex.encode(Sha256.hash(ctBytes));
                    if (!hash.equalsIgnoreCase(b.voteHash())) {
                        throw new IllegalStateException("Ciphertext hash mismatch at height " + b.height());
                    }
                    if (!seenTokenHashes.add(b.tokenHash())) {
                        throw new IllegalStateException("Duplicate tokenHash in ledger at height " + b.height());
                    }
                    voteBlocks.add(b);
                }
            }
        }

        // 5. Reconstruct tally key from shares
        PrivateKey tallyPriv = tallyKeyService.reconstructAndGetPrivateKey(keyShares);

        // 6. Fisher-Yates shuffle blocks with SecureRandom
        Collections.shuffle(voteBlocks, rnd);

        // 7. Decrypt each ciphertext via RSA-OAEP-SHA-256
        Set<Long> candidateIds = candidateRepo.findByElectionId(electionId).stream()
                .map(Candidate::getId)
                .collect(Collectors.toSet());

        Map<Long, Long> counts = new HashMap<>();
        for (Long cId : candidateIds) {
            counts.put(cId, 0L);
        }

        long invalidCount = 0;
        List<Ballot> ballots = new ArrayList<>();

        for (Block b : voteBlocks) {
            try {
                Cipher c = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
                c.init(Cipher.DECRYPT_MODE, tallyPriv);
                byte[] pt = c.doFinal(Base64Codec.decode(b.ciphertextB64()));
                if (pt.length < 44) {
                    invalidCount++;
                    continue;
                }
                ByteBuffer bb = ByteBuffer.wrap(pt);
                long eId = bb.getLong();
                long candidateId = (long) bb.getInt();
                byte[] ballotId = new byte[32];
                bb.get(ballotId);

                if (eId != electionId || !candidateIds.contains(candidateId)) {
                    invalidCount++;
                } else {
                    counts.merge(candidateId, 1L, Long::sum);
                    ballots.add(new Ballot((int) candidateId, Hex.encode(ballotId)));
                }
            } catch (Exception e) {
                invalidCount++;
            }
        }

        // 8. Re-shuffle ballots so order does not correlate with anything
        Collections.shuffle(ballots, rnd);

        byte[] rootBytes = ledger.rootAt(closeBlockHeight);
        String chainMerkleRoot = Hex.encode(rootBytes);

        TallyResult result = new TallyResult(
                counts,
                invalidCount,
                closeBlockHeight,
                closeHeadHash,
                chainMerkleRoot,
                ballots,
                System.currentTimeMillis() / 1000
        );

        LATEST_TALLIES.put(electionId, result);

        // Update election status to TALLIED
        txTemplate.executeWithoutResult(status -> {
            Election election = electionRepo.findById(electionId).orElseThrow();
            election.setStatus(Election.Status.TALLIED);
            electionRepo.saveAndFlush(election);
        });

        return result;
    }

    @Override
    public Optional<TallyResult> getLatestTally(long electionId) {
        return Optional.ofNullable(LATEST_TALLIES.get(electionId));
    }
}
