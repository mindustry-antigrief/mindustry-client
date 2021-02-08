package com.github.blahblahbloopster.crypto

import org.bouncycastle.crypto.AsymmetricCipherKeyPair
import org.bouncycastle.crypto.KeyGenerationParameters
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.params.*
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.jcajce.interfaces.EdDSAPublicKey
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Utility class that handles signing.
 * Uses ED25519 for signing.
 * For encryption, it should be modified to send keys via ED25519 or ECDH and move to AES.
 */
object Crypto {
    var signatureSize = 64
    private lateinit var signatureEngine: Ed25519Signer
    private lateinit var aes: Cipher

    /** Initializes cryptography stuff, must be called before usage. */
    fun init() {
        Security.addProvider(BouncyCastleProvider())
        signatureEngine = Ed25519Signer()
        aes = Cipher.getInstance("AES/CBC/PKCS5Padding")
    }

    fun generateKeyPair(): AsymmetricCipherKeyPair {
        val generator = Ed25519KeyPairGenerator()
        generator.init(Ed25519KeyGenerationParameters(SecureRandom.getInstance("NativePRNGNonBlocking")))
        return generator.generateKeyPair()
    }

    /**
     * Signs an input with a private key (verify with [verify]).
     * Note: vulnerable to replay attack.
     */
    fun sign(input: ByteArray, key: Ed25519PrivateKeyParameters): ByteArray {
        signatureEngine.init(true, key)
        signatureEngine.update(input, 0, input.size)
        return signatureEngine.generateSignature()
    }

    fun encrypt(input: ByteArray, key: SecretKeySpec, iv: IvParameterSpec): ByteArray {
        aes.init(Cipher.ENCRYPT_MODE, key, iv)
        return aes.doFinal(input)
    }

    fun decrypt(input: ByteArray, key: SecretKeySpec, iv: IvParameterSpec): ByteArray {
        aes.init(Cipher.DECRYPT_MODE, key, iv)
        return aes.doFinal(input)
    }

    /**
     * Verifies a signature from [sign] given the input, signature, and corresponding public key.
     * Note: vulnerable to replay attack.
     */
    fun verify(original: ByteArray, sign: ByteArray, key: Ed25519PublicKeyParameters): Boolean {
        signatureEngine.init(false, key)
        signatureEngine.update(original, 0, original.size)
        return signatureEngine.verifySignature(sign)
    }

    /** Serializes the private key into a [ByteArray].  Deserialize with [deserializePublic]. */
    fun serializePublic(key: Ed25519PublicKeyParameters): ByteArray {
        return key.encoded
    }

    /** Serializes the public key into a [ByteArray].  Deserialize with [deserializePrivate]. */
    fun deserializePrivate(input: ByteArray): Ed25519PrivateKeyParameters {
        return Ed25519PrivateKeyParameters(input, 0)
    }

    /** Deserializes the public key from a [ByteArray].  Serialize with [serializePublic]. */
    fun deserializePublic(input: ByteArray): Ed25519PublicKeyParameters {
        return Ed25519PublicKeyParameters(input, 0)
    }

    /** Deserializes the private key from a [ByteArray].  Serialize with [serializePrivate]. */
    fun serializePrivate(key: Ed25519PrivateKeyParameters): ByteArray {
        return key.encoded
    }
}


class CryptoClient(publicKey: Ed25519PublicKeyParameters, privateKey: Ed25519PrivateKeyParameters) {
    private var pair: AsymmetricCipherKeyPair
    init {
        val gen = X25519KeyPairGenerator()
        gen.init(KeyGenerationParameters(SecureRandom(privateKey.encoded + publicKey.encoded), 256))
        pair = gen.generateKeyPair()
    }
    val xPublicKey: X25519PublicKeyParameters = pair.public as X25519PublicKeyParameters
    val xPrivateKey: X25519PrivateKeyParameters = pair.private as X25519PrivateKeyParameters
    var sharedSecret: ByteArray = byteArrayOf()
    lateinit var aesKey: SecretKeySpec
    lateinit var iv: IvParameterSpec

    fun generate(otherKey: X25519PublicKeyParameters) {
        val agreement = X25519Agreement()
        agreement.init(xPrivateKey)
        sharedSecret = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(otherKey, sharedSecret, 0)

        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(sharedSecret)
        val keyBytes = ByteArray(16)
        System.arraycopy(digest.digest(), 0, keyBytes, 0, keyBytes.size)
        val ivBytes = ByteArray(16)
        System.arraycopy(digest.digest(), 16, ivBytes, 0, keyBytes.size)
        aesKey = SecretKeySpec(keyBytes, "AES")
        iv = IvParameterSpec(ivBytes)
    }

    fun encrypt(input: ByteArray): ByteArray {
        return Crypto.encrypt(input, aesKey, iv)
    }

    fun decrypt(input: ByteArray): ByteArray {
        return Crypto.decrypt(input, aesKey, iv)
    }
}
