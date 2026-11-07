package com.verivote.core.ledger;

import com.verivote.core.crypto.Sha256;
import com.verivote.core.encoding.Hex;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and repairs the append-only ledger file.
 */
public final class LedgerFileParser {

    private static final Pattern JSON_FIELD_PATTERN =
            Pattern.compile("\"([^\"]+)\"\\s*:\\s*(\"([^\"]*)\"|null|-?\\d+)");

    private LedgerFileParser() {}

    /**
     * Result of a full ledger parse.
     *
     * @param blocks      list of successfully parsed blocks (may be empty)
     * @param fatal       non-null error message if the file is unrecoverable, otherwise null
     * @param hasTornTail true if a torn tail was encountered and repaired
     */
    public record ParseResult(List<Block> blocks, String fatal, boolean hasTornTail) {
        public ParseResult(List<Block> blocks, String fatal) {
            this(blocks, fatal, false);
        }

        public boolean isFatal() {
            return fatal != null;
        }
    }

    /**
     * Result of a torn-write repair attempt.
     *
     * @param lastValid      last block that parsed without error
     * @param truncatedBytes number of bytes truncated from the end of the file
     */
    public record TornRepair(Block lastValid, long truncatedBytes) {}

    /**
     * Parses the ledger file at {@code file} and returns all valid blocks.
     */
    public static ParseResult parse(Path file) {
        if (!Files.exists(file)) {
            return new ParseResult(Collections.emptyList(), null, false);
        }

        byte[] data;
        try {
            data = Files.readAllBytes(file);
        } catch (IOException e) {
            return new ParseResult(Collections.emptyList(), "IO error reading file: " + e.getMessage(), false);
        }

        if (data.length == 0) {
            return new ParseResult(Collections.emptyList(), null, false);
        }

        List<String> completeLines = new ArrayList<>();
        int start = 0;
        int lastNewline = -1;
        for (int i = 0; i < data.length; i++) {
            if (data[i] == '\n') {
                int end = i;
                if (end > start && data[end - 1] == '\r') {
                    end--;
                }
                completeLines.add(new String(data, start, end - start, StandardCharsets.UTF_8));
                start = i + 1;
                lastNewline = i;
            }
        }

        List<Block> blocks = new ArrayList<>();

        // Parse all newline-terminated lines
        for (int lineIdx = 0; lineIdx < completeLines.size(); lineIdx++) {
            String line = completeLines.get(lineIdx).trim();
            if (line.isEmpty()) {
                continue;
            }

            Block block;
            try {
                block = parseBlockJson(line);
            } catch (Exception e) {
                return new ParseResult(blocks, "FATAL_MALFORMED_JSON line " + (lineIdx + 1) + ": " + e.getMessage(), false);
            }

            String validationError = validateBlockAndChain(block, blocks);
            if (validationError != null) {
                return new ParseResult(blocks, validationError, false);
            }

            blocks.add(block);
        }

        // Check if there is trailing content without newline
        if (start < data.length) {
            byte[] trailingBytes = Arrays.copyOfRange(data, start, data.length);
            String trailingStr = new String(trailingBytes, StandardCharsets.UTF_8).trim();

            Block trailingBlock = null;
            boolean trailingValid = false;

            if (!trailingStr.isEmpty()) {
                try {
                    trailingBlock = parseBlockJson(trailingStr);
                    if (validateBlockAndChain(trailingBlock, blocks) == null) {
                        trailingValid = true;
                    }
                } catch (Exception ignored) {
                    // trailing segment failed parse/validation
                }
            }

            if (trailingValid) {
                blocks.add(trailingBlock);
                return new ParseResult(blocks, null, false);
            } else {
                // Torn tail!
                TornRepair repair = executeTornRepair(file, blocks.isEmpty() ? null : blocks.get(blocks.size() - 1), trailingBytes, lastNewline + 1);
                return new ParseResult(blocks, null, true);
            }
        }

        return new ParseResult(blocks, null, false);
    }

    /**
     * Attempts to repair a torn (partially written) ledger file at {@code file}.
     */
    public static TornRepair repairTorn(Path file) {
        if (!Files.exists(file)) {
            return new TornRepair(null, 0L);
        }

        byte[] data;
        try {
            data = Files.readAllBytes(file);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read file for torn repair", e);
        }

        if (data.length == 0) {
            return new TornRepair(null, 0L);
        }

        int start = 0;
        int lastNewline = -1;
        List<Block> blocks = new ArrayList<>();

        for (int i = 0; i < data.length; i++) {
            if (data[i] == '\n') {
                int end = i;
                if (end > start && data[end - 1] == '\r') {
                    end--;
                }
                String line = new String(data, start, end - start, StandardCharsets.UTF_8).trim();
                if (!line.isEmpty()) {
                    try {
                        Block b = parseBlockJson(line);
                        blocks.add(b);
                    } catch (Exception ignored) {}
                }
                start = i + 1;
                lastNewline = i;
            }
        }

        Block lastValid = blocks.isEmpty() ? null : blocks.get(blocks.size() - 1);

        if (start < data.length) {
            byte[] trailingBytes = Arrays.copyOfRange(data, start, data.length);
            String trailingStr = new String(trailingBytes, StandardCharsets.UTF_8).trim();

            boolean trailingValid = false;
            if (!trailingStr.isEmpty()) {
                try {
                    Block tb = parseBlockJson(trailingStr);
                    if (validateBlockAndChain(tb, blocks) == null) {
                        trailingValid = true;
                    }
                } catch (Exception ignored) {}
            }

            if (!trailingValid) {
                return executeTornRepair(file, lastValid, trailingBytes, lastNewline + 1);
            }
        }

        return new TornRepair(lastValid, 0L);
    }

