package com.verivote.service.tally;

import com.verivote.core.crypto.Shamir;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.PrivateKey;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TallyKeyServiceTest {

    private TallyKeyService tallyKeyService;

    @BeforeEach
    void setUp() {
        tallyKeyService = new TallyKeyService();
    }

    @Test
    void twoDifferentSharesReconstructToSamePrivateKey() {
        List<Shamir.Share> shares = tallyKeyService.getShares();
        assertEquals(3, shares.size());

        List<Shamir.Share> subset1 = List.of(shares.get(0), shares.get(1));
        List<Shamir.Share> subset2 = List.of(shares.get(1), shares.get(2));

        PrivateKey priv1 = tallyKeyService.reconstructAndGetPrivateKey(subset1);
        PrivateKey priv2 = tallyKeyService.reconstructAndGetPrivateKey(subset2);

        assertNotNull(priv1);
        assertNotNull(priv2);
        assertArrayEquals(priv1.getEncoded(), priv2.getEncoded());
    }

    @Test
    void anyTwoOfThreeSubsetWorks() {
        List<Shamir.Share> shares = tallyKeyService.getShares();

        PrivateKey p01 = tallyKeyService.reconstructAndGetPrivateKey(List.of(shares.get(0), shares.get(1)));
        PrivateKey p02 = tallyKeyService.reconstructAndGetPrivateKey(List.of(shares.get(0), shares.get(2)));
        PrivateKey p12 = tallyKeyService.reconstructAndGetPrivateKey(List.of(shares.get(1), shares.get(2)));

        assertArrayEquals(p01.getEncoded(), p02.getEncoded());
        assertArrayEquals(p02.getEncoded(), p12.getEncoded());
    }

    @Test
    void oneShareReconstructFails() {
        List<Shamir.Share> shares = tallyKeyService.getShares();
        List<Shamir.Share> oneShare = List.of(shares.get(0));

        assertThrows(RuntimeException.class, () -> tallyKeyService.reconstructAndGetPrivateKey(oneShare));
    }

    @Test
    void publicKeySpkiIsNonEmpty() {
        byte[] spki = tallyKeyService.getPublicKeySpki();
        assertNotNull(spki);
        assertTrue(spki.length > 0);
        assertNotNull(tallyKeyService.getPublicKey());
    }
}
