package com.verivote.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verivote.data.entity.Candidate;
import com.verivote.data.entity.Voter;
import com.verivote.data.repo.VoterRepo;
import com.verivote.service.VoteService;
import com.verivote.service.dto.VoteRequest;
import com.verivote.service.dto.VoteResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class VoteController {

    private static final DateTimeFormatter RECEIPT_TIME =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    private final VoteService voteService;
    private final VoterRepo voterRepo;
    private final SessionStore sessionStore;
    private final MatdaanService matdaanService;
    private final ObjectMapper objectMapper;

    public VoteController(
            VoteService voteService,
            VoterRepo voterRepo,
            SessionStore sessionStore,
            MatdaanService matdaanService,
            ObjectMapper objectMapper
    ) {
        this.voteService = voteService;
        this.voterRepo = voterRepo;
        this.sessionStore = sessionStore;
        this.matdaanService = matdaanService;
        this.objectMapper = objectMapper;
    }

    /**
     * Two request shapes are accepted:
     * <ul>
     *   <li>Matdaan portal ballot: {@code {"candidateId": 3}} or {@code {"partyCode": "BJP"}}
     *       with {@code Authorization: Bearer <sessionToken>} from {@code /api/login}.</li>
     *   <li>Raw cryptographic {@link VoteRequest} (ciphertext, tokenPlain, signature, clientNonce)
     *       — anonymous by design, used by the desktop client and tests.</li>
     * </ul>
     */
    @PostMapping("/vote")
    public ResponseEntity<?> vote(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        boolean portalBallot = body.containsKey("partyCode") || body.containsKey("candidateId");
        if (portalBallot) {
            return castPortalBallot(body, authHeader);
        }

        try {
            VoteRequest req = objectMapper.convertValue(body, VoteRequest.class);
            VoteResponse resp = voteService.castVote(req);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(e.getMessage())));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }

    private ResponseEntity<?> castPortalBallot(Map<String, Object> body, String authHeader) {
        Optional<Long> voterPk = sessionStore.getVoterId(SessionStore.bearer(authHeader));
        if (voterPk.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Your session has expired. Please sign in again to vote."));
        }
        Optional<Voter> voterOpt = voterRepo.findById(voterPk.get());
        if (voterOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Elector record not found. Please sign in again."));
        }
        Voter voter = voterOpt.get();

        if (!matdaanService.isElectionOpen()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "The election is currently CLOSED by the electoral administrator."));
        }
        if (voter.isHasVoted()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "You have already voted. Only one vote per elector is permitted."));
        }

        Optional<Candidate> candidate = matdaanService.resolveCandidate(body.get("candidateId"), body.get("partyCode"));
        if (candidate.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Unknown ballot option. Please reload the ballot and choose again."));
        }

        try {
            VoteResponse resp = matdaanService.castBallot(voter, candidate.get());
            String timestamp = ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).format(RECEIPT_TIME) + " IST";
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Ballot securely recorded.",
                    "receiptHash", resp.receiptHash(),
                    "blockHeight", resp.blockHeight(),
                    "blockHash", resp.blockHash(),
                    "timestamp", timestamp
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", String.valueOf(e.getMessage())));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Ballot could not be recorded: " + e.getMessage()));
        }
    }
}
