package client

import com.github.blahblahbloopster.crypto.Crypto.generateKeyQuad
import com.github.blahblahbloopster.crypto.Crypto
import com.github.blahblahbloopster.crypto.KeyHolder
import com.github.blahblahbloopster.crypto.MessageCrypto.Companion.MessageCryptoEvent
import com.github.blahblahbloopster.crypto.MessageCrypto.Companion.SignatureEvent
import com.github.blahblahbloopster.crypto.MessageCrypto.PlayerTriple
import com.github.blahblahbloopster.crypto.MessageCrypto
import org.junit.jupiter.api.BeforeAll
import com.github.blahblahbloopster.crypto.DummyCommunicationSystem
import com.github.blahblahbloopster.crypto.DummyKeyList
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

class MessageCryptographyTests {

    companion object {
        var client1 = MessageCrypto()
        var client2 = MessageCrypto()

        @JvmStatic
        @BeforeAll
        fun init() {
            Crypto.init()
            client1.init(DummyCommunicationSystem())
            client2.init(DummyCommunicationSystem())
            client1.keys = DummyKeyList()
            client2.keys = DummyKeyList()
        }
    }

    /** Tests that signing messages works.  */
    @Test
    @Throws(InterruptedException::class)
    fun testSending() {
        val valid = AtomicBoolean()
        var receivedMessage: String? = null

        val client1pair = generateKeyQuad()
        val client2pair = generateKeyQuad()
        client1.keyQuad = client1pair
        client2.keyQuad = client2pair
        val client1holder = KeyHolder(client1pair.publicPair(), "client1", false, client2)
        val client2holder = KeyHolder(client2pair.publicPair(), "client2", false, client1)

        client1holder.crypto!!.generate(client1pair.publicPair())
        client2holder.crypto!!.generate(client2pair.publicPair())

        Assertions.assertTrue(client1holder.crypto!!.aesKey.encoded.contentEquals(client2holder.crypto!!.aesKey.encoded))
        Assertions.assertTrue(client1holder.crypto!!.iv.iv.contentEquals(client2holder.crypto!!.iv.iv))

        client1.keys.add(client2holder)
        client2.keys.add(client1holder)

        client1.listeners.add { event: MessageCryptoEvent? ->
            if (event is SignatureEvent) {
                valid.set(event.valid)
            } else if (event is MessageCrypto.Companion.EncryptedMessageEvent) {
                receivedMessage = event.message
            }
        }
        client2.listeners.add { event: MessageCryptoEvent? ->
            if (event is SignatureEvent) {
                valid.set(event.valid)
            } else if (event is MessageCrypto.Companion.EncryptedMessageEvent) {
                receivedMessage = event.message
            }
        }
        var message = "Hello world!"
        client2.player = PlayerTriple(client1.communicationSystem.id, Instant.now().epochSecond, message)
        client1.sign(message, client1pair)
        Assertions.assertTrue(valid.get())

        message = "Test test blah"
        client1.player = PlayerTriple(client2.communicationSystem.id, Instant.now().epochSecond, message)
        client2.sign(message, client2pair)
        Assertions.assertTrue(valid.get())

        message = "aaa"
        client1.player = PlayerTriple(client2.communicationSystem.id, Instant.now().epochSecond, message)
        client2.sign(message, client2pair)
        Assertions.assertTrue(valid.get())

        message = "aaaa"
        client1.player = PlayerTriple(client2.communicationSystem.id, Instant.now().epochSecond, message)
        client2.sign(message, client2pair)
        Assertions.assertTrue(valid.get())

        message = "oh no"
        client1.player = PlayerTriple(client2.communicationSystem.id, Instant.now().epochSecond, message)
        client2.sign(message, client1pair) // invalid, using wrong key to sign
        Thread.sleep(10L)
        Assertions.assertFalse(valid.get())

        message = "hello world"
        client1.encrypt(message, client2holder)
        Assertions.assertEquals(message, receivedMessage)

        message = "testing"
        client2.encrypt(message, client1holder)
        Assertions.assertEquals(message, receivedMessage)
    }
}