package com.verivote.core.ledger;

/**
 * Immutable ledger block value type.
 */
public record Block(
        long height,
        String prevHash,
        String type,
        long refHeight,
        String voteHash,
        String tokenHash,
        String receiptHash,
        String ciphertextB64,
        long timestampSec,
        String merkleRoot,
        String blockHash
) {
    /** Type tag for a normal vote block. */
    public static final String VOTE = "VOTE";

    /** Type tag for an orphan (gap-fill) block. */
    public static final String ORPHAN = "ORPHAN";

    /** 64-character all-zero hex string used as the genesis prevHash sentinel. */
    public static final String ZERO = "0".repeat(64);

    /** Expected byte length of the RSA-OAEP ciphertext stored in ciphertextB64 (before encoding). */
    public static final int CT_LEN = 256;
}
