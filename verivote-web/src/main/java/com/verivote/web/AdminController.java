package com.verivote.web;

import com.verivote.core.encoding.Base64Codec;
import com.verivote.core.ledger.LedgerService;
import com.verivote.data.entity.Operator;
import com.verivote.data.repo.IssuanceCounterRepo;
import com.verivote.data.repo.IssuancePublishedRepo;
import com.verivote.data.repo.OperatorRepo;
import com.verivote.service.audit.AuditService;
import com.verivote.service.audit.Finding;
import com.verivote.service.audit.TamperReport;
import com.verivote.service.recovery.OperatorService;
import com.verivote.service.recovery.RecoveryRequest;
import com.verivote.service.recovery.RecoveryResult;
import com.verivote.service.recovery.RecoveryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

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

    @Autowired
    public AdminController(
            AuditService auditService,
            RecoveryService recoveryService,
            OperatorService operatorService,
            OperatorRepo operatorRepo,
            IssuanceCounterRepo counterRepo,
            IssuancePublishedRepo publishedRepo,
            LedgerService ledgerService
    ) {
        this.auditService = auditService;
        this.recoveryService = recoveryService;
        this.operatorService = operatorService;
        this.operatorRepo = operatorRepo;
        this.counterRepo = counterRepo;
        this.publishedRepo = publishedRepo;
        this.ledgerService = ledgerService;
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
