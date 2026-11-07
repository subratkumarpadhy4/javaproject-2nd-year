package com.verivote.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that all stub classes exist on the classpath.
 * Does not invoke stub methods (to avoid UnsupportedOperationException at test time).
 */
class SkeletonCompilesTest {

    private Class<?> load(String fqcn) throws ClassNotFoundException {
        return Class.forName(fqcn);
    }

    // ── Encoding stubs ──────────────────────────────────────────────────────

    @Test
    void bigEndianExists() throws Exception {
        assertNotNull(load("com.verivote.core.encoding.BigEndian"));
    }

    @Test
    void hexExists() throws Exception {
        assertNotNull(load("com.verivote.core.encoding.Hex"));
    }

    @Test
    void base64CodecExists() throws Exception {
        assertNotNull(load("com.verivote.core.encoding.Base64Codec"));
    }

    // ── Crypto stubs ────────────────────────────────────────────────────────

    @Test
    void sha256Exists() throws Exception {
        assertNotNull(load("com.verivote.core.crypto.Sha256"));
    }

    @Test
    void hmacExists() throws Exception {
        assertNotNull(load("com.verivote.core.crypto.Hmac"));
    }

    @Test
    void merkleTreeExists() throws Exception {
        assertNotNull(load("com.verivote.core.crypto.MerkleTree"));
    }

    @Test
    void merkleVerifierExists() throws Exception {
        assertNotNull(load("com.verivote.core.crypto.MerkleVerifier"));
    }

    @Test
    void blindSignatureServiceExists() throws Exception {
        assertNotNull(load("com.verivote.core.crypto.BlindSignatureService"));
    }

    @Test
    void shamirExists() throws Exception {
        assertNotNull(load("com.verivote.core.crypto.Shamir"));
    }

    // ── Ledger stubs ────────────────────────────────────────────────────────

    @Test
    void blockExists() throws Exception {
        assertNotNull(load("com.verivote.core.ledger.Block"));
    }

    @Test
    void blockHashExists() throws Exception {
        assertNotNull(load("com.verivote.core.ledger.BlockHash"));
    }

    @Test
    void ledgerFileParserExists() throws Exception {
        assertNotNull(load("com.verivote.core.ledger.LedgerFileParser"));
    }

    // ── Domain ──────────────────────────────────────────────────────────────

    @Test
    void domainStringsExists() throws Exception {
        assertNotNull(load("com.verivote.core.domain.DomainStrings"));
    }

    // ── Method presence checks ──────────────────────────────────────────────

    @Test
    void bigEndianHasExpectedMethods() throws Exception {
        Class<?> c = load("com.verivote.core.encoding.BigEndian");
        assertNotNull(c.getMethod("u32be", long.class));
        assertNotNull(c.getMethod("u64be", long.class));
        assertNotNull(c.getMethod("int64be", long.class));
    }

    @Test
    void sha256HasExpectedMethods() throws Exception {
        Class<?> c = load("com.verivote.core.crypto.Sha256");
        assertNotNull(c.getMethod("hash", byte[].class));
        assertNotNull(c.getMethod("hash", byte[][].class));
    }

    @Test
    void blockHasExpectedConstants() throws Exception {
        Class<?> c = load("com.verivote.core.ledger.Block");
        assertEquals("VOTE",   c.getField("VOTE").get(null));
        assertEquals("ORPHAN", c.getField("ORPHAN").get(null));
        assertEquals("0".repeat(64), c.getField("ZERO").get(null));
        assertEquals(256,      c.getField("CT_LEN").get(null));
    }
}
