package client

import mindustry.client.crypto.Crypto
import mindustry.client.crypto.CryptoClient
import mindustry.client.crypto.KeyQuad
import mindustry.client.crypto.PublicKeyPair
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** A few tests for [Crypto]  */
class CryptographyTests {

    companion object {
        @BeforeAll
        @JvmStatic
        fun init() {
            Crypto.initializeAlways()
        }
    }

    /** Tests key serialization and deserialization.  */
    @Test
    fun testSerialization() {
        val quad = Crypto.generateKeyQuad()
        val decoded = KeyQuad(quad.serialize())
        assertEquals(quad, decoded)

        val pair = PublicKeyPair(quad)
        val decodedPair = PublicKeyPair(pair.serialize())
        assertEquals(pair, decodedPair)
    }

    @Test
    fun testClientInteractions() {
        val client1 = CryptoClient(Crypto.generateKeyQuad())
        val client2 = CryptoClient(Crypto.generateKeyQuad())

        client1.generate(PublicKeyPair(client2.key.xPublicKey, client2.key.edPublicKey))
        client2.generate(PublicKeyPair(client1.key.xPublicKey, client1.key.edPublicKey))

        assertTrue(client1.sharedSecret.contentEquals(client2.sharedSecret))
        assertEquals(client1.aesKey, client2.aesKey)
        assertTrue(client1.iv.iv.contentEquals(client2.iv.iv))

        val original = Random.nextBytes(1024)
        assertTrue(client1.verify(client2.sign(original), original))
        assertTrue(client2.verify(client1.sign(original), original))

        val plaintext = Random.nextBytes(1024)
        val encrypted1 = client1.encrypt(plaintext)
        val encrypted2 = client2.encrypt(plaintext)

        assertTrue(client2.decrypt(encrypted1).contentEquals(plaintext))
        assertTrue(client1.decrypt(encrypted2).contentEquals(plaintext))
    }
}
