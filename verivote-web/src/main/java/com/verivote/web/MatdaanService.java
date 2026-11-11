package com.verivote.web;

import com.verivote.core.crypto.BlindSignatureService;
import com.verivote.core.domain.DomainStrings;
import com.verivote.core.encoding.Base64Codec;
import com.verivote.core.encoding.BigEndian;
import com.verivote.data.entity.Candidate;
import com.verivote.data.entity.Election;
import com.verivote.data.entity.Vote;
import com.verivote.data.entity.Voter;
import com.verivote.data.repo.CandidateRepo;
import com.verivote.data.repo.ElectionRepo;
import com.verivote.data.repo.IssuanceCounterRepo;
import com.verivote.data.repo.SpentTokenRepo;
import com.verivote.data.repo.VoteRepo;
import com.verivote.data.repo.VoterRepo;
import com.verivote.service.TokenService;
import com.verivote.service.VoteService;
import com.verivote.service.checkpoint.CheckpointService;
import com.verivote.service.dto.TokenIssuanceResponse;
import com.verivote.service.dto.VoteRequest;
import com.verivote.service.dto.VoteResponse;
import com.verivote.service.tally.TallyKeyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Backend logic for the Matdaan web portal (static/index.html + app.js).
 *
 * The browser never handles key material: the server performs the blind-token
 * issuance and RSA-OAEP ballot encryption on the voter's behalf, then casts the
 * ballot through the same {@link VoteService} pipeline (spent-token check,
 * append-only ledger, DB row) used by the cryptographic API.
 */
@Service
public class MatdaanService {

    public static final long ELECTION_ID = 1L;

    private final VoterRepo voterRepo;
    private final VoteRepo voteRepo;
    private final CandidateRepo candidateRepo;
    private final ElectionRepo electionRepo;
    private final SpentTokenRepo spentTokenRepo;
    private final IssuanceCounterRepo issuanceCounterRepo;
    private final TokenService tokenService;
    private final VoteService voteService;
    private final TallyKeyService tallyKeyService;
    private final CheckpointService checkpointService;
    private final SecureRandom rnd = new SecureRandom();

    // Live-tally cache: decrypting every ballot on each 3-second admin poll is wasteful.
    private volatile long cachedVoteCount = -1;
    private volatile Long cachedMaxVoteId = null;
    private volatile LiveTally cachedTally = null;

    public MatdaanService(
            VoterRepo voterRepo,
            VoteRepo voteRepo,
            CandidateRepo candidateRepo,
            ElectionRepo electionRepo,
            SpentTokenRepo spentTokenRepo,
            IssuanceCounterRepo issuanceCounterRepo,
            TokenService tokenService,
            VoteService voteService,
            TallyKeyService tallyKeyService,
            CheckpointService checkpointService
    ) {
        this.voterRepo = voterRepo;
        this.voteRepo = voteRepo;
        this.candidateRepo = candidateRepo;
        this.electionRepo = electionRepo;
        this.spentTokenRepo = spentTokenRepo;
        this.issuanceCounterRepo = issuanceCounterRepo;
        this.tokenService = tokenService;
        this.voteService = voteService;
        this.tallyKeyService = tallyKeyService;
        this.checkpointService = checkpointService;
    }

    public record LiveTally(Map<Long, Long> counts, long undecryptable) {}

    // ------------------------------------------------------------------
    // Election / candidates
    // ------------------------------------------------------------------

    public String electionStatus() {
        return electionRepo.findById(ELECTION_ID)
                .map(e -> e.getStatus().name())
                .orElse(Election.Status.OPEN.name());
    }

    public boolean isElectionOpen() {
        return electionRepo.findById(ELECTION_ID)
                .map(e -> e.getStatus() == Election.Status.OPEN)
                .orElse(true);
    }

    public List<Candidate> candidates() {
        return candidateRepo.findByElectionId(ELECTION_ID);
    }

    /** Short ballot label, e.g. "BJP", "CPI(M)", "JD(U)", "NOTA". */
    public static String shortName(Candidate c) {
        String party = c.getParty() == null ? "" : c.getParty().trim();
        int dash = party.lastIndexOf('—');
        if (dash >= 0) {
            return party.substring(dash + 1).trim();
        }
        int open = party.lastIndexOf('(');
        int close = party.lastIndexOf(')');
        if (open >= 0 && close > open) {
            return party.substring(open + 1, close).trim();
        }
        return c.getSymbol() != null && !c.getSymbol().isBlank() ? c.getSymbol().toUpperCase() : party;
    }

