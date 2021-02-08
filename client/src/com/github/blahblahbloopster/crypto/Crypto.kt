package com.github.blahblahbloopster.crypto

import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.params.*
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.nio.ByteBuffer
import java.security.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

typealias XPublicKey = X25519PublicKeyParameters
typealias XPrivateKey = X25519PrivateKeyParameters

typealias EdPublicKey = Ed25519PublicKeyParameters
typealias EdPrivateKey = Ed25519PrivateKeyParameters


fun ByteBuffer.bytes(num: Int): ByteArray {
    val bytes = ByteArray(num)
    get(bytes)
    return bytes
}

class KeyQuad {
    val xPrivateKey: XPrivateKey
    val xPublicKey: XPublicKey
    val edPrivateKey: EdPrivateKey
    val edPublicKey: EdPublicKey

    constructor(xPrivateKey: XPrivateKey, xPublicKey: XPublicKey, edPrivateKey: EdPrivateKey, edPublicKey: EdPublicKey) {
        this.xPrivateKey = xPrivateKey
        this.xPublicKey = xPublicKey
        this.edPrivateKey = edPrivateKey
        this.edPublicKey = edPublicKey
    }

    constructor(input: ByteArray) {
        val buff = ByteBuffer.wrap(input)
        xPrivateKey = XPrivateKey(buff.bytes(XPrivateKey.SECRET_SIZE), 0)
        xPublicKey = XPublicKey(buff.bytes(XPublicKey.KEY_SIZE), 0)
        edPrivateKey = EdPrivateKey(buff.bytes(EdPrivateKey.KEY_SIZE), 0)
        edPublicKey = EdPublicKey(buff.bytes(EdPublicKey.KEY_SIZE), 0)
    }

    fun serialize(): ByteArray {
        return xPrivateKey.encoded.plus(xPublicKey.encoded).plus(edPrivateKey.encoded).plus(edPublicKey.encoded)
    }

    override operator fun equals(other: Any?): Boolean {
        if (other is KeyQuad) {
            return other.xPrivateKey.encoded.contentEquals(xPrivateKey.encoded) &&
                    other.xPublicKey.encoded.contentEquals(xPublicKey.encoded) &&
                    other.edPrivateKey.encoded.contentEquals(edPrivateKey.encoded) &&
                    other.edPublicKey.encoded.contentEquals(edPublicKey.encoded)
        }
        return false
    }

    override fun hashCode(): Int {
        var result = xPrivateKey.encoded.contentHashCode()
        result = 31 * result + xPublicKey.encoded.contentHashCode()
        result = 31 * result + edPrivateKey.encoded.contentHashCode()
        result = 31 * result + edPublicKey.encoded.contentHashCode()
        return result
    }
}

class PublicKeyPair {

    val xPublicKey: XPublicKey
    val edPublicKey: EdPublicKey

    constructor(xPublicKey: XPublicKey, edPublicKey: EdPublicKey) {
        this.xPublicKey = xPublicKey
        this.edPublicKey = edPublicKey
    }

    constructor(input: ByteArray) {
        val buff = ByteBuffer.wrap(input)
        xPublicKey = XPublicKey(buff.bytes(XPublicKey.KEY_SIZE), 0)
        edPublicKey = EdPublicKey(buff.bytes(EdPublicKey.KEY_SIZE), 0)
    }

    constructor(key: KeyQuad) {
        xPublicKey = key.xPublicKey
        edPublicKey = key.edPublicKey
    }

    fun serialize(): ByteArray {
        return xPublicKey.encoded.plus(edPublicKey.encoded)
    }

    override operator fun equals(other: Any?): Boolean {
        if (other is PublicKeyPair) {
            return other.xPublicKey.encoded.contentEquals(xPublicKey.encoded) &&
                    other.edPublicKey.encoded.contentEquals(edPublicKey.encoded)
        }
        return false
    }

    override fun hashCode(): Int {
        var result = xPublicKey.encoded.contentHashCode()
        result = 31 * result + edPublicKey.encoded.contentHashCode()
        return result
    }
}

/**
 * Utility class that handles signing.
 * Uses ED25519 for signing.
 * For encryption, it should be modified to send keys via ED25519 or ECDH and move to AES.
 */
object Crypto {
    var signatureSize = 64
    private lateinit var signatureEngine: Ed25519Signer
    private lateinit var aes: Cipher
    private val random = SecureRandom.getInstance("NativePRNGNonBlocking")

    /** Initializes cryptography stuff, must be called before usage. */
    fun init() {
        Security.addProvider(BouncyCastleProvider())
        signatureEngine = Ed25519Signer()
        aes = Cipher.getInstance("AES/CBC/PKCS5Padding")
    }

    fun generateKeyQuad(): KeyQuad {
        val generator = Ed25519KeyPairGenerator()
        generator.init(Ed25519KeyGenerationParameters(random))
        val edKeys = generator.generateKeyPair()
        val xGenerator = X25519KeyPairGenerator()
        xGenerator.init(X25519KeyGenerationParameters(random))
        val xKeys = xGenerator.generateKeyPair()

        return KeyQuad(xKeys.private as XPrivateKey, xKeys.public as XPublicKey, edKeys.private as EdPrivateKey, edKeys.public as EdPublicKey)
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


class CryptoClient(val key: KeyQuad) {
    var sharedSecret: ByteArray = byteArrayOf()
    lateinit var aesKey: SecretKeySpec
    lateinit var iv: IvParameterSpec
    lateinit var otherKey: EdPublicKey

    fun generate(other: PublicKeyPair) {
        otherKey = other.edPublicKey
        val agreement = X25519Agreement()
        agreement.init(key.xPrivateKey)
        sharedSecret = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(other.xPublicKey, sharedSecret, 0)

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

    fun sign(input: ByteArray): ByteArray {
        return Crypto.sign(input, key.edPrivateKey)
    }

    fun verify(signature: ByteArray, original: ByteArray): Boolean {
        return Crypto.verify(original, signature, otherKey)
    }
}

fun main() {
    Crypto.init()

    // Test serialization
    val quad = Crypto.generateKeyQuad()
    val decoded = KeyQuad(quad.serialize())
    assert(quad == decoded)

    val pair = PublicKeyPair(quad)
    val decodedPair = PublicKeyPair(pair.serialize())
    assert(pair == decodedPair)

    val client1 = CryptoClient(Crypto.generateKeyQuad())
    val client2 = CryptoClient(Crypto.generateKeyQuad())

    client1.generate(PublicKeyPair(client2.key.xPublicKey, client2.key.edPublicKey))
    client2.generate(PublicKeyPair(client1.key.xPublicKey, client1.key.edPublicKey))

    assert(client1.sharedSecret.contentEquals(client2.sharedSecret))
    assert(client1.aesKey == client2.aesKey)
    assert(client1.iv.iv.contentEquals(client2.iv.iv))

    val original = Random.Default.nextBytes(1024)
    assert(client1.verify(client2.sign(original), original))
    assert(client2.verify(client1.sign(original), original))
}
