package com.verivote.client.http;

import com.verivote.core.encoding.Base64Codec;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

public class ApiClient {

    private final HttpClient http;
    private final String baseUrl;

    public record RegisterResponse(String totpSecretBase32, String otpauthUri) {}
    public record LoginResponse(String challengeToken) {}
    public record TotpResponse(String sessionToken) {}
    public record PubkeyResponse(String publicKeySpki) {}
    public record TokenIssuanceResponse(String signature, long issued) {}
    public record VoteResponse(String receiptHash, long blockHeight, String blockHash) {}
    public record VerifyResponse(String status, Long index, Long treeSize, List<String> proof, String leafData) {}
    public record CheckpointResponse(long treeSize, String merkleRoot, String headHash, String signature, long createdAtSec) {}

    public ApiClient(String baseUrl) {
        this(baseUrl, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    public ApiClient(String baseUrl, HttpClient http) {
        if (baseUrl == null || baseUrl.isBlank()) {
            this.baseUrl = "http://localhost:8080";
        } else {
            this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        }
        this.http = http;
    }

    public RegisterResponse register(String nationalId, String displayName, String password) throws ApiException {
        String json = "{\"nationalId\":" + escapeJson(nationalId) +
                ",\"displayName\":" + escapeJson(displayName) +
                ",\"password\":" + escapeJson(password) + "}";
        HttpResponse<String> resp = sendPost("/api/register", json, null);
        Map<String, Object> map = parseObject(resp.body());
        return new RegisterResponse(
                (String) map.get("totpSecretBase32"),
                (String) map.get("otpauthUri")
        );
    }

    public LoginResponse login(String nationalId, String password) throws ApiException {
        String json = "{\"nationalId\":" + escapeJson(nationalId) +
                ",\"password\":" + escapeJson(password) + "}";
        HttpResponse<String> resp = sendPost("/api/login", json, null);
        Map<String, Object> map = parseObject(resp.body());
        return new LoginResponse((String) map.get("challengeToken"));
    }

    public TotpResponse verifyTotp(String challengeToken, int totpCode) throws ApiException {
        String json = "{\"challengeToken\":" + escapeJson(challengeToken) +
                ",\"totpCode\":" + totpCode + "}";
        HttpResponse<String> resp = sendPost("/api/login/totp", json, null);
        Map<String, Object> map = parseObject(resp.body());
        return new TotpResponse((String) map.get("sessionToken"));
    }

    public PubkeyResponse getTallyPubkey() throws ApiException {
        HttpResponse<String> resp = sendGet("/api/token/pubkey", null);
        Map<String, Object> map = parseObject(resp.body());
        String spki = (String) map.get("publicKeySpki");
        if (spki == null) {
            spki = (String) map.get("pubkey");
        }
        return new PubkeyResponse(spki);
    }

    public TokenIssuanceResponse requestToken(String sessionToken, byte[] blindedMessage) throws ApiException {
        String b64;
        try {
            // If already base64 ASCII, test decoding, otherwise encode
            Base64Codec.decode(new String(blindedMessage));
            b64 = new String(blindedMessage);
        } catch (Exception e) {
            b64 = Base64Codec.encode(blindedMessage);
        }
        String json = "{\"blindedMessage\":" + escapeJson(b64) + "}";
        HttpResponse<String> resp = sendPost("/api/token/request", json, "Bearer " + sessionToken);
        Map<String, Object> map = parseObject(resp.body());
        long issued = map.get("issued") instanceof Number n ? n.longValue() : 0L;
        return new TokenIssuanceResponse(
                (String) map.get("signature"),
                issued
        );
    }

    public VoteResponse castVote(byte[] ciphertext, byte[] tokenPlain, byte[] signature, byte[] clientNonce)
            throws ApiException {
        String json = "{" +
                "\"ciphertext\":" + escapeJson(Base64Codec.encode(ciphertext)) + "," +
                "\"tokenPlain\":" + escapeJson(Base64Codec.encode(tokenPlain)) + "," +
                "\"signature\":" + escapeJson(Base64Codec.encode(signature)) + "," +
                "\"clientNonce\":" + escapeJson(Base64Codec.encode(clientNonce)) +
                "}";
        HttpResponse<String> resp = sendPost("/api/vote", json, null);
        Map<String, Object> map = parseObject(resp.body());
        long height = map.get("blockHeight") instanceof Number n ? n.longValue() : 0L;
        return new VoteResponse(
                (String) map.get("receiptHash"),
                height,
                (String) map.get("blockHash")
        );
    }

    public VerifyResponse verifyReceipt(String receiptHash) throws ApiException {
        HttpResponse<String> resp = sendGet("/bb/verify/" + receiptHash, null);
        Map<String, Object> map = parseObject(resp.body());
        Long index = map.get("index") instanceof Number n ? n.longValue() : null;
        Long treeSize = map.get("treeSize") instanceof Number n ? n.longValue() : null;
        List<String> proof = new ArrayList<>();
        if (map.get("proof") instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) proof.add(o.toString());
            }
        }
        return new VerifyResponse(
                (String) map.get("status"),
                index,
                treeSize,
                proof,
                (String) map.get("leafData")
        );
    }

