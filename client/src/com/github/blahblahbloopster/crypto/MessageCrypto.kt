package com.github.blahblahbloopster.crypto

import arc.Core
import arc.Events
import arc.graphics.Color
import arc.util.Log
import arc.util.Time
import arc.util.serialization.Base64Coder
import com.github.blahblahbloopster.ui.age
import com.github.blahblahbloopster.ui.remainingBytes
import com.github.blahblahbloopster.ui.toInstant
import mindustry.Vars
import mindustry.game.EventType
import java.nio.ByteBuffer
import java.time.Instant
import java.util.zip.DeflaterInputStream
import java.util.zip.InflaterInputStream

/** Provides the interface between [Crypto] and a [CommunicationSystem], and handles some UI stuff.
 * TODO: replace a lot of this with a real packet system
 */
class MessageCrypto {
    lateinit var communicationSystem: CommunicationSystem
    var keyQuad: KeyQuad? = null

    var player = PlayerTriple(0, 0, "")  // Maps player ID to last sent message
    var received = ReceivedTriple(0, 0, byteArrayOf()) // Maps player ID to last sent message
    var keys: KeyList = KeyFolder
    val listeners = mutableListOf<(MessageCryptoEvent) -> Unit>()

    private fun fire(event: MessageCryptoEvent) {
        listeners.forEach {
            it(event)
        }
    }

    companion object {
        private const val ENCRYPTION_VALIDITY = 0b10101010.toByte()

        open class MessageCryptoEvent

        class SignatureEvent(val sender: Int, val senderKey: KeyHolder?, val message: String?, val valid: Boolean) : MessageCryptoEvent()

        class EncryptedMessageEvent(val sender: Int, val senderKey: KeyHolder, val message: String?) : MessageCryptoEvent()

        fun base64public(input: String): PublicKeyPair? {
            return try {
                PublicKeyPair(Base64Coder.decode(input))
            } catch (e: Exception) { null }
        }
    }

    data class PlayerTriple(val id: Int, val time: Long, val message: String)

    data class ReceivedTriple(val id: Int, val time: Long, val signature: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ReceivedTriple

            if (id != other.id) return false
            if (time != other.time) return false
            if (!signature.contentEquals(other.signature)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = id
            result = 31 * result + time.hashCode()
            result = 31 * result + signature.contentHashCode()
            return result
        }
    }

    fun init(communicationSystem: CommunicationSystem) {
        this.communicationSystem = communicationSystem
        communicationSystem.addListener(::handle)

        if (Core.settings?.dataDirectory?.child("key.txt")?.exists() == true) {  // Protects against nullability
            try {
                keyQuad = KeyQuad(Base64Coder.decode(Core.settings.dataDirectory.child("key.txt").readString()))
                Log.info("Loaded keypair")
            } catch (ignored: Exception) {}

            Events.on(EventType.SendChatMessageEvent::class.java) { event ->
                sign(event.message, keyQuad ?: return@on)
                Time.run(0.05f) {
                    val message = Vars.ui.chatfrag.messages.find { it.message.contains(player.message) } ?: return@run
                    message.backgroundColor = Color.green.cpy().mul(0.4f)
                }
            }
        }

        if (Core.app?.isDesktop == true) {
            listeners.add {
                if (it is SignatureEvent && it.valid && it.message != null) {
                    val message = Vars.ui?.chatfrag?.messages?.find { msg -> msg.message.contains(it.message) }
                    message?.backgroundColor = Color.sky.cpy().mul(if (it.senderKey?.official == true) 0.75f else 0.4f)
                    message?.verifiedSender = it.senderKey?.name ?: return@add
                    message?.format()
                } else if (it is EncryptedMessageEvent && it.message != null) {
                    Vars.ui?.chatfrag?.addMessage(it.message, it.senderKey.name, Color.green.cpy().mul(if (it.senderKey.official) 0.75f else 0.4f))
                }
            }
        }

        Events.on(EventType.PlayerChatEventClient::class.java) { event ->
            player = PlayerTriple((event.player ?: return@on).id, Instant.now().epochSecond, event.message)
            check(player, received)
        }
    }

    fun base64public(): String? {
        return Base64Coder.encode(PublicKeyPair(keyQuad ?: return null).serialize()).concatToString()
    }

