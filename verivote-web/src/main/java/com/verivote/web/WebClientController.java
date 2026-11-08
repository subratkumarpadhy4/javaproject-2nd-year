package com.verivote.web;

import com.verivote.core.crypto.BlindSignatureService;
import com.verivote.core.domain.DomainStrings;
import com.verivote.core.encoding.Base64Codec;
import com.verivote.core.encoding.BigEndian;
import com.verivote.core.encoding.Hex;
import com.verivote.core.ledger.Block;
import com.verivote.core.ledger.LedgerService;
import com.verivote.data.entity.Checkpoint;
import com.verivote.data.entity.Vote;
import com.verivote.data.repo.CheckpointRepo;
import com.verivote.data.repo.VoteRepo;
import com.verivote.service.TokenService;
import com.verivote.service.VoteService;
import com.verivote.service.checkpoint.CheckpointService;
import com.verivote.service.dto.TokenIssuanceResponse;
import com.verivote.service.dto.VoteRequest;
import com.verivote.service.dto.VoteResponse;
import com.verivote.service.tally.TallyKeyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;

@RestController
@RequestMapping("/api/web")
public class WebClientController {

    private final TokenService tokenService;
    private final VoteService voteService;
    private final TallyKeyService tallyKeyService;
    private final SessionStore sessionStore;
    private final CheckpointService checkpointService;
    private final CheckpointRepo checkpointRepo;
    private final VoteRepo voteRepo;
    private final LedgerService ledgerService;
    private final SecureRandom rnd = new SecureRandom();

    public WebClientController(
            TokenService tokenService,
            VoteService voteService,
            TallyKeyService tallyKeyService,
            SessionStore sessionStore,
            CheckpointService checkpointService,
            CheckpointRepo checkpointRepo,
            VoteRepo voteRepo,
            LedgerService ledgerService
    ) {
        this.tokenService = tokenService;
        this.voteService = voteService;
        this.tallyKeyService = tallyKeyService;
        this.sessionStore = sessionStore;
        this.checkpointService = checkpointService;
        this.checkpointRepo = checkpointRepo;
        this.voteRepo = voteRepo;
        this.ledgerService = ledgerService;
    }

    @PostMapping("/request-token")
    public ResponseEntity<?> requestToken(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String sessionToken = null;
        if (authHeader != null && authHeader.toLowerCase().startsWith("bearer ")) {
            sessionToken = authHeader.substring(7).trim();
        } else if (body != null && body.containsKey("sessionToken")) {
            sessionToken = body.get("sessionToken");
        }

        if (sessionToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "missing session token"));
        }

        Optional<Long> voterIdOpt = sessionStore.getVoterId(sessionToken);
        if (voterIdOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "invalid or expired session"));
        }

        long eid = 1L;
        byte[] tokenPlain = new byte[32];
        rnd.nextBytes(tokenPlain);

        byte[] prefix = DomainStrings.TOKEN_PREFIX.getBytes(StandardCharsets.US_ASCII);
        byte[] eidBytes = BigEndian.u64be(eid);
        byte[] preimage = new byte[prefix.length + eidBytes.length + tokenPlain.length];
        System.arraycopy(prefix, 0, preimage, 0, prefix.length);
        System.arraycopy(eidBytes, 0, preimage, prefix.length, eidBytes.length);
        System.arraycopy(tokenPlain, 0, preimage, prefix.length + eidBytes.length, tokenPlain.length);

        BlindSignatureService.KeyPair kp = tokenService.getElectionKeyPair();
        BigInteger[] bf = new BigInteger[1];
        byte[] blinded = BlindSignatureService.blind(preimage, kp, rnd, bf);

        TokenIssuanceResponse issuance = tokenService.issueToken(voterIdOpt.get(), blinded);
        byte[] sigBytes = Base64Codec.decode(issuance.signature());
        byte[] unblinded = BlindSignatureService.unblind(sigBytes, bf[0].toByteArray(), kp, 256);

        return ResponseEntity.ok(Map.of(
                "tokenPlain", Base64Codec.encode(tokenPlain),
                "signature", Base64Codec.encode(unblinded)
        ));
    }

    @PostMapping("/cast-vote")
    public ResponseEntity<?> castVote(@RequestBody Map<String, Object> body) {
        int candidateId = body.get("candidateId") instanceof Number n ? n.intValue() : 1;
        String tokenPlainB64 = (String) body.get("tokenPlain");
        String signatureB64 = (String) body.get("signature");

        if (tokenPlainB64 == null || signatureB64 == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "tokenPlain and signature are required"));
        }

        byte[] tokenPlain = Base64Codec.decode(tokenPlainB64);
        byte[] signature = Base64Codec.decode(signatureB64);

        byte[] ballotId = new byte[32];
        rnd.nextBytes(ballotId);

        byte[] ciphertext = TallyKeyService.encryptVote(tallyKeyService.getPublicKey(), 1L, candidateId, ballotId);

        byte[] clientNonce = new byte[32];
        rnd.nextBytes(clientNonce);

        VoteRequest req = new VoteRequest(ciphertext, tokenPlain, signature, clientNonce);
        VoteResponse resp = voteService.castVote(req);

        try {
            checkpointService.forceNow();
        } catch (Exception ignored) {}

        return ResponseEntity.ok(Map.of(
                "receiptHash", resp.receiptHash(),
                "blockHeight", resp.blockHeight(),
                "blockHash", resp.blockHash()
        ));
    }

    @GetMapping("/verify/{receiptHash}")
    public ResponseEntity<?> verify(@PathVariable("receiptHash") String receiptHash) {
        Optional<Vote> voteOpt = voteRepo.findByReceiptHash(receiptHash);
        if (voteOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Receipt not found in ledger"));
        }

        Vote vote = voteOpt.get();
        long height = vote.getBlockHeight();

        try {
            checkpointService.forceNow();
        } catch (Exception ignored) {}

        Optional<Checkpoint> cpOpt = checkpointRepo.findLatest();
        if (cpOpt.isEmpty() || height >= cpOpt.get().getTreeSize()) {
            return ResponseEntity.ok(Map.of(
                    "status", "PENDING_INCLUSION",
                    "blockHeight", height
            ));
        }

        Checkpoint cp = cpOpt.get();
        long treeSize = cp.getTreeSize();
        List<byte[]> proofBytes = ledgerService.proof(height, treeSize);
        List<String> hexProof = proofBytes.stream().map(Hex::encode).toList();

        Block block = ledgerService.at(height).orElseThrow();

        return ResponseEntity.ok(Map.of(
                "status", "VERIFIED",
                "blockHeight", height,
                "treeSize", treeSize,
                "merkleRoot", cp.getMerkleRoot(),
                "proof", hexProof,
                "voteHash", block.voteHash(),
                "checkpointSignature", cp.getSignature()
        ));
    }
}
