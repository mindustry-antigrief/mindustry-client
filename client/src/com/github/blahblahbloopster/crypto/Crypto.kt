package com.github.blahblahbloopster.crypto

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAKeyGenParameterSpec
import java.security.spec.X509EncodedKeySpec

/**
 * Utility class that handles signing.
 * Uses SHA384 with RSA for signing.
 * For encryption, it will need to be modified to send keys via RSA and move to AES.
 */
object Crypto {
    /** Handles key deserialization.  Not sure if I should have it instantiate a new one very time it's used. */
    private lateinit var factory: KeyFactory
    /** Handles signing.  Not sure if I should have it instantiate a new one very time it's used. */
    private lateinit var signature: Signature
    /** The size of a signature because [Signature] doesn't let you get the length out. */
    var signatureSize = 384 / 8

    /** Initializes cryptography stuff, must be called before usage. */
    fun init() {
        Security.addProvider(BouncyCastleProvider())
        signature = Signature.getInstance("SHA384withRSA", "BC")
        factory = KeyFactory.getInstance("RSA", "BC")
    }

    /** Encodes an RSA [PrivateKey] to bytes.  Decode with [decodePrivate] */
    fun encodePrivate(key: PrivateKey): ByteArray {
        return key.encoded
    }

    /** Encodes an RSA [PublicKey] to bytes.  Decode with [decodePublic] */
    fun encodePublic(key: PublicKey): ByteArray {
        return key.encoded
    }

    /** Decodes an RSA private key from [encodePrivate] */
    fun decodePrivate(encoded: ByteArray): PrivateKey {
        val pkcs8EncodedKeySpec = PKCS8EncodedKeySpec(encoded)
        return factory.generatePrivate(pkcs8EncodedKeySpec)
    }

    /** Decodes an RSA public key from [encodePublic] */
    fun decodePublic(encoded: ByteArray): PublicKey {
        val x509EncodedKeySpec = X509EncodedKeySpec(encoded)
        return factory.generatePublic(x509EncodedKeySpec)
    }

    /** Generates a new RSA keypair using the native PRNG. */
    fun generateKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance("RSA", "BC")
        generator.initialize(RSAKeyGenParameterSpec(3072, RSAKeyGenParameterSpec.F4), SecureRandom.getInstance("NativePRNGNonBlocking"))

        return generator.genKeyPair()
    }

    /**
     * Signs an input with a private key (verify with [verify]).
     * Note: vulnerable to replay attack, include time in input?
     */
    fun sign(input: ByteArray, key: PrivateKey): ByteArray {
        signature.initSign(key)
        signature.update(input)
        return signature.sign()
    }

    /**
     * Verifies a signature from [sign] given the input, signature, and corresponding public key.
     * Note: vulnerable to replay attack, include time in input?
     */
    fun verify(original: ByteArray, sign: ByteArray, key: PublicKey): Boolean {
        signature.initVerify(key)
        signature.update(original)
        return signature.verify(sign)
    }
}

fun main() {
    Crypto.init()
}