    /** Checks the validity of a message given two triples, see above. */
    private fun check(player: PlayerTriple, received: ReceivedTriple) {
        fun event(sender: Int = 0, keyHolder: KeyHolder? = null, message: String? = null, valid: Boolean = false) {
            fire(SignatureEvent(sender, keyHolder, message, valid))
        }

        if (player.id == 0 || player.time == 0L || player.message == "") return
        if (received.id == 0 || received.time == 0L || received.signature.isEmpty()) return
        if (player.time.toInstant().age() > 3 || received.time.toInstant().age() > 3) {
            event(player.id, message = player.message)
            return
        }

        if (player.id != received.id) {
            event(message = player.message)
            return
        }

        for (key in keys) {
            val match = verify(player.message, received.id, received.signature, key.keys, received.time)
            if (match) {
                event(player.id, key, player.message, true)
                return
            }
        }
        event()
    }

    /**
     *  Converts an input message, sender ID, and current time (unix time) to a [ByteArray] for sending.
     *  The message isn't used on its own because it would be vulnerable to replay attacks.
     */
    private fun stringToSendable(input: String, sender: Int, time: Long): ByteArray {
        val output = input.toByteArray().toMutableList()
        output.addAll(ByteBuffer.allocate(4).putInt(sender).array().toList())  // Add sender ID
        output.addAll(ByteBuffer.allocate(8).putLong(time).array().toList())  // Add current time
        return output.toByteArray()
    }

    /** Signs an outgoing message.  Includes the sender ID and current time to prevent impersonation and replay attacks. */
    fun sign(message: String, key: KeyQuad) {
        val time = Instant.now().epochSecond
        val out = ByteBuffer.allocate(Crypto.signatureSize + 16)
        out.putInt(0)  // Signatures are type 0
        out.putLong(time)
        out.putInt(communicationSystem.id)
        val signature = Crypto.sign(stringToSendable(message, communicationSystem.id, time), key.edPrivateKey)
        out.put(signature)
        communicationSystem.send(out.array())
    }

    fun encrypt(message: String, destination: KeyHolder) {
        val time = Instant.now().epochSecond
        val id = communicationSystem.id
        val compressor = DeflaterInputStream(message.toByteArray().inputStream())
        val encoded = compressor.readBytes()
        val plaintext = ByteBuffer.allocate(encoded.size + Long.SIZE_BYTES + Int.SIZE_BYTES + Byte.SIZE_BYTES)
        plaintext.putLong(time)
        plaintext.putInt(id)
        plaintext.put(ENCRYPTION_VALIDITY)
        plaintext.put(encoded)
        val ciphertext = destination.crypto?.encrypt(plaintext.array()) ?: return
        val toSend = ByteBuffer.allocate(ciphertext.size + Int.SIZE_BYTES + Long.SIZE_BYTES + Int.SIZE_BYTES)
        toSend.putInt(1)  // Encrypted messages are type 1
        toSend.putLong(time)
        toSend.putInt(id)
        toSend.put(ciphertext)

        communicationSystem.send(toSend.array())
    }

    private fun handle(input: ByteArray, sender: Int) {
        val buf = ByteBuffer.wrap(input)
        val type = buf.int
        val time = buf.long
        val id = buf.int
        val content = buf.remainingBytes()

        when (type) {
            0 -> {
                received = ReceivedTriple(sender, time, content)
                check(player, received)
            }
            1 -> {
                for (key in keys) {
                    val crypto = key.crypto ?: continue
                    try {
                        val decoded = crypto.decrypt(content)
                        val buffer = ByteBuffer.wrap(decoded)
                        val timeSent = buffer.long
                        val senderId = buffer.int
                        val validity = buffer.get()
                        val plaintext = buffer.remainingBytes()

                        if (validity != ENCRYPTION_VALIDITY) continue

                        if (timeSent.toInstant().age() > 3 || time.toInstant().age() > 3) continue

                        if (senderId != sender) return

                        val zip = InflaterInputStream(plaintext.inputStream())

                        val str = zip.readBytes().decodeToString()

                        fire(EncryptedMessageEvent(sender, key, str))
                        zip.close()
                    } catch (ignored: Exception) {}
                }
            }
        }
    }

    /** Verifies an incoming message. */
    private fun verify(message: String, sender: Int, signature: ByteArray, key: PublicKeyPair, time: Long): Boolean {
        if (signature.size != Crypto.signatureSize) {
            return false
        }

        val original = stringToSendable(message, sender, time)

        val validSignature = try {
            Crypto.verify(original, signature, key.edPublicKey)
        } catch (ignored: java.lang.Exception) { false }
        val age = time.toInstant().age()

        return age < 3 && validSignature
    }
}
