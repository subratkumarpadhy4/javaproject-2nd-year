package com.verivote.web;

import com.verivote.core.encoding.Base64Codec;
import com.verivote.core.ledger.LedgerService;
import com.verivote.data.entity.Candidate;
import com.verivote.data.entity.Election;
import com.verivote.data.entity.Operator;
import com.verivote.data.entity.Vote;
import com.verivote.data.repo.CandidateRepo;
import com.verivote.data.repo.ElectionRepo;
import com.verivote.data.repo.IssuanceCounterRepo;
import com.verivote.data.repo.IssuancePublishedRepo;
import com.verivote.data.repo.OperatorRepo;
import com.verivote.data.repo.VoteRepo;
import com.verivote.data.repo.VoterRepo;
import com.verivote.service.audit.AuditService;
import com.verivote.service.audit.Finding;
import com.verivote.service.audit.TamperReport;
import com.verivote.service.recovery.OperatorService;
import com.verivote.service.recovery.RecoveryRequest;
import com.verivote.service.recovery.RecoveryResult;
import com.verivote.service.recovery.RecoveryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AuditService auditService;
    private final RecoveryService recoveryService;
    private final OperatorService operatorService;
    private final OperatorRepo operatorRepo;
    private final IssuanceCounterRepo counterRepo;
    private final IssuancePublishedRepo publishedRepo;
    private final LedgerService ledgerService;
    private final ElectionRepo electionRepo;
    private final CandidateRepo candidateRepo;
    private final VoteRepo voteRepo;
    private final VoterRepo voterRepo;

    @Autowired
    public AdminController(
            AuditService auditService,
            RecoveryService recoveryService,
            OperatorService operatorService,
            OperatorRepo operatorRepo,
            IssuanceCounterRepo counterRepo,
            IssuancePublishedRepo publishedRepo,
            LedgerService ledgerService,
            ElectionRepo electionRepo,
            CandidateRepo candidateRepo,
            VoteRepo voteRepo,
            VoterRepo voterRepo
    ) {
        this.auditService = auditService;
        this.recoveryService = recoveryService;
        this.operatorService = operatorService;
        this.operatorRepo = operatorRepo;
        this.counterRepo = counterRepo;
        this.publishedRepo = publishedRepo;
        this.ledgerService = ledgerService;
        this.electionRepo = electionRepo;
        this.candidateRepo = candidateRepo;
        this.voteRepo = voteRepo;
        this.voterRepo = voterRepo;
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Optional<Election> electionOpt = electionRepo.findById(1L);
        return ResponseEntity.ok(Map.of(
                "registeredVoters", voterRepo.count(),
                "castVotes", voteRepo.count(),
                "ledgerBlocks", ledgerService.blockCount(),
                "realVotes", ledgerService.realVoteCount(),
                "orphanCount", ledgerService.orphanCount(),
                "electionStatus", electionOpt.map(e -> e.getStatus().name()).orElse("OPEN")
        ));
    }

    @GetMapping("/election")
    public ResponseEntity<Map<String, Object>> getAdminElection() {
        Optional<Election> electionOpt = electionRepo.findById(1L);
        Election election = electionOpt.orElseGet(() -> new Election("Indian General Election (Educational Demonstration)"));
        List<Candidate> candidates = candidateRepo.findByElectionId(1L);
        return ResponseEntity.ok(Map.of(
                "election", election,
                "candidates", candidates
        ));
    }

    @PostMapping("/election/status")
    public ResponseEntity<?> setElectionStatus(@RequestBody Map<String, String> body) {
        String statusStr = body.get("status");
        if (statusStr == null || statusStr.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Status is required (OPEN or CLOSED)"));
        }
        Election.Status newStatus;
        try {
            newStatus = Election.Status.valueOf(statusStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid status: " + statusStr));
        }

        Election election = electionRepo.findById(1L).orElseGet(() -> new Election("Indian General Election (Educational Demonstration)"));
        election.setStatus(newStatus);
        electionRepo.saveAndFlush(election);
        return ResponseEntity.ok(Map.of("status", newStatus.name()));
    }

    @PostMapping("/candidates")
    public ResponseEntity<?> addCandidate(@RequestBody Map<String, String> body) {
        String party = body.get("party");
        String name = body.get("name");
        String constituency = body.get("constituency");
        String symbol = body.get("symbol");

        if (party == null || party.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Party name is required"));
        }
        if (name == null || name.isBlank()) {
            name = "Party Nominee";
        }
        if (constituency == null || constituency.isBlank()) {
            constituency = "National Demo Constituency 01";
        }

        Candidate candidate = new Candidate(1L, name.trim(), party.trim(), constituency.trim(), symbol != null ? symbol.trim() : "");
        Candidate saved = candidateRepo.saveAndFlush(candidate);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/candidates/{id}")
    public ResponseEntity<?> deleteCandidate(@PathVariable("id") Long id) {
        if (!candidateRepo.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Candidate option not found"));
        }
        candidateRepo.deleteById(id);
        candidateRepo.flush();
        return ResponseEntity.ok(Map.of("deleted", id));
    }

    @GetMapping("/votes")
    public ResponseEntity<List<Map<String, Object>>> getSubmittedVotes() {
        List<Vote> votes = voteRepo.findAll();
        List<Map<String, Object>> result = votes.stream()
                .map(v -> Map.<String, Object>of(
                        "id", v.getId(),
                        "blockHeight", v.getBlockHeight(),
                        "receiptHash", v.getReceiptHash(),
                        "blockHash", v.getBlockHash(),
                        "electionId", v.getElectionId()
                ))
                .toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping(value = "/export/votes", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> exportVotesCsv() {
        List<Vote> votes = voteRepo.findAll();
        StringBuilder sb = new StringBuilder("id,blockHeight,receiptHash,blockHash,electionId\n");
        for (Vote v : votes) {
            sb.append(v.getId()).append(",")
                    .append(v.getBlockHeight()).append(",")
                    .append(v.getReceiptHash()).append(",")
                    .append(v.getBlockHash()).append(",")
                    .append(v.getElectionId()).append("\n");
        }
        return ResponseEntity.ok(sb.toString());
    }

    @GetMapping(value = "/audit", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> audit() {
        TamperReport report = auditService.audit();
        return ResponseEntity.ok(tamperReportToJson(report));
    }

    @GetMapping(value = "/ledger-status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> ledgerStatus() {
        String head = ledgerService.headHash();
        String json = "{\"blockCount\":" + ledgerService.blockCount()
                + ",\"realVoteCount\":" + ledgerService.realVoteCount()
                + ",\"orphanCount\":" + ledgerService.orphanCount()
                + ",\"headHash\":" + (head != null ? "\"" + escapeJson(head) + "\"" : "null")
                + "}";
        return ResponseEntity.ok(json);
    }

    @PostMapping(value = "/recover", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> recover(@RequestBody Map<String, Object> body) {
        Object rawHeight = body.get("blockHeight");
        long blockHeight = rawHeight instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(rawHeight));

        String actionStr = (String) body.get("action");
        if (actionStr == null || actionStr.isBlank()) {
            throw new IllegalArgumentException("action is required");
        }
        RecoveryRequest.Action action = RecoveryRequest.Action.valueOf(actionStr.trim().toUpperCase());

        String reason = (String) body.get("reason");
        String blockHash = (String) body.get("blockHash");
        String tokenHash = (String) body.get("tokenHash");
        String op1 = (String) body.get("operator1");
        String sig1Str = (String) body.get("signature1");
        byte[] sig1 = (sig1Str != null && !sig1Str.isBlank()) ? Base64Codec.decode(sig1Str) : null;

        String op2 = (String) body.get("operator2");
        String sig2Str = (String) body.get("signature2");
        byte[] sig2 = (sig2Str != null && !sig2Str.isBlank()) ? Base64Codec.decode(sig2Str) : null;

        RecoveryRequest req = new RecoveryRequest(
                blockHeight, action, reason, blockHash, tokenHash, op1, sig1, op2, sig2
        );

        RecoveryResult res = recoveryService.execute(req);
        String json = "{\"eventId\":" + res.eventId()
                + ",\"blockHeight\":" + res.blockHeight()
                + ",\"action\":\"" + res.action().name() + "\"}";
        return ResponseEntity.ok(json);
    }

    @PostMapping(value = "/operators", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> enrollOperator(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        String pub = body.get("ed25519Pub");
        String role = body.get("role");
        String createdBy = body.getOrDefault("createdBy", "admin");
        if (createdBy == null || createdBy.isBlank()) {
            createdBy = "admin";
        }
        Operator op = operatorService.enroll(name, pub, role, createdBy);
        String json = "{\"id\":" + op.getId() + ",\"name\":\"" + escapeJson(op.getName()) + "\"}";
        return ResponseEntity.ok(json);
    }

    @GetMapping(value = "/operators", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> listOperators() {
        List<Operator> list = operatorRepo.findAll();
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        Instant now = Instant.now();
        for (int i = 0; i < list.size(); i++) {
            Operator op = list.get(i);
            if (i > 0) sb.append(",");
            boolean active = (op.getActiveFrom() == null || !op.getActiveFrom().isAfter(now))
                    && (op.getActiveTo() == null || op.getActiveTo().isAfter(now));
            sb.append("{")
                    .append("\"id\":").append(op.getId()).append(",")
                    .append("\"name\":\"").append(escapeJson(op.getName())).append("\",")
                    .append("\"role\":\"").append(op.getRole().name()).append("\",")
                    .append("\"active\":").append(active)
                    .append("}");
        }
        sb.append("]");
        return ResponseEntity.ok(sb.toString());
    }

    private String tamperReportToJson(TamperReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"clean\":").append(report.clean()).append(",");
        sb.append("\"auditedAtSec\":").append(report.auditedAtSec()).append(",");
        sb.append("\"ledgerBlockCount\":").append(report.ledgerBlockCount()).append(",");
        sb.append("\"ledgerRealVoteCount\":").append(report.ledgerRealVoteCount()).append(",");
        sb.append("\"ledgerOrphanCount\":").append(report.ledgerOrphanCount()).append(",");
        sb.append("\"dbVoteCount\":").append(report.dbVoteCount()).append(",");
        sb.append("\"findings\":[");
        List<Finding> findings = report.findings();
        for (int i = 0; i < findings.size(); i++) {
            Finding f = findings.get(i);
            if (i > 0) sb.append(",");
            sb.append("{");
            sb.append("\"severity\":\"").append(f.severity().name()).append("\",");
            sb.append("\"code\":\"").append(f.code().name()).append("\",");
            sb.append("\"message\":\"").append(escapeJson(f.message())).append("\",");
            sb.append("\"blockHeight\":").append(f.blockHeight() == null ? "null" : f.blockHeight()).append(",");
            sb.append("\"treeSize\":").append(f.treeSize() == null ? "null" : f.treeSize()).append(",");
            sb.append("\"remediation\":").append(f.remediation() == null ? "null" : "\"" + escapeJson(f.remediation()) + "\"");
            sb.append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
