package mindustry.client.crypto

import mindustry.client.Initializable
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.params.*
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.math.ec.rfc8032.Ed25519
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.Security
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

typealias XPublicKey = X25519PublicKeyParameters
typealias XPrivateKey = X25519PrivateKeyParameters

typealias EdPublicKey = Ed25519PublicKeyParameters
typealias EdPrivateKey = Ed25519PrivateKeyParameters

/**
 * Utility object that handles signing and encryption.
 * Uses ED25519 for signing and X25519 to generate a shared AES key.
 * DISCLAIMER: I am not a cryptographer.  This implementation may be buggy.
 */
object Crypto : Initializable {
    /** The length of a signature, in bytes. */
    var signatureSize = Ed25519.SIGNATURE_SIZE
    private lateinit var signatureEngine: Ed25519Signer
    private lateinit var aes: Cipher
    /** The secure random number generator. */
    private val random = SecureRandom.getInstanceStrong()

    /** Initializes cryptography stuff, must be called before usage. */
    override fun initializeAlways() {
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
     * Note: vulnerable to replay attack, do not use on its own.
     */
    fun sign(input: ByteArray, key: Ed25519PrivateKeyParameters): ByteArray {
        signatureEngine.init(true, key)
        signatureEngine.update(input, 0, input.size)
        return signatureEngine.generateSignature()
    }

    /** Encrypts a message with AES. */
    fun encrypt(input: ByteArray, key: SecretKeySpec, iv: IvParameterSpec): ByteArray {
        aes.init(Cipher.ENCRYPT_MODE, key, iv)
        return aes.doFinal(input)
    }

    /** Decrypts a message with AES. */
    fun decrypt(input: ByteArray, key: SecretKeySpec, iv: IvParameterSpec): ByteArray {
        aes.init(Cipher.DECRYPT_MODE, key, iv)
        return aes.doFinal(input)
    }

    /**
     * Verifies a signature from [sign] given the input, signature, and corresponding public key.
     */
    fun verify(original: ByteArray, sign: ByteArray, key: EdPublicKey): Boolean {
        signatureEngine.init(false, key)
        signatureEngine.update(original, 0, original.size)
        return signatureEngine.verifySignature(sign)
    }
}

/** Represents a client with a [KeyQuad].  It talks to exactly one other client and deals with generating the shared
 * secret and encryption. */
class CryptoClient(val key: KeyQuad) {
    var sharedSecret: ByteArray = byteArrayOf()
    lateinit var aesKey: SecretKeySpec
    lateinit var iv: IvParameterSpec
    lateinit var otherKey: EdPublicKey

    /** Generates the shared secret and initializes the AES cipher.  Must be called before using the other methods. */
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
    Crypto.initializeAlways()

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
