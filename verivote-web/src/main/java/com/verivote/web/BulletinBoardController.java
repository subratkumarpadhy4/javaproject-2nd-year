package com.verivote.web;

import com.verivote.core.encoding.Hex;
import com.verivote.core.ledger.Block;
import com.verivote.core.ledger.LedgerService;
import com.verivote.data.entity.Checkpoint;
import com.verivote.data.entity.Vote;
import com.verivote.data.repo.CheckpointRepo;
import com.verivote.data.repo.VoteRepo;
import com.verivote.service.tally.TallyResult;
import com.verivote.service.tally.TallyService;
import com.verivote.web.dto.CheckpointResponse;
import com.verivote.web.dto.TallyResponse;
import com.verivote.web.dto.VerifyResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/bb")
public class BulletinBoardController {

    private final VoteRepo voteRepo;
    private final CheckpointRepo checkpointRepo;
    private final LedgerService ledgerService;
    private final TallyService tallyService;

    @Autowired
    public BulletinBoardController(
            VoteRepo voteRepo,
            CheckpointRepo checkpointRepo,
            LedgerService ledgerService,
            TallyService tallyService
    ) {
        this.voteRepo = voteRepo;
        this.checkpointRepo = checkpointRepo;
        this.ledgerService = ledgerService;
        this.tallyService = tallyService;
    }

    @GetMapping("/receipts")
    public List<String> getReceipts() {
        return voteRepo.findAll().stream()
                .map(Vote::getReceiptHash)
                .toList();
    }

    @GetMapping("/verify/{receiptHash}")
    public ResponseEntity<VerifyResponse> verifyReceipt(@PathVariable("receiptHash") String receiptHash) {
        Optional<Vote> voteOpt = voteRepo.findByReceiptHash(receiptHash);
        if (voteOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new VerifyResponse("NOT_FOUND", null, null, Collections.emptyList(), null));
        }

        Vote vote = voteOpt.get();
        long height = vote.getBlockHeight();

        Optional<Checkpoint> latestCpOpt = checkpointRepo.findLatest();
        if (latestCpOpt.isEmpty() || height >= latestCpOpt.get().getTreeSize()) {
            long treeSize = latestCpOpt.map(c -> (long) c.getTreeSize()).orElse(0L);
            return ResponseEntity.ok(new VerifyResponse("PENDING_INCLUSION", height, treeSize, Collections.emptyList(), null));
        }

        Checkpoint cp = latestCpOpt.get();
        long treeSize = cp.getTreeSize();
        List<byte[]> proofBytes = ledgerService.proof(height, treeSize);
        List<String> hexProof = proofBytes.stream().map(Hex::encode).toList();

        Block block = ledgerService.at(height)
                .orElseThrow(() -> new IllegalStateException("Block missing at height: " + height));

        return ResponseEntity.ok(new VerifyResponse("OK", height, treeSize, hexProof, block.voteHash()));
    }

    @GetMapping("/checkpoints")
    public List<CheckpointResponse> getCheckpoints() {
        return checkpointRepo.findAll().stream()
                .map(cp -> new CheckpointResponse(
                        cp.getTreeSize(),
                        cp.getMerkleRoot(),
                        cp.getHeadHash(),
                        cp.getSignature(),
                        cp.getCreatedAt() != null ? cp.getCreatedAt().getEpochSecond() : null
                ))
                .toList();
    }

    @GetMapping("/tally")
    public ResponseEntity<TallyResponse> getTally(@RequestParam(name = "electionId", defaultValue = "1") long electionId) {
        Optional<TallyResult> resOpt = tallyService.getLatestTally(electionId);
        if (resOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        TallyResult res = resOpt.get();
        return ResponseEntity.ok(new TallyResponse(
                res.counts(),
                res.invalidCount(),
                res.closeBlockHeight(),
                res.closeHeadHash(),
                res.chainMerkleRoot(),
                res.ballots(),
                res.tallyPublishedAtSec()
        ));
    }
}