    @SuppressWarnings("unchecked")
    public List<CheckpointResponse> getCheckpoints() throws ApiException {
        HttpResponse<String> resp = sendGet("/bb/checkpoints", null);
        Object parsed = parse(resp.body().trim());
        List<CheckpointResponse> result = new ArrayList<>();
        if (parsed instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Map<String, Object> map = (Map<String, Object>) m;
                    long treeSize = map.get("treeSize") instanceof Number n ? n.longValue() : 0L;
                    long createdAt = map.get("createdAtSec") instanceof Number n ? n.longValue() : 0L;
                    result.add(new CheckpointResponse(
                            treeSize,
                            (String) map.get("merkleRoot"),
                            (String) map.get("headHash"),
                            (String) map.get("signature"),
                            createdAt
                    ));
                }
            }
        }
        return result;
    }

    // --- HTTP helpers ---

    private HttpResponse<String> sendPost(String path, String jsonBody, String authHeader) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
        if (authHeader != null) {
            builder.header("Authorization", authHeader);
        }
        return sendRequest(builder.build());
    }

    private HttpResponse<String> sendGet(String path, String authHeader) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Accept", "application/json")
                .GET();
        if (authHeader != null) {
            builder.header("Authorization", authHeader);
        }
        return sendRequest(builder.build());
    }

    private HttpResponse<String> sendRequest(HttpRequest req) {
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            int code = resp.statusCode();
            if (code >= 400) {
                throw new ApiException(code, "Server returned HTTP " + code, resp.body());
            }
            return resp;
        } catch (ApiException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            throw new ApiException(0, "Network error: " + e.getMessage(), e);
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
                    if (c < ' ') {
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

    // --- Minimal JSON Parser ---

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseObject(String json) {
        Object val = parse(json != null ? json.trim() : "");
        if (val instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Collections.emptyMap();
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
            if (c == '{') return parseObj();
            if (c == '[') return parseArr();
            if (c == '"') return parseStr();
            if (c == 't' || c == 'f') return parseBool();
            if (c == 'n') return parseNull();
            return parseNum();
        }

        private Map<String, Object> parseObj() {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++; // skip '{'
            skipWhitespace();
            if (peek('}')) {
                pos++;
                return map;
            }
            while (pos < src.length()) {
                skipWhitespace();
                String key = parseStr();
                skipWhitespace();
                if (peek(':')) pos++;
                Object val = parseValue();
                map.put(key, val);
                skipWhitespace();
                if (peek(',')) {
                    pos++;
                } else if (peek('}')) {
                    pos++;
                    break;
                }
            }
            return map;
        }

        private List<Object> parseArr() {
            List<Object> list = new ArrayList<>();
            pos++; // skip '['
            skipWhitespace();
            if (peek(']')) {
                pos++;
                return list;
            }
            while (pos < src.length()) {
                skipWhitespace();
                list.add(parseValue());
                skipWhitespace();
                if (peek(',')) {
                    pos++;
                } else if (peek(']')) {
                    pos++;
                    break;
                }
            }
            return list;
        }

        private String parseStr() {
            skipWhitespace();
            if (!peek('"')) return "";
            pos++; // skip opening '"'
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos++);
                if (c == '"') break;
                if (c == '\\' && pos < src.length()) {
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
                            if (pos + 4 <= src.length()) {
                                String hex = src.substring(pos, pos + 4);
                                sb.append((char) Integer.parseInt(hex, 16));
                                pos += 4;
                            }
                        }
                        default -> sb.append(esc);
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
