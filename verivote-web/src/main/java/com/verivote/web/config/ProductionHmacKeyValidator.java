package com.verivote.web.config;

import com.verivote.core.encoding.Hex;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Strict validator for production HMAC master key.
 * Active strictly under the 'prod' profile.
 * 
 * Guarantees:
 * 1. VERIVOTE_HMAC_MASTER_KEY is mandatory in production.
 * 2. Rejects missing, blank, whitespace, or null keys.
 * 3. Prohibits the public development/test key.
 * 4. Enforces at least 64 hexadecimal characters (256 bits of entropy).
 * 5. Rejects malformed or non-hexadecimal key strings.
 */
@Configuration
@Profile("prod")
public class ProductionHmacKeyValidator {

    private static final Logger log = LoggerFactory.getLogger(ProductionHmacKeyValidator.class);

    public static final String INSECURE_DEV_KEY = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    public static final int REQUIRED_HEX_LENGTH = 64; // 256 bits (32 bytes)

    @Value("${verivote.crypto.hmac-master-key:#{null}}")
    private String hmacKeyProp;

    @Value("${VERIVOTE_HMAC_MASTER_KEY:#{null}}")
    private String envKey;

    @PostConstruct
    public void validateOnStartup() {
        // Evaluate the configured key (either from direct env var or property)
        String candidateKey = null;
        if (envKey != null && !envKey.isBlank()) {
            candidateKey = envKey;
        } else if (hmacKeyProp != null && !hmacKeyProp.isBlank()) {
            candidateKey = hmacKeyProp;
        } else if (System.getenv("VERIVOTE_HMAC_MASTER_KEY") != null && !System.getenv("VERIVOTE_HMAC_MASTER_KEY").isBlank()) {
            candidateKey = System.getenv("VERIVOTE_HMAC_MASTER_KEY");
        }
        validateKey(candidateKey);
    }

    public static void validateKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalStateException(
                "CRITICAL PRODUCTION SECURITY ERROR: Environment variable VERIVOTE_HMAC_MASTER_KEY is missing or blank. " +
                "Production profile requires a dedicated 64-character hexadecimal master key (256 bits). " +
                "Application refuses to start to protect voter privacy and cryptographic integrity."
            );
        }

        String trimmed = key.trim();

        if (INSECURE_DEV_KEY.equalsIgnoreCase(trimmed)) {
            throw new IllegalStateException(
                "CRITICAL PRODUCTION SECURITY ERROR: Environment variable VERIVOTE_HMAC_MASTER_KEY matches the known " +
                "insecure public development/test key. Production refuses to start with development credentials."
            );
        }

        if (trimmed.length() < REQUIRED_HEX_LENGTH) {
            throw new IllegalStateException(
                "CRITICAL PRODUCTION SECURITY ERROR: Environment variable VERIVOTE_HMAC_MASTER_KEY is too short (length: " +
                trimmed.length() + ", required: at least 64 hex characters / 256 bits). Application refuses to start."
            );
        }

        try {
            byte[] decoded = Hex.decode(trimmed.toLowerCase());
            if (decoded.length < 32) {
                throw new IllegalStateException(
                    "CRITICAL PRODUCTION SECURITY ERROR: Decoded HMAC master key is less than 32 bytes (256 bits)."
                );
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                "CRITICAL PRODUCTION SECURITY ERROR: Environment variable VERIVOTE_HMAC_MASTER_KEY is malformed. " +
                "Must be a valid hexadecimal string (even length, hexadecimal characters [0-9a-fA-F]): " + e.getMessage(), e
            );
        }

        log.info("Production HMAC master key successfully validated (256-bit entropy verified).");
    }
}
