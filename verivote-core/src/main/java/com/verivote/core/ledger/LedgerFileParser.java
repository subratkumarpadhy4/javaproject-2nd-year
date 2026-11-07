package com.verivote.core.ledger;

import java.nio.file.Path;
import java.util.List;

/**
 * Parses and repairs the append-only ledger file.
 * Implementation deferred to Part 7.
 */
public final class LedgerFileParser {

    private LedgerFileParser() {}

    /**
     * Result of a full ledger parse.
     *
     * @param blocks list of successfully parsed blocks (may be empty)
     * @param fatal  non-null error message if the file is unrecoverable, otherwise null
     */
    public record ParseResult(List<Block> blocks, String fatal) {}

    /**
     * Result of a torn-write repair attempt.
     *
     * @param lastValid      last block that parsed without error
     * @param truncatedBytes number of bytes truncated from the end of the file
     */
    public record TornRepair(Block lastValid, long truncatedBytes) {}

    /** Parses the ledger file at {@code file} and returns all valid blocks. */
    public static ParseResult parse(Path file) {
        throw new UnsupportedOperationException("Part 7");
    }

    /** Attempts to repair a torn (partially written) ledger file at {@code file}. */
    public static TornRepair repairTorn(Path file) {
        throw new UnsupportedOperationException("Part 7");
    }
}