    private static TornRepair executeTornRepair(Path file, Block lastValid, byte[] tornBytes, int truncateTo) {
        Path tornFile = file.resolveSibling(file.getFileName().toString() + ".torn");
        try {
            Files.write(tornFile, tornBytes, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            try (var channel = Files.newByteChannel(file, StandardOpenOption.WRITE)) {
                channel.truncate(truncateTo);
            }
            Path repairsLog = file.resolveSibling("repairs.log");
            String logMsg = String.format("[WARN] Torn tail repaired at %s: truncated %d bytes, moved to %s%n",
                    file, tornBytes.length, tornFile.getFileName());
            Files.writeString(repairsLog, logMsg, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IllegalStateException("Failed during torn repair", e);
        }
        return new TornRepair(lastValid, tornBytes.length);
    }

    private static String validateBlockAndChain(Block block, List<Block> priorBlocks) {
        // 1. Verify block hash
        String computedHash = Hex.encode(BlockHash.compute(block));
        if (!computedHash.equalsIgnoreCase(block.blockHash())) {
            return "CHAIN_HASH_MISMATCH at height " + block.height() + ": computed " + computedHash + " != stored " + block.blockHash();
        }

        // 2. Chain link continuity
        if (block.height() == 0) {
            if (!Block.ZERO.equalsIgnoreCase(block.prevHash())) {
                return "GENESIS_PREV_HASH_NON_ZERO: height 0 must have prevHash ZERO, got " + block.prevHash();
            }
        } else {
            if (priorBlocks.isEmpty()) {
                return "CHAIN_START_GAP: first block has non-zero height " + block.height();
            }
            Block prev = priorBlocks.get(priorBlocks.size() - 1);
            if (block.height() != prev.height() + 1) {
                return "HEIGHT_GAP: expected " + (prev.height() + 1) + " but got " + block.height();
            }
            if (!prev.blockHash().equalsIgnoreCase(block.prevHash())) {
                return "CHAIN_LINK_BROKEN at height " + block.height() + ": prevHash " + block.prevHash() + " != previous blockHash " + prev.blockHash();
            }
        }
        return null;
    }

    public static Block parseBlockJson(String json) {
        String trimmed = json.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            throw new IllegalArgumentException("Not a JSON object: " + json);
        }

        Map<String, String> map = new HashMap<>();
        Matcher m = JSON_FIELD_PATTERN.matcher(trimmed);
        while (m.find()) {
            String key = m.group(1);
            String strVal = m.group(3);
            if (strVal != null) {
                map.put(key, strVal);
            } else {
                String raw = m.group(2);
                map.put(key, "null".equals(raw) ? null : raw);
            }
        }

        long height = Long.parseLong(requireField(map, "height"));
        String prevHash = requireField(map, "prevHash");
        String type = requireField(map, "type");
        long refHeight = Long.parseLong(requireField(map, "refHeight"));
        String voteHash = requireField(map, "voteHash");
        String tokenHash = requireField(map, "tokenHash");
        String receiptHash = requireField(map, "receiptHash");
        String ciphertextB64 = map.get("ciphertextB64");
        long timestampSec = Long.parseLong(requireField(map, "timestampSec"));
        String merkleRoot = requireField(map, "merkleRoot");
        String blockHash = requireField(map, "blockHash");

        return new Block(
                height,
                prevHash,
                type,
                refHeight,
                voteHash,
                tokenHash,
                receiptHash,
                ciphertextB64,
                timestampSec,
                merkleRoot,
                blockHash
        );
    }

    public static String toJson(Block b) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"height\":").append(b.height()).append(",");
        sb.append("\"prevHash\":\"").append(b.prevHash()).append("\",");
        sb.append("\"type\":\"").append(b.type()).append("\",");
        sb.append("\"refHeight\":").append(b.refHeight()).append(",");
        sb.append("\"voteHash\":\"").append(b.voteHash()).append("\",");
        sb.append("\"tokenHash\":\"").append(b.tokenHash()).append("\",");
        sb.append("\"receiptHash\":\"").append(b.receiptHash()).append("\",");
        if (b.ciphertextB64() == null) {
            sb.append("\"ciphertextB64\":null,");
        } else {
            sb.append("\"ciphertextB64\":\"").append(b.ciphertextB64()).append("\",");
        }
        sb.append("\"timestampSec\":").append(b.timestampSec()).append(",");
        sb.append("\"merkleRoot\":\"").append(b.merkleRoot()).append("\",");
        sb.append("\"blockHash\":\"").append(b.blockHash()).append("\"");
        sb.append("}");
        return sb.toString();
    }

    private static String requireField(Map<String, String> map, String key) {
        String val = map.get(key);
        if (val == null) {
            throw new IllegalArgumentException("Missing required JSON field: " + key);
        }
        return val;
    }
}
