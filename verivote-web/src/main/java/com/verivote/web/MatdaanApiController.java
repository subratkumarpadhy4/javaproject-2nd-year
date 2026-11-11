package com.verivote.web;

import com.verivote.data.entity.Candidate;
import com.verivote.data.entity.Election;
import com.verivote.data.entity.Voter;
import com.verivote.data.repo.VoteRepo;
import com.verivote.data.repo.VoterRepo;
import com.verivote.service.VoterService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * REST API backing the Matdaan web portal (static/index.html + app.js).
 * Public: metrics, ballot candidates. Officer-only (Bearer token from
 * {@code POST /api/admin/login}): tally, election status, demo sample, reset.
 */
@RestController
@RequestMapping("/api")
public class MatdaanApiController {

    private final VoterRepo voterRepo;
    private final VoteRepo voteRepo;
    private final VoterService voterService;
    private final SessionStore sessionStore;
    private final MatdaanService matdaanService;
    private final String adminUsername;
    private final String adminPassword;

    public MatdaanApiController(
            VoterRepo voterRepo,
            VoteRepo voteRepo,
            VoterService voterService,
            SessionStore sessionStore,
            MatdaanService matdaanService,
            @Value("${verivote.admin.username:admin}") String adminUsername,
            @Value("${verivote.admin.password:admin123}") String adminPassword
    ) {
        this.voterRepo = voterRepo;
        this.voteRepo = voteRepo;
        this.voterService = voterService;
        this.sessionStore = sessionStore;
        this.matdaanService = matdaanService;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    // ------------------------------------------------------------------
    // Public
    // ------------------------------------------------------------------

    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getMetrics() {
        long registered = voterRepo.count();
        long votes = voteRepo.count();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("registered", registered);
        m.put("votes", votes);
        m.put("turnout", turnout(registered, votes));
        m.put("electionStatus", matdaanService.electionStatus());
        return ResponseEntity.ok(m);
    }

    @GetMapping("/candidates")
    public ResponseEntity<Map<String, Object>> getCandidates() {
        List<Map<String, Object>> list = matdaanService.candidates().stream()
                .sorted(ballotOrder())
                .map(matdaanService::candidateView)
                .toList();
        return ResponseEntity.ok(Map.of(
                "electionStatus", matdaanService.electionStatus(),
                "candidates", list
        ));
    }

    /** Published election result. Candidate tallies stay sealed until an officer publishes. */
    @GetMapping("/results")
    public ResponseEntity<Map<String, Object>> getResults() {
        if (!matdaanService.isPublished()) {
            return ResponseEntity.ok(Map.of(
                    "published", false,
                    "electionStatus", matdaanService.electionStatus()
            ));
        }
        return ResponseEntity.ok(resultsBody());
    }

    // ------------------------------------------------------------------
    // Officer authentication
    // ------------------------------------------------------------------

    @PostMapping("/admin/login")
    public ResponseEntity<?> adminLogin(@RequestBody Map<String, Object> body) {
        String user = body.get("username") == null ? "" : body.get("username").toString().trim();
        String pass = body.get("password") == null ? "" : body.get("password").toString();
        if (constantTimeEquals(user, adminUsername) & constantTimeEquals(pass, adminPassword)) {
            return ResponseEntity.ok(Map.of("success", true, "token", sessionStore.createAdminSession()));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Invalid officer credentials."));
    }

    @PostMapping("/admin/logout")
    public ResponseEntity<?> adminLogout(@RequestHeader(value = "Authorization", required = false) String auth) {
        sessionStore.invalidateAdmin(SessionStore.bearer(auth));
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/admin/session")
    public ResponseEntity<?> adminSession(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (!isAdmin(auth)) {
            return forbidden();
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ------------------------------------------------------------------
    // Officer-only
    // ------------------------------------------------------------------

    @GetMapping("/admin/tally")
    public ResponseEntity<?> getAdminTally(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (!isAdmin(auth)) {
            return forbidden();
        }

        long registered = voterRepo.count();
        long votes = voteRepo.count();
        MatdaanService.LiveTally live = matdaanService.liveTally();

        List<Map<String, Object>> tally = new ArrayList<>();
        for (Candidate c : matdaanService.candidates().stream().sorted(ballotOrder()).toList()) {
            Map<String, Object> row = matdaanService.candidateView(c);
            row.put("votes", live.counts().getOrDefault(c.getId(), 0L));
            tally.add(row);
        }

        List<Map<String, Object>> roll = new ArrayList<>();
        voterRepo.findAll().stream()
                .sorted(Comparator.comparing(Voter::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .forEach(v -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", v.getVoterId() != null ? v.getVoterId() : "—");
                    m.put("name", v.getDisplayName());
                    m.put("age", v.getAge());
                    m.put("state", v.getState());
                    m.put("registeredAt", v.getCreatedAt() != null ? v.getCreatedAt().toString() : null);
                    m.put("hasVoted", v.isHasVoted());
                    roll.add(m);
                });

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("registered", registered);
        resp.put("votes", votes);
        resp.put("turnout", turnout(registered, votes));
        resp.put("electionStatus", matdaanService.electionStatus());
        resp.put("tally", tally);
        resp.put("undecryptable", live.undecryptable());
        resp.put("voters", roll);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/admin/election-status")
    public ResponseEntity<?> setElectionStatus(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody Map<String, Object> body
    ) {
        if (!isAdmin(auth)) {
            return forbidden();
        }
        String s = body.get("status") == null ? "" : body.get("status").toString().trim().toUpperCase();
        if (!s.equals("OPEN") && !s.equals("CLOSED")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Status must be OPEN or CLOSED."));
        }
        Election e = matdaanService.setElectionStatus(Election.Status.valueOf(s));
        return ResponseEntity.ok(Map.of("success", true, "electionStatus", e.getStatus().name()));
    }

    @PostMapping("/admin/publish")
    public ResponseEntity<?> publishResults(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (!isAdmin(auth)) {
            return forbidden();
        }
        matdaanService.publishResults();
        return ResponseEntity.ok(resultsBody());
    }

    @PostMapping("/admin/sample")
    public ResponseEntity<?> loadSampleVotes(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (!isAdmin(auth)) {
            return forbidden();
        }
        if (!matdaanService.isElectionOpen()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Open the election before loading sample votes."));
        }

        String[][] samples = {
                {"Sunita Devi", "Uttar Pradesh", "34"},
                {"Rajesh Patil", "Maharashtra", "41"},
                {"Priya Nair", "Kerala", "28"},
                {"Mohammed Arif", "Telangana", "39"},
                {"Ananya Banerjee", "West Bengal", "31"},
                {"Gurpreet Singh", "Punjab", "45"},
                {"Vikramaditya Rao", "Karnataka", "52"},
                {"Kavita Joshi", "Uttarakhand", "26"},
                {"Deepak Sharma", "Rajasthan", "37"},
                {"Meenakshi Sundaram", "Tamil Nadu", "49"},
                {"Amitabh Verma", "Bihar", "33"},
                {"Lalitha Reddy", "Andhra Pradesh", "30"},
                {"Srinivas Goud", "Andhra Pradesh", "44"},
                {"Fatima Sheikh", "Delhi", "29"}
        };

        Optional<Candidate> bjp = matdaanService.resolveCandidate(null, "BJP");
        List<Candidate> options = matdaanService.candidates();
        if (bjp.isEmpty() || options.isEmpty()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "The BJP ballot option is missing, so sample votes cannot be generated."));
        }

        // Enrol the sample electors and keep those who have not voted yet.
        List<Voter> eligible = new ArrayList<>();
        int skipped = 0;
        for (int i = 0; i < samples.length; i++) {
            String voterId = "VTR" + (800100 + i);
            Optional<Voter> existing = voterRepo.findByVoterId(voterId);
            if (existing.isEmpty()) {
                voterService.register(voterId, samples[i][0], "1234", Integer.parseInt(samples[i][2]), samples[i][1]);
                existing = voterRepo.findByVoterId(voterId);
            }
            Voter voter = existing.orElseThrow();
            if (voter.isHasVoted()) {
                skipped++;
            } else {
                eligible.add(voter);
            }
        }

        // Fresh random split on every load, with BJP strictly ahead within this batch.
        List<Candidate> ballots = MatdaanService.sampleBallots(options, bjp.get(), eligible.size(), new java.security.SecureRandom());
        int cast = 0;
        for (int i = 0; i < eligible.size(); i++) {
            matdaanService.castBallot(eligible.get(i), ballots.get(i));
            cast++;
        }

        String message = cast > 0
                ? cast + " sample electors enrolled and ballots cast through the encrypted ledger."
                : "Sample electors have already voted. Reset the demo to load them again.";
        if (cast > 0 && skipped > 0) {
            message += " (" + skipped + " already voted, skipped.)";
        }
        return ResponseEntity.ok(Map.of("success", true, "cast", cast, "skipped", skipped, "message", message));
    }

    @PostMapping("/admin/reset")
    public ResponseEntity<?> resetData(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (!isAdmin(auth)) {
            return forbidden();
        }
        matdaanService.resetElectionRecords();
        sessionStore.clearVoterSessions();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Electoral roll and ballots cleared from the database. The append-only ledger file is retained."
        ));
    }

    // ------------------------------------------------------------------

    /**
     * Result payload. The winner is the party with the most votes; NOTA is shown
     * but cannot win (as in Indian elections). A tie at the top yields no winner.
     */
    private Map<String, Object> resultsBody() {
        MatdaanService.LiveTally live = matdaanService.liveTally();
        List<Map<String, Object>> rows = new ArrayList<>();
        long total = 0;
        for (Candidate c : matdaanService.candidates().stream().sorted(ballotOrder()).toList()) {
            Map<String, Object> row = matdaanService.candidateView(c);
            long v = live.counts().getOrDefault(c.getId(), 0L);
            row.put("votes", v);
            total += v;
            rows.add(row);
        }

        List<Map<String, Object>> contenders = rows.stream()
                .filter(r -> !"NOTA".equalsIgnoreCase(String.valueOf(r.get("shortName"))))
                .sorted(Comparator.comparingLong((Map<String, Object> r) -> (Long) r.get("votes")).reversed())
                .toList();
        Map<String, Object> winner = null;
        boolean tie = false;
        if (!contenders.isEmpty() && (Long) contenders.get(0).get("votes") > 0) {
            long top = (Long) contenders.get(0).get("votes");
            tie = contenders.size() > 1 && (Long) contenders.get(1).get("votes") == top;
            if (!tie) {
                winner = contenders.get(0);
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("published", true);
        body.put("electionStatus", matdaanService.electionStatus());
        body.put("publishedAt", matdaanService.publishedAt().map(Object::toString).orElse(null));
        body.put("totalVotes", total);
        body.put("results", rows);
        body.put("winner", winner);
        body.put("tie", tie);
        body.put("undecryptable", live.undecryptable());
        return body;
    }

    private boolean isAdmin(String authHeader) {
        return sessionStore.isAdminSession(SessionStore.bearer(authHeader));
    }

    private static ResponseEntity<Map<String, Object>> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Access denied: election officer sign-in required."));
    }

    private static double turnout(long registered, long votes) {
        double t = registered > 0 ? votes * 100.0 / registered : 0.0;
        return Math.round(t * 10.0) / 10.0;
    }

    /** Alphabetical by short name with NOTA last, matching the ballot paper convention. */
    private static Comparator<Candidate> ballotOrder() {
        return Comparator
                .comparing((Candidate c) -> "NOTA".equalsIgnoreCase(MatdaanService.shortName(c)))
                .thenComparing(c -> MatdaanService.shortName(c).toUpperCase());
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
