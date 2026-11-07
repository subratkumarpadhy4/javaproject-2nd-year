package com.verivote.core;

import com.verivote.core.domain.DomainStrings;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that every constant in {@link DomainStrings} matches its literal value
 * and that the class contains no stub methods.
 */
class DomainStringsTest {

    // ── String prefix constants ─────────────────────────────────────────────

    @Test
    void tokenPrefix() {
        assertEquals("VERIVOTE-TOKEN-v1", DomainStrings.TOKEN_PREFIX);
    }

    @Test
    void spendPrefix() {
        assertEquals("VERIVOTE-SPEND-v1", DomainStrings.SPEND_PREFIX);
    }

    @Test
    void receiptPrefix() {
        assertEquals("VERIVOTE-RECEIPT-v1", DomainStrings.RECEIPT_PREFIX);
    }

    @Test
    void blockPrefix() {
        assertEquals("VERIVOTE-BLOCK-v1", DomainStrings.BLOCK_PREFIX);
    }

    @Test
    void orphanPrefix() {
        assertEquals("VERIVOTE-ORPHAN-v1", DomainStrings.ORPHAN_PREFIX);
    }

    @Test
    void ckptPrefix() {
        assertEquals("VERIVOTE-CKPT-v1", DomainStrings.CKPT_PREFIX);
    }

    @Test
    void recoveryPrefix() {
        assertEquals("VERIVOTE-RECOVERY-v1", DomainStrings.RECOVERY_PREFIX);
    }

    @Test
    void issuancePrefix() {
        assertEquals("VERIVOTE-ISSUANCE-v1", DomainStrings.ISSUANCE_PREFIX);
    }

    // ── Merkle domain bytes ─────────────────────────────────────────────────

    @Test
    void leafDomain() {
        assertEquals((byte) 0x00, DomainStrings.LEAF_DOMAIN);
    }

    @Test
    void nodeDomain() {
        assertEquals((byte) 0x01, DomainStrings.NODE_DOMAIN);
    }

    @Test
    void emptyRootDomain() {
        assertEquals((byte) 0x02, DomainStrings.EMPTY_ROOT_DOMAIN);
    }

    // ── Block type bytes ────────────────────────────────────────────────────

    @Test
    void typeByteVote() {
        assertEquals((byte) 1, DomainStrings.TYPE_BYTE_VOTE);
    }

    @Test
    void typeByteOrphan() {
        assertEquals((byte) 2, DomainStrings.TYPE_BYTE_ORPHAN);
    }

    // ── Claim string ────────────────────────────────────────────────────────

    @Test
    void claimString() {
        assertEquals("blind RSA-PSS (RFC 9474 family)", DomainStrings.CLAIM_STRING);
    }

    @Test
    void claimStringDoesNotClaimImplementation() {
        String forbidden = new StringBuilder("implements").append(" ").append("RFC 9474").toString();
        assertFalse(
                DomainStrings.CLAIM_STRING.contains(forbidden),
                "CLAIM_STRING must not claim implementation"
        );
    }

    // ── Class structure guards ──────────────────────────────────────────────

    @Test
    void domainStringsHasNoMethodsThrowingUnsupportedOperationException() {
        for (Method m : DomainStrings.class.getDeclaredMethods()) {
            // If the method were a stub it would throw UnsupportedOperationException.
            // We verify no such public method exists by checking declared methods are
            // only private constructors / synthetic — i.e. there are none callable externally.
            // DomainStrings has only a private constructor; getDeclaredMethods() should be empty
            // (the constructor is returned by getDeclaredConstructors(), not getDeclaredMethods()).
            fail("DomainStrings must have no declared methods, but found: " + m.getName());
        }
    }
}
