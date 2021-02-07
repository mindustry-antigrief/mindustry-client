package com.github.blahblahbloopster.crypto

import net.i2p.crypto.eddsa.*
import net.i2p.crypto.eddsa.KeyPairGenerator
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec
import java.security.*
import java.security.KeyFactory

/**
 * Utility class that handles signing.
 * Uses ED25519 for signing.
 * For encryption, it should be modified to send keys via ED25519 or ECDH and move to AES.
 */
object Crypto {
    var signatureSize = 64
    private lateinit var signatureEngine: Signature

    /** Initializes cryptography stuff, must be called before usage. */
    fun init() {
        Security.addProvider(EdDSASecurityProvider())
        signatureEngine = EdDSAEngine.getInstance("NONEwithEdDSA")  // It appears that it actually uses SHA512 instead of nothing
    }

    fun generateKeyPair(): KeyPair {
        val generator = KeyPairGenerator()
        generator.initialize(256, SecureRandom.getInstance("NativePRNGNonBlocking"))
        return generator.generateKeyPair()
    }

    /**
     * Signs an input with a private key (verify with [verify]).
     * Note: vulnerable to replay attack.
     */
    fun sign(input: ByteArray, key: PrivateKey): ByteArray {
        signatureEngine.initSign(key)
        signatureEngine.setParameter(EdDSAEngine.ONE_SHOT_MODE)
        signatureEngine.update(input)
        return signatureEngine.sign()
    }

    fun encrypt(input: ByteArray, key: Key) {

    }

    /**
     * Verifies a signature from [sign] given the input, signature, and corresponding public key.
     * Note: vulnerable to replay attack.
     */
    fun verify(original: ByteArray, sign: ByteArray, key: EdDSAPublicKey): Boolean {
        signatureEngine.initVerify(key)
        signatureEngine.setParameter(EdDSAEngine.ONE_SHOT_MODE)
        signatureEngine.update(original)
        return signatureEngine.verify(sign)
    }

    /** Serializes the private key into a [ByteArray].  Deserialize with [deserializePublic]. */
    fun serializePublic(key: EdDSAPublicKey): ByteArray {
        return key.abyte
    }

    /** Serializes the public key into a [ByteArray].  Deserialize with [deserializePrivate]. */
    fun deserializePrivate(input: ByteArray): PrivateKey {
        val spec = EdDSAPrivateKeySpec(input, EdDSANamedCurveTable.ED_25519_CURVE_SPEC)
        val factory = KeyFactory.getInstance("EdDSA")
        return factory.generatePrivate(spec)
    }

    /** Deserializes the public key from a [ByteArray].  Serialize with [serializePublic]. */
    fun deserializePublic(input: ByteArray): PublicKey {
        val spec = EdDSAPublicKeySpec(input, EdDSANamedCurveTable.ED_25519_CURVE_SPEC)
        val factory = KeyFactory.getInstance("EdDSA")
        return factory.generatePublic(spec)
    }

    /** Deserializes the private key from a [ByteArray].  Serialize with [serializePrivate]. */
    fun serializePrivate(key: EdDSAPrivateKey): ByteArray {
        return key.seed
    }
}
