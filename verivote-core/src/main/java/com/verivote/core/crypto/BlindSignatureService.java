package com.verivote.core.crypto;

import java.math.BigInteger;
import java.security.SecureRandom;

/**
 * Blind RSA-PSS signature service (RFC 9474 family).
 * Implementation deferred to Part 5.
 *
 * <p>Scheme: blind RSA-PSS (RFC 9474 family).
 * This class does NOT claim to implement RFC 9474 verbatim.</p>
 */
public final class BlindSignatureService {

    private BlindSignatureService() {}

    /**
     * An RSA key pair represented as raw DER bytes.
     *
     * @param publicKeySpki  X.509 SubjectPublicKeyInfo (DER)
     * @param privateKeyPkcs8 PKCS#8 PrivateKeyInfo (DER)
     */
    public record KeyPair(byte[] publicKeySpki, byte[] privateKeyPkcs8) {}

    /** Generates a new RSA key pair suitable for blind signing. */
    public static KeyPair generateKeyPair() {
        throw new UnsupportedOperationException("Part 5");
    }

    /**
     * Blinds {@code message} using the public key in {@code kp}.
     * The blinding factor is written into {@code outBlindingFactor[0]}.
     */
    public static byte[] blind(byte[] message, KeyPair kp, SecureRandom rnd,
                               BigInteger[] outBlindingFactor) {
        throw new UnsupportedOperationException("Part 5");
    }

    /** Signs {@code blindedMessage} with the private key in {@code kp}. */
    public static byte[] sign(byte[] blindedMessage, KeyPair kp) {
        throw new UnsupportedOperationException("Part 5");
    }

    /**
     * Unblinds a blind signature to produce the final signature over the
     * original message.
     */
    public static byte[] unblind(byte[] blindedSignature, byte[] blindingFactor,
                                 KeyPair kp, int modLen) {
        throw new UnsupportedOperationException("Part 5");
    }

    /** Verifies {@code signature} over {@code message} using the public key in {@code kp}. */
    public static boolean verify(byte[] message, byte[] signature, KeyPair kp) {
        throw new UnsupportedOperationException("Part 5");
    }
}
