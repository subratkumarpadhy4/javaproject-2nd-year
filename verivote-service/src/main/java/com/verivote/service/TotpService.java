package com.verivote.service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * Implements RFC 6238 Time-based One-Time Password (TOTP).
 *
 * <p>NOTE: "HmacSHA1" is used strictly for compatibility with RFC 6238 standard TOTP
 * authenticators (e.g. Google Authenticator), and is used NOWHERE else in the VeriVote-J protocol.</p>
 */
public final class TotpService {

    private static final String HMAC_SHA1 = "HmacSHA1";
    private static final int TIME_STEP_SECONDS = 30;
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private TotpService() {}

    /**
     * Generates a 6-digit TOTP code for the current time.
     */
    public static int generateTotp(byte[] secret) {
        return generateTotp(secret, System.currentTimeMillis() / 1000L);
    }

    /**
     * Generates a 6-digit TOTP code for a specific unix timestamp (in seconds).
     */
    public static int generateTotp(byte[] secret, long unixTimeSec) {
        long t = unixTimeSec / TIME_STEP_SECONDS;
        return generateTotpForStep(secret, t);
    }

    private static int generateTotpForStep(byte[] secret, long step) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA1);
            mac.init(new SecretKeySpec(secret, "RAW"));
            ByteBuffer bb = ByteBuffer.allocate(8);
            bb.putLong(step);
            byte[] hash = mac.doFinal(bb.array());

            int offset = hash[19] & 0x0F;
            int truncated = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            return truncated % 1_000_000;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to calculate TOTP HMAC-SHA1", e);
        }
    }

    /**
     * Verifies a 6-digit code against secret within ±window steps.
     */
    public static boolean verify(byte[] secret, int code, int window) {
        long currentStep = (System.currentTimeMillis() / 1000L) / TIME_STEP_SECONDS;
        for (long step = currentStep - window; step <= currentStep + window; step++) {
            if (generateTotpForStep(secret, step) == code) {
                return true;
            }
        }
        return false;
    }

    /**
     * Encodes raw bytes to RFC 4648 Base32 string (without padding).
     */
    public static String base32Encode(byte[] data) {
        if (data == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                bitsLeft -= 5;
                sb.append(BASE32_ALPHABET.charAt((buffer >> bitsLeft) & 0x1F));
            }
        }
        if (bitsLeft > 0) {
            buffer <<= (5 - bitsLeft);
            sb.append(BASE32_ALPHABET.charAt(buffer & 0x1F));
        }
        return sb.toString();
    }

    /**
     * Decodes an RFC 4648 Base32 string back to raw bytes.
     */
    public static byte[] base32Decode(String s) {
        if (s == null) {
            return null;
        }
        String clean = s.toUpperCase().replaceAll("[= \\t\\r\\n]", "");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bitsLeft = 0;
        for (char c : clean.toCharArray()) {
            int val = BASE32_ALPHABET.indexOf(c);
            if (val < 0) {
                throw new IllegalArgumentException("Invalid Base32 character: " + c);
            }
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                bitsLeft -= 8;
                out.write((buffer >> bitsLeft) & 0xFF);
            }
        }
        return out.toByteArray();
    }
}
