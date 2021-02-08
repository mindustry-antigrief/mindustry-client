package client

import com.github.blahblahbloopster.crypto.Crypto
import com.github.blahblahbloopster.crypto.CryptoClient
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** A few tests for [Crypto]  */
class CryptographyTests {

    companion object {
        @BeforeAll
        @JvmStatic
        fun init() {
            Crypto.init()
        }
    }

    /** Tests signature creation and validation.  */
    @Test
    fun testSigning() {
        val pair = Crypto.generateKeyPair()
        val input = Random.nextBytes(128)
        val signature: ByteArray = Crypto.sign(input, pair.private as Ed25519PrivateKeyParameters)
        Assertions.assertTrue(Crypto.verify(input, signature, pair.public as Ed25519PublicKeyParameters))
    }

    /** Tests key serialization and deserialization.  */
    @Test
    fun testSerialization() {
        val pair = Crypto.generateKeyPair()
        val encodedPublic: ByteArray = Crypto.serializePublic(pair.public as Ed25519PublicKeyParameters)
        val encodedPrivate: ByteArray = Crypto.serializePrivate(pair.private as Ed25519PrivateKeyParameters)
        Assertions.assertTrue(Crypto.deserializePublic(encodedPublic).encoded.contentEquals((pair.public as Ed25519PublicKeyParameters).encoded))
        Assertions.assertTrue(Crypto.deserializePrivate(encodedPrivate).encoded.contentEquals((pair.private as Ed25519PrivateKeyParameters).encoded))
    }

    /** Tests shared secret generation and AES. */
    @Test
    fun testEncryption() {
        val client1KeyPair = Crypto.generateKeyPair()
        val client1 = CryptoClient(client1KeyPair.public as Ed25519PublicKeyParameters, client1KeyPair.private as Ed25519PrivateKeyParameters)

        val client2KeyPair = Crypto.generateKeyPair()
        val client2 = CryptoClient(client2KeyPair.public as Ed25519PublicKeyParameters, client2KeyPair.private as Ed25519PrivateKeyParameters)

        client1.generate(client2.xPublicKey)
        client2.generate(client1.xPublicKey)

        assert(client1.sharedSecret.contentEquals(client2.sharedSecret))
        assert(client1.aesKey.encoded.contentEquals(client2.aesKey.encoded))
        assert(client1.iv.iv.contentEquals(client2.iv.iv))

        val plaintext = Random.nextBytes(10)

        val encrypted1 = client1.encrypt(plaintext)
        val encrypted2 = client2.encrypt(plaintext)
        assert(encrypted1.contentEquals(encrypted2))

        assert(client2.decrypt(encrypted1).contentEquals(plaintext))
        assert(client1.decrypt(encrypted2).contentEquals(plaintext))
    }
}
