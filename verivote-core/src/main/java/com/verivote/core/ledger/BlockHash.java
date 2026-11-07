package com.verivote.core.ledger;

import com.verivote.core.crypto.Sha256;
import com.verivote.core.domain.DomainStrings;
import com.verivote.core.encoding.BigEndian;
import com.verivote.core.encoding.Hex;

import java.nio.charset.StandardCharsets;

/**
 * Computes the canonical block hash for a ledger {@link Block}.
 *
 * <p>Hash preimage (Architecture §5.2):
 * {@code
 *   SHA-256( "VERIVOTE-BLOCK-v1"
 *         || typeByte(1=VOTE, 2=ORPHAN)
 *         || u64be(height)
 *         || u64be(refHeight)
 *         || prevHash[32]
 *         || voteHash[32]
 *         || tokenHash[32]
 *         || receiptHash[32]
 *         || u64be(timestampSec)
 *         || merkleRoot[32] )
 * }</p>
 *
 * <p>{@code ciphertextB64} is deliberately NOT part of the preimage;
 * ciphertext integrity is enforced via {@code voteHash} at load time.</p>
 */
public final class BlockHash {

    private BlockHash() {}

    /**
     * Computes the SHA-256 block hash for the given block.
     *
     * @param b the block whose hash is to be computed
     * @return 32-byte hash
     * @throws IllegalArgumentException if {@code b} is null, if any hex field
     *                                  is malformed or the wrong length, or if
     *                                  {@code b.type()} is not VOTE or ORPHAN
     */
    public static byte[] compute(Block b) {
        if (b == null) {
            throw new IllegalArgumentException("Block cannot be null");
        }

        byte typeByte;
        if (Block.VOTE.equals(b.type())) {
            typeByte = DomainStrings.TYPE_BYTE_VOTE;
        } else if (Block.ORPHAN.equals(b.type())) {
            typeByte = DomainStrings.TYPE_BYTE_ORPHAN;
        } else {
            throw new IllegalArgumentException("Invalid block type: " + b.type());
        }

        byte[] prevHashBytes = decodeHashField(b.prevHash(), "prevHash");
        byte[] voteHashBytes = decodeHashField(b.voteHash(), "voteHash");
        byte[] tokenHashBytes = decodeHashField(b.tokenHash(), "tokenHash");
        byte[] receiptHashBytes = decodeHashField(b.receiptHash(), "receiptHash");
        byte[] merkleRootBytes = decodeHashField(b.merkleRoot(), "merkleRoot");

        return Sha256.hash(
                DomainStrings.BLOCK_PREFIX.getBytes(StandardCharsets.UTF_8),
                new byte[]{typeByte},
                BigEndian.u64be(b.height()),
                BigEndian.u64be(b.refHeight()),
                prevHashBytes,
                voteHashBytes,
                tokenHashBytes,
                receiptHashBytes,
                BigEndian.u64be(b.timestampSec()),
                merkleRootBytes
        );
    }

    private static byte[] decodeHashField(String hex, String fieldName) {
        if (hex == null) {
            throw new IllegalArgumentException(fieldName + " cannot be null");
        }
        byte[] bytes;
        try {
            bytes = Hex.decode(hex);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid " + fieldName + ": " + hex, e);
        }
        if (bytes.length != 32) {
            throw new IllegalArgumentException("Invalid " + fieldName + " length (expected 32 bytes, got " + bytes.length + "): " + hex);
        }
        return bytes;
    }
}
