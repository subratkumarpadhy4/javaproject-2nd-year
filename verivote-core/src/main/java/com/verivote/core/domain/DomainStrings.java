package com.verivote.core.domain;

/**
 * Frozen domain-separation string constants and type-byte constants.
 *
 * <p>This class is the single source of truth for every prefix, domain byte,
 * and type byte used in hash preimages and signature preimages.
 * See {@code docs/DOMAIN.md} for the full encoding specification.</p>
 *
 * <p>This class has no methods and throws no exceptions. It is fully
 * implemented in Part 1 and must not be modified in later parts.</p>
 */
public final class DomainStrings {

    private DomainStrings() {}

    // ── Signature / hash preimage prefixes ─────────────────────────────────

    /** Prefix for token-issuance blind-signature preimages. */
    public static final String TOKEN_PREFIX    = "VERIVOTE-TOKEN-v1";

    /** Prefix for spent-token hash preimages. */
    public static final String SPEND_PREFIX    = "VERIVOTE-SPEND-v1";

    /** Prefix for vote-receipt hash preimages. */
    public static final String RECEIPT_PREFIX  = "VERIVOTE-RECEIPT-v1";

    /** Prefix for block hash preimages. */
    public static final String BLOCK_PREFIX    = "VERIVOTE-BLOCK-v1";

    /** Prefix for ORPHAN Merkle-leaf preimages. */
    public static final String ORPHAN_PREFIX   = "VERIVOTE-ORPHAN-v1";

    /** Prefix for checkpoint signature preimages. */
    public static final String CKPT_PREFIX     = "VERIVOTE-CKPT-v1";

    /** Prefix for operator recovery-action signature preimages. */
    public static final String RECOVERY_PREFIX = "VERIVOTE-RECOVERY-v1";

    /** Prefix for published-issuance-counter signature preimages. */
    public static final String ISSUANCE_PREFIX = "VERIVOTE-ISSUANCE-v1";

    // ── Merkle domain-separation bytes (RFC 6962) ───────────────────────────

    /** Domain byte prepended to Merkle leaf preimages: {@code 0x00}. */
    public static final byte LEAF_DOMAIN       = 0x00;

    /** Domain byte prepended to Merkle internal-node preimages: {@code 0x01}. */
    public static final byte NODE_DOMAIN       = 0x01;

    /** Domain byte used for the empty-tree hash: {@code 0x02}. */
    public static final byte EMPTY_ROOT_DOMAIN = 0x02;

    // ── Block type bytes ────────────────────────────────────────────────────

    /** Type byte for a VOTE block in block-hash preimages. */
    public static final byte TYPE_BYTE_VOTE    = 1;

    /** Type byte for an ORPHAN block in block-hash preimages. */
    public static final byte TYPE_BYTE_ORPHAN  = 2;

    // ── Scheme claim string ─────────────────────────────────────────────────

    /**
     * Human-readable scheme identifier.
     * Must NOT claim RFC 9474 compliance.
     */
    public static final String CLAIM_STRING    = "blind RSA-PSS (RFC 9474 family)";
}
