package com.verivote.client.admin;

import com.verivote.client.http.ApiException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

public class AdminApiClient {

    public record AuditFinding(String severity, String code, String message,
                               Long blockHeight, Long treeSize,
                               String remediation) {}

    public record AuditResponse(boolean clean, long auditedAtSec,
                                long ledgerBlockCount, long ledgerRealVoteCount,
                                long ledgerOrphanCount, long dbVoteCount,
                                List<AuditFinding> findings) {}

    public record RecoveryRequest(long blockHeight, String action, String reason,
                                  String blockHash, String tokenHash,
                                  String operator1, String signature1,
                                  String operator2, String signature2) {}

    public record RecoveryResponse(long eventId, long blockHeight, String action) {}

    public record TallyRequest(List<String> shareBase64List) {}

    public record TallyResponse(Map<Long, Long> counts, long invalidCount,
                                long closeBlockHeight, String closeHeadHash,
                                String chainMerkleRoot, long publishedAtSec) {}

    public record LedgerStatusResponse(long blockCount, long realVoteCount,
                                       long orphanCount, String headHash) {}

    private final HttpClient http;
    private final String baseUrl;

    public AdminApiClient(String baseUrl) {
        this(baseUrl, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    public AdminApiClient(String baseUrl, HttpClient http) {
        if (baseUrl == null || baseUrl.isBlank()) {
            this.baseUrl = "http://localhost:8080";
        } else {
            this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        }
        this.http = http;
    }

    public AuditResponse getAudit() throws ApiException {
        HttpResponse<String> resp = sendGet("/api/admin/audit");
        Map<String, Object> map = parseObject(resp.body());
        boolean clean = Boolean.TRUE.equals(map.get("clean"));
        long auditedAtSec = asLong(map.get("auditedAtSec"));
        long ledgerBlockCount = asLong(map.get("ledgerBlockCount"));
        long ledgerRealVoteCount = asLong(map.get("ledgerRealVoteCount"));
        long ledgerOrphanCount = asLong(map.get("ledgerOrphanCount"));
        long dbVoteCount = asLong(map.get("dbVoteCount"));

        List<AuditFinding> findings = new ArrayList<>();
        Object findingsObj = map.get("findings");
        if (findingsObj instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> fm) {
                    findings.add(new AuditFinding(
                            (String) fm.get("severity"),
                            (String) fm.get("code"),
                            (String) fm.get("message"),
                            fm.get("blockHeight") != null ? asLong(fm.get("blockHeight")) : null,
                            fm.get("treeSize") != null ? asLong(fm.get("treeSize")) : null,
                            (String) fm.get("remediation")
                    ));
                }
            }
        }
        return new AuditResponse(clean, auditedAtSec, ledgerBlockCount, ledgerRealVoteCount, ledgerOrphanCount, dbVoteCount, findings);
    }

    public LedgerStatusResponse getLedgerStatus() throws ApiException {
        HttpResponse<String> resp = sendGet("/api/admin/ledger-status");
        Map<String, Object> map = parseObject(resp.body());
        return new LedgerStatusResponse(
                asLong(map.get("blockCount")),
                asLong(map.get("realVoteCount")),
                asLong(map.get("orphanCount")),
                (String) map.get("headHash")
        );
    }

    public RecoveryResponse recover(RecoveryRequest req) throws ApiException {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"blockHeight\":").append(req.blockHeight()).append(",");
        sb.append("\"action\":").append(escapeJson(req.action())).append(",");
        sb.append("\"reason\":").append(escapeJson(req.reason())).append(",");
        sb.append("\"blockHash\":").append(escapeJson(req.blockHash())).append(",");
        sb.append("\"tokenHash\":").append(escapeJson(req.tokenHash())).append(",");
        sb.append("\"operator1\":").append(escapeJson(req.operator1())).append(",");
        sb.append("\"signature1\":").append(escapeJson(req.signature1())).append(",");
        sb.append("\"operator2\":").append(escapeJson(req.operator2())).append(",");
        sb.append("\"signature2\":").append(escapeJson(req.signature2()));
        sb.append("}");

