package com.verivote.web.config;

import com.verivote.core.crypto.Hmac;
import com.verivote.core.encoding.Hex;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ProductionHmacKeyValidatorTest {

    private static final String VALID_TEST_KEY = "e4d909c290d0fb1ca068ffaddf22cbd0a1b2c3d4e5f60718293a4b5c6d7e8f90";

    @Test
    @DisplayName("Rejects null or missing key")
    void rejectsNullOrMissingKey() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                ProductionHmacKeyValidator.validateKey(null)
        );
        assertTrue(ex.getMessage().contains("missing or blank"));
    }

    @Test
    @DisplayName("Rejects blank or whitespace-only key")
    void rejectsBlankKey() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                ProductionHmacKeyValidator.validateKey("   \t\n  ")
        );
        assertTrue(ex.getMessage().contains("missing or blank"));
    }

    @Test
    @DisplayName("Rejects the known public development/test key")
    void rejectsPublicDevelopmentKey() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                ProductionHmacKeyValidator.validateKey(ProductionHmacKeyValidator.INSECURE_DEV_KEY)
        );
        assertTrue(ex.getMessage().contains("matches the known insecure public development/test key"));
    }

    @Test
    @DisplayName("Rejects keys shorter than 64 hex characters (256 bits)")
    void rejectsTooShortKey() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                ProductionHmacKeyValidator.validateKey("0123456789abcdef0123456789abcdef") // only 32 chars (128 bits)
        );
        assertTrue(ex.getMessage().contains("too short"));
    }

    @Test
    @DisplayName("Rejects malformed non-hexadecimal key")
    void rejectsMalformedNonHexKey() {
        String nonHexKey = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789zzzzzz"; // contains 'z'
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                ProductionHmacKeyValidator.validateKey(nonHexKey)
        );
        assertTrue(ex.getMessage().contains("malformed"));
    }

    @Test
    @DisplayName("Rejects odd-length key string")
    void rejectsOddLengthKey() {
        String oddLengthKey = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef1"; // 65 chars
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                ProductionHmacKeyValidator.validateKey(oddLengthKey)
        );
        assertTrue(ex.getMessage().contains("malformed"));
    }

    @Test
    @DisplayName("Accepts valid 64-character lowercase hexadecimal key")
    void acceptsValid64CharHexKey() {
        assertDoesNotThrow(() ->
                ProductionHmacKeyValidator.validateKey(VALID_TEST_KEY)
        );
    }

    @Test
    @DisplayName("Accepts valid 64-character uppercase hexadecimal key")
    void acceptsValid64CharUpperHexKey() {
        assertDoesNotThrow(() ->
                ProductionHmacKeyValidator.validateKey(VALID_TEST_KEY.toUpperCase())
        );
    }

    @Test
    @DisplayName("Spring Boot startup in prod profile refuses to start when HMAC key is missing")
    void startupFailsWhenHmacKeyMissingInProd() {
        SpringApplicationBuilder builder = new SpringApplicationBuilder(ProductionHmacKeyValidator.class)
                .profiles("prod")
                .properties(
                        "server.port=0",
                        "spring.main.web-application-type=none"
                );

        Exception ex = assertThrows(Exception.class, builder::run);
        assertTrue(findRootMessage(ex).contains("missing or blank"));
    }

    @Test
    @DisplayName("Spring Boot startup in prod profile refuses to start when dev key is used")
    void startupFailsWhenDevKeyUsedInProd() {
        SpringApplicationBuilder builder = new SpringApplicationBuilder(ProductionHmacKeyValidator.class)
                .profiles("prod")
                .properties(
                        "server.port=0",
                        "spring.main.web-application-type=none",
                        "VERIVOTE_HMAC_MASTER_KEY=" + ProductionHmacKeyValidator.INSECURE_DEV_KEY
                );

        Exception ex = assertThrows(Exception.class, builder::run);
        assertTrue(findRootMessage(ex).contains("matches the known insecure public development/test key"),
                "Expected rejection of development key, got: " + findRootMessage(ex));
    }

    @Test
    @DisplayName("Spring Boot startup in prod profile succeeds when valid production key is provided")
    void startupSucceedsWithValidKeyInProd() {
        SpringApplicationBuilder builder = new SpringApplicationBuilder(ProductionHmacKeyValidator.class)
                .profiles("prod")
                .properties(
                        "server.port=0",
                        "spring.main.web-application-type=none",
                        "VERIVOTE_HMAC_MASTER_KEY=" + VALID_TEST_KEY
                );

        assertDoesNotThrow(() -> {
            try (var ctx = builder.run()) {
                assertNotNull(ctx.getBean(ProductionHmacKeyValidator.class));
            }
        });
    }

    @Test
    @DisplayName("Preserves HMAC calculation and voter token compatibility when key is supplied")
    void preservesHmacCompatibility() {
        byte[] keyBytes = Hex.decode(VALID_TEST_KEY.toLowerCase());
        byte[] inputData = "voter-national-id-987654321".getBytes(StandardCharsets.UTF_8);

        byte[] hmac1 = Hmac.sha256(keyBytes, inputData);
        byte[] hmac2 = Hmac.sha256(keyBytes, inputData);

        assertArrayEquals(hmac1, hmac2);
        assertEquals(Hex.encode(hmac1), Hex.encode(hmac2));
        assertEquals(64, Hex.encode(hmac1).length());
    }

    private static String findRootMessage(Throwable t) {
        StringBuilder sb = new StringBuilder();
        Throwable curr = t;
        while (curr != null) {
            if (curr.getMessage() != null) {
                sb.append(curr.getMessage()).append(" ");
            }
            curr = curr.getCause();
        }
        return sb.toString();
    }
}