    /** Full party name without the abbreviation suffix. */
    public static String fullName(Candidate c) {
        String party = c.getParty() == null ? "" : c.getParty().trim();
        int dash = party.lastIndexOf('—');
        if (dash >= 0) {
            return party.substring(0, dash).trim();
        }
        int open = party.lastIndexOf('(');
        if (open > 0 && party.endsWith(")")) {
            return party.substring(0, open).trim();
        }
        return party;
    }

    /** Stable machine code used by the frontend, e.g. "bjp", "cpim". */
    public static String code(Candidate c) {
        if (c.getSymbol() != null && !c.getSymbol().isBlank()) {
            return c.getSymbol().trim().toLowerCase();
        }
        return shortName(c).replaceAll("[^A-Za-z0-9]", "").toLowerCase();
    }

    public Map<String, Object> candidateView(Candidate c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("code", code(c));
        m.put("shortName", shortName(c));
        m.put("party", fullName(c));
        m.put("nominee", c.getName());
        m.put("constituency", c.getConstituency());
        return m;
    }

    /**
     * Resolve a ballot choice by candidate id, symbol code ("cpim") or short
     * name ("CPI(M)"). Never falls back to an arbitrary candidate.
     */
    public Optional<Candidate> resolveCandidate(Object candidateId, Object partyCode) {
        List<Candidate> all = candidates();
        if (candidateId != null && !candidateId.toString().isBlank()) {
            try {
                long id = Long.parseLong(candidateId.toString().trim());
                return all.stream().filter(c -> c.getId() == id).findFirst();
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }
        if (partyCode == null) {
            return Optional.empty();
        }
        String wanted = partyCode.toString().trim();
        String wantedNorm = wanted.replaceAll("[^A-Za-z0-9]", "");
        return all.stream()
                .filter(c -> code(c).equalsIgnoreCase(wanted)
                        || shortName(c).equalsIgnoreCase(wanted)
                        || shortName(c).replaceAll("[^A-Za-z0-9]", "").equalsIgnoreCase(wantedNorm))
                .findFirst();
    }

    // ------------------------------------------------------------------
    // Voting
    // ------------------------------------------------------------------

    /**
     * Issues a blind-signed token to the voter (marks them as voted), encrypts the
     * ballot for the chosen candidate and casts it into the ledger.
     *
     * @throws IllegalStateException if the voter already voted or the election is closed
     */
    public VoteResponse castBallot(Voter voter, Candidate candidate) {
        if (!isElectionOpen()) {
            throw new IllegalStateException("The election is currently CLOSED by the electoral administrator.");
        }
        if (voter.isHasVoted()) {
            throw new IllegalStateException("You have already voted. Only one vote per elector is permitted.");
        }

        byte[] tokenPlain = new byte[32];
        rnd.nextBytes(tokenPlain);

        byte[] prefix = DomainStrings.TOKEN_PREFIX.getBytes(StandardCharsets.US_ASCII);
        byte[] eidBytes = BigEndian.u64be(ELECTION_ID);
        byte[] preimage = new byte[prefix.length + eidBytes.length + tokenPlain.length];
        System.arraycopy(prefix, 0, preimage, 0, prefix.length);
        System.arraycopy(eidBytes, 0, preimage, prefix.length, eidBytes.length);
        System.arraycopy(tokenPlain, 0, preimage, prefix.length + eidBytes.length, tokenPlain.length);

        BlindSignatureService.KeyPair kp = tokenService.getElectionKeyPair();
        BigInteger[] bf = new BigInteger[1];
        byte[] blinded = BlindSignatureService.blind(preimage, kp, rnd, bf);

        // Marks voter.hasVoted under a row lock; throws "already voted" on a race.
        TokenIssuanceResponse issuance = tokenService.issueToken(voter.getId(), blinded);
        byte[] sigBytes = Base64Codec.decode(issuance.signature());
        byte[] unblinded = BlindSignatureService.unblind(sigBytes, bf[0].toByteArray(), kp, 256);

        byte[] ballotId = new byte[32];
        rnd.nextBytes(ballotId);
        byte[] ciphertext = TallyKeyService.encryptVote(
                tallyKeyService.getPublicKey(), ELECTION_ID, candidate.getId().intValue(), ballotId);

        byte[] clientNonce = new byte[32];
        rnd.nextBytes(clientNonce);

        VoteResponse resp = voteService.castVote(new VoteRequest(ciphertext, tokenPlain, unblinded, clientNonce));
        try {
            checkpointService.forceNow();
        } catch (Exception ignored) {
            // checkpointing is best-effort; the ballot is already in the ledger
        }
        return resp;
    }

    // ------------------------------------------------------------------
    // Tally
    // ------------------------------------------------------------------

    /**
     * Decrypts the ballots stored in the database and counts them per candidate.
     * Ballots encrypted under a previous key epoch (e.g. before key persistence was
     * enabled) cannot be decrypted and are reported as {@code undecryptable}.
     */
    public synchronized LiveTally liveTally() {
        List<Vote> votes = voteRepo.findAll();
        Long maxId = votes.stream().map(Vote::getId).max(Long::compare).orElse(null);
        if (cachedTally != null && cachedVoteCount == votes.size() && java.util.Objects.equals(cachedMaxVoteId, maxId)) {
            return cachedTally;
        }

        Map<Long, Long> counts = new LinkedHashMap<>();
        for (Candidate c : candidates()) {
            counts.put(c.getId(), 0L);
        }
        long undecryptable = 0;

        if (!votes.isEmpty()) {
            PrivateKey priv = tallyKeyService.reconstructAndGetPrivateKey(new ArrayList<>(tallyKeyService.getShares()));
            for (Vote v : votes) {
                try {
                    Cipher c = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
                    c.init(Cipher.DECRYPT_MODE, priv);
                    byte[] pt = c.doFinal(Base64Codec.decode(v.getEncryptedVote()));
                    ByteBuffer bb = ByteBuffer.wrap(pt);
                    long eid = bb.getLong();
                    long candidateId = bb.getInt();
                    if (eid == ELECTION_ID && counts.containsKey(candidateId)) {
                        counts.merge(candidateId, 1L, Long::sum);
                    } else {
                        undecryptable++;
                    }
                } catch (Exception e) {
                    undecryptable++;
                }
            }
        }

        LiveTally result = new LiveTally(counts, undecryptable);
        cachedTally = result;
        cachedVoteCount = votes.size();
        cachedMaxVoteId = maxId;
        return result;
    }

    // ------------------------------------------------------------------
    // Demo utilities
    // ------------------------------------------------------------------

    /**
     * Clears database election records: votes, spent tokens, issuance counters and
     * the electoral roll. The ledger file is append-only by design and is kept.
     */
    @Transactional
    public void resetElectionRecords() {
        voteRepo.deleteAllInBatch();
        spentTokenRepo.deleteAllInBatch();
        // Zero the counters rather than deleting them: TokenService recreates a missing
        // counter with an assigned id, which Hibernate rejects as a stale update.
        issuanceCounterRepo.findAll().forEach(c -> c.setIssued(0L));
        issuanceCounterRepo.flush();
        voterRepo.deleteAllInBatch();
        electionRepo.findById(ELECTION_ID).ifPresent(e -> {
            e.setStatus(Election.Status.OPEN);
            electionRepo.saveAndFlush(e);
        });
        cachedTally = null;
        cachedVoteCount = -1;
        cachedMaxVoteId = null;
    }

    /**
     * Publishes the result: polling closes and the election moves to TALLIED.
     * Results are recomputed from the (now frozen) ballots on each read, so they
     * survive restarts without a separate results table.
     */
    public Election publishResults() {
        Election election = electionRepo.findById(ELECTION_ID)
                .orElseGet(() -> new Election("Indian General Election (Educational Demonstration)"));
        election.setStatus(Election.Status.TALLIED);
        election.setClosesAt(java.time.Instant.now());
        return electionRepo.saveAndFlush(election);
    }

    public boolean isPublished() {
        return electionRepo.findById(ELECTION_ID)
                .map(e -> e.getStatus() == Election.Status.TALLIED)
                .orElse(false);
    }

    public Optional<java.time.Instant> publishedAt() {
        return electionRepo.findById(ELECTION_ID)
                .filter(e -> e.getStatus() == Election.Status.TALLIED)
                .map(Election::getClosesAt);
    }

    public Election setElectionStatus(Election.Status status) {
        Election election = electionRepo.findById(ELECTION_ID)
                .orElseGet(() -> new Election("Indian General Election (Educational Demonstration)"));
        election.setStatus(status);
        return electionRepo.saveAndFlush(election);
    }
}