        HttpResponse<String> resp = sendPost("/api/admin/recover", sb.toString());
        Map<String, Object> map = parseObject(resp.body());
        return new RecoveryResponse(
                asLong(map.get("eventId")),
                asLong(map.get("blockHeight")),
                (String) map.get("action")
        );
    }

    public TallyResponse tally(List<String> shareBase64List) throws ApiException {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < shareBase64List.size(); i++) {
            if (i > 0) sb.append(",");
            String s = shareBase64List.get(i);
            int idx = i + 1;
            String val = s;
            if (s.contains(":")) {
                int colon = s.indexOf(':');
                try {
                    idx = Integer.parseInt(s.substring(0, colon).trim());
                    val = s.substring(colon + 1).trim();
                } catch (NumberFormatException ignored) {}
            }
            sb.append("{\"index\":").append(idx)
              .append(",\"value\":").append(escapeJson(val)).append("}");
        }
        sb.append("]");

        HttpResponse<String> resp = sendPost("/api/admin/tally?electionId=1", sb.toString());
        Map<String, Object> map = parseObject(resp.body());

        Map<Long, Long> counts = new HashMap<>();
        Object countsObj = map.get("counts");
        if (countsObj instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                long k = Long.parseLong(e.getKey().toString());
                long v = asLong(e.getValue());
                counts.put(k, v);
            }
        }

        long pubSec = 0;
        if (map.containsKey("tallyPublishedAtSec")) {
            pubSec = asLong(map.get("tallyPublishedAtSec"));
        } else if (map.containsKey("publishedAtSec")) {
            pubSec = asLong(map.get("publishedAtSec"));
        }

        return new TallyResponse(
                counts,
                asLong(map.get("invalidCount")),
                asLong(map.get("closeBlockHeight")),
                (String) map.get("closeHeadHash"),
                (String) map.get("chainMerkleRoot"),
                pubSec
        );
    }

    public List<Map<String, Object>> listOperators() throws ApiException {
        HttpResponse<String> resp = sendGet("/api/admin/operators");
        Object obj = parse(resp.body());
        if (obj instanceof List<?> l) {
            List<Map<String, Object>> res = new ArrayList<>();
            for (Object item : l) {
                if (item instanceof Map<?, ?> m) {
                    Map<String, Object> casted = new HashMap<>();
                    m.forEach((k, v) -> casted.put(String.valueOf(k), v));
                    res.add(casted);
                }
            }
            return res;
        }
        return Collections.emptyList();
    }

    public void enrollOperator(String name, String ed25519Pub, String role) throws ApiException {
        String json = "{\"name\":" + escapeJson(name) +
                ",\"ed25519Pub\":" + escapeJson(ed25519Pub) +
                ",\"role\":" + escapeJson(role) +
                ",\"createdBy\":\"admin\"}";
        sendPost("/api/admin/operators", json);
    }

    private HttpResponse<String> sendGet(String path) throws ApiException {
        try {
            HttpRequest.Builder req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .GET();
            HttpResponse<String> resp = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new ApiException(resp.statusCode(), resp.body());
            }
            return resp;
        } catch (IOException | InterruptedException e) {
            throw new ApiException(0, "Network error: " + e.getMessage());
        }
    }

    private HttpResponse<String> sendPost(String path, String jsonBody) throws ApiException {
        try {
            HttpRequest.Builder req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
            HttpResponse<String> resp = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new ApiException(resp.statusCode(), resp.body());
            }
            return resp;
        } catch (IOException | InterruptedException e) {
            throw new ApiException(0, "Network error: " + e.getMessage());
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
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
        sb.append("\"");
        return sb.toString();
    }

    private static long asLong(Object obj) {
        if (obj == null) return 0L;
        if (obj instanceof Number n) return n.longValue();
        return Long.parseLong(obj.toString());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseObject(String json) throws ApiException {
        Object o = parse(json);
        if (o instanceof Map) {
            return (Map<String, Object>) o;
        }
        throw new ApiException(0, "Expected JSON object but got: " + json);
    }

    private static Object parse(String json) {
        if (json == null || json.isEmpty()) return null;
        return new JsonParser(json).parseValue();
    }

    private static class JsonParser {
        private final String src;
        private int pos = 0;

        JsonParser(String src) {
            this.src = src;
        }

        Object parseValue() {
            skipWhitespace();
            if (pos >= src.length()) return null;
            char c = src.charAt(pos);
            if (c == '{') return parseObject();
            if (c == '[') return parseArray();
            if (c == '"') return parseString();
            if (c == 't' || c == 'f') return parseBool();
            if (c == 'n') return parseNull();
            if (Character.isDigit(c) || c == '-') return parseNum();
            throw new IllegalArgumentException("Unexpected char at pos " + pos + ": " + c);
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++; // skip '{'
            skipWhitespace();
            if (peek('}')) {
                pos++;
                return map;
            }
            while (pos < src.length()) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                if (!peek(':')) throw new IllegalArgumentException("Expected ':' at pos " + pos);
                pos++; // skip ':'
                Object value = parseValue();
                map.put(key, value);
                skipWhitespace();
                if (peek('}')) {
                    pos++;
                    break;
                }
                if (!peek(',')) throw new IllegalArgumentException("Expected ',' or '}' at pos " + pos);
                pos++; // skip ','
            }
            return map;
        }

        private List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            pos++; // skip '['
            skipWhitespace();
            if (peek(']')) {
                pos++;
                return list;
            }
            while (pos < src.length()) {
                Object val = parseValue();
                list.add(val);
                skipWhitespace();
                if (peek(']')) {
                    pos++;
                    break;
                }
                if (!peek(',')) throw new IllegalArgumentException("Expected ',' or ']' at pos " + pos);
                pos++; // skip ','
            }
            return list;
        }

        private String parseString() {
            pos++; // skip opening '"'
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos++);
                if (c == '"') break;
                if (c == '\\') {
                    if (pos >= src.length()) break;
                    char esc = src.charAt(pos++);
                    switch (esc) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            String hex = src.substring(pos, pos + 4);
                            sb.append((char) Integer.parseInt(hex, 16));
                            pos += 4;
                        }
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        private Boolean parseBool() {
            if (src.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (src.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            return null;
        }

        private Object parseNull() {
            if (src.startsWith("null", pos)) {
                pos += 4;
            }
            return null;
        }

        private Number parseNum() {
            int start = pos;
            if (peek('-')) pos++;
            boolean decimal = false;
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (Character.isDigit(c)) {
                    pos++;
                } else if (c == '.' || c == 'e' || c == 'E' || c == '+') {
                    decimal = true;
                    pos++;
                } else {
                    break;
                }
            }
            String s = src.substring(start, pos);
            if (decimal) {
                return Double.parseDouble(s);
            }
            return Long.parseLong(s);
        }

        private boolean peek(char c) {
            return pos < src.length() && src.charAt(pos) == c;
        }

        private void skipWhitespace() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
        }
    }
}
