package com.verivote.core.crypto;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.CryptoException;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.engines.RSABlindingEngine;
import org.bouncycastle.crypto.engines.RSAEngine;
import org.bouncycastle.crypto.generators.RSABlindingFactorGenerator;
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator;
import org.bouncycastle.crypto.params.ParametersWithRandom;
import org.bouncycastle.crypto.params.RSABlindingParameters;
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.bouncycastle.crypto.params.RSAPrivateCrtKeyParameters;
import org.bouncycastle.crypto.signers.PSSSigner;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory;
import org.bouncycastle.crypto.util.PublicKeyFactory;
import org.bouncycastle.crypto.util.SubjectPublicKeyInfoFactory;

import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;

/**
 * Blind RSA-PSS signature service (RFC 9474 family).
 *
 * <p>Scheme: blind RSA-PSS (RFC 9474 family).
 * This class does NOT claim to implement RFC 9474 verbatim.</p>
 */
public final class BlindSignatureService {

    private static final int RSA_KEY_SIZE = 2048;
    private static final BigInteger RSA_EXPONENT = BigInteger.valueOf(65537);
    private static final int SALT_LEN = 32;

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
        SecureRandom rnd = new SecureRandom();
        RSAKeyPairGenerator gen = new RSAKeyPairGenerator();
        gen.init(new RSAKeyGenerationParameters(RSA_EXPONENT, rnd, RSA_KEY_SIZE, 80));
        AsymmetricCipherKeyPair pair = gen.generateKeyPair();
        try {
            byte[] spki = SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(pair.getPublic()).getEncoded();
            byte[] pkcs8 = PrivateKeyInfoFactory.createPrivateKeyInfo(pair.getPrivate()).getEncoded();
            return new KeyPair(spki, pkcs8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode RSA key pair", e);
        }
    }

    /**
     * Blinds {@code message} using the public key in {@code kp}.
     * The blinding factor is written into {@code outBlindingFactor[0]}.
     */
    public static byte[] blind(byte[] message, KeyPair kp, SecureRandom rnd,
                               BigInteger[] outBlindingFactor) {
        try {
            RSAKeyParameters pub = (RSAKeyParameters) PublicKeyFactory.createKey(kp.publicKeySpki());
            RSABlindingFactorGenerator bfg = new RSABlindingFactorGenerator();
            bfg.init(pub);
            BigInteger r = bfg.generateBlindingFactor();
            if (outBlindingFactor != null && outBlindingFactor.length > 0) {
                outBlindingFactor[0] = r;
            }
            RSABlindingParameters blindParams = new RSABlindingParameters(pub, r);
            PSSSigner clientBlind = new PSSSigner(new RSABlindingEngine(), new SHA256Digest(), SALT_LEN);
            clientBlind.init(true, new ParametersWithRandom(blindParams, rnd));
            clientBlind.update(message, 0, message.length);
            return clientBlind.generateSignature();
        } catch (IOException | CryptoException e) {
            throw new IllegalStateException("Failed to blind message", e);
        }
    }

    /** Signs {@code blindedMessage} with the private key in {@code kp}. */
    public static byte[] sign(byte[] blindedMessage, KeyPair kp) {
        try {
            RSAPrivateCrtKeyParameters priv = (RSAPrivateCrtKeyParameters) PrivateKeyFactory.createKey(kp.privateKeyPkcs8());
            RSAEngine rsa = new RSAEngine();
            rsa.init(true, priv);
            byte[] sig = rsa.processBlock(blindedMessage, 0, blindedMessage.length);
            int modLen = (priv.getModulus().bitLength() + 7) / 8;
            if (sig.length == modLen) {
                return sig;
            }
            byte[] padded = new byte[modLen];
            System.arraycopy(sig, 0, padded, modLen - sig.length, sig.length);
            return padded;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to sign blinded message", e);
        }
    }

    /**
     * Unblinds a blind signature to produce the final signature over the
     * original message.
     */
    public static byte[] unblind(byte[] blindedSignature, byte[] blindingFactor,
                                 KeyPair kp, int modLen) {
        try {
            RSAKeyParameters pub = (RSAKeyParameters) PublicKeyFactory.createKey(kp.publicKeySpki());
            BigInteger bf = new BigInteger(1, blindingFactor);
            RSABlindingParameters unblindParams = new RSABlindingParameters(pub, bf);
            RSABlindingEngine unblindEngine = new RSABlindingEngine();
            unblindEngine.init(false, unblindParams);
            byte[] unblindedRaw = unblindEngine.processBlock(blindedSignature, 0, blindedSignature.length);

            if (unblindedRaw.length == modLen) {
                return unblindedRaw;
            }
            byte[] unblinded = new byte[modLen];
            System.arraycopy(unblindedRaw, 0, unblinded, modLen - unblindedRaw.length, unblindedRaw.length);
            return unblinded;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to unblind signature", e);
        }
    }

    /** Verifies {@code signature} over {@code message} using the public key in {@code kp}. */
    public static boolean verify(byte[] message, byte[] signature, KeyPair kp) {
        try {
            RSAKeyParameters pub = (RSAKeyParameters) PublicKeyFactory.createKey(kp.publicKeySpki());
            int modLen = (pub.getModulus().bitLength() + 7) / 8;
            byte[] sig = signature;
            if (sig.length < modLen) {
                sig = new byte[modLen];
                System.arraycopy(signature, 0, sig, modLen - signature.length, signature.length);
            }
            PSSSigner verifier = new PSSSigner(new RSAEngine(), new SHA256Digest(), SALT_LEN);
            verifier.init(false, pub);
            verifier.update(message, 0, message.length);
            return verifier.verifySignature(sig);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to verify signature", e);
        }
    }
}
