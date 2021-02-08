package com.github.blahblahbloopster.crypto

import arc.Core
import arc.Events
import arc.graphics.Color
import arc.util.Log
import arc.util.serialization.Base64Coder
import mindustry.Vars
import mindustry.game.EventType
import org.bouncycastle.crypto.AsymmetricCipherKeyPair
import org.bouncycastle.crypto.params.AsymmetricKeyParameter
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import java.nio.ByteBuffer
import java.security.PublicKey
import java.time.Instant
import kotlin.math.abs

/** Provides the interface between [Crypto] and a [CommunicationSystem] */
class MessageCrypto {
    lateinit var communicationSystem: CommunicationSystem
    var keyPair: AsymmetricCipherKeyPair? = null

    private var player = PlayerTriple(0, 0, "")  // Maps player ID to last sent message
    private var received = ReceivedTriple(0, 0, byteArrayOf()) // Maps player ID to last sent message

    companion object {
        const val VERSION = 1
        private const val ENCRYPTED_VALIDITY_CHECK: Byte = 0b10101010.toByte()

        enum class TransmissionType(onReceived: (senderId: Int, content: ByteArray, messageCrypto: MessageCrypto) -> Unit) {
            SIGNATURE({ senderId, content, messageCrypto ->
                messageCrypto.received = ReceivedTriple(
                    senderId,
                    Instant.now().epochSecond,
                    content
                )
                messageCrypto.check(messageCrypto.player, messageCrypto.received)
            }),
//            ENCRYPTED_CHAT({ senderId, content, messageCrypto ->
//                for (key in KeyFolder.keys) {
//                    if (Crypto)
//                }
//            })
        }

        fun base64public(input: Ed25519PublicKeyParameters): String {
            return Base64Coder.encode(Crypto.serializePublic(input)).toString()
        }

        fun base64public(input: String): Ed25519PublicKeyParameters? {
            return try {
                Crypto.deserializePublic(Base64Coder.decode(input))
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        fun base64private(input: String): Ed25519PrivateKeyParameters? {
            return try {
                Crypto.deserializePrivate(Base64Coder.decode(input))
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        fun base64private(input: Ed25519PrivateKeyParameters): String {
            return Base64Coder.encode(Crypto.serializePrivate(input)).toString()
        }
    }

    private data class PlayerTriple(val id: Int, val time: Long, val message: String)

    private data class ReceivedTriple(val id: Int, val time: Long, val signature: ByteArray)

    fun init(communicationSystem: CommunicationSystem) {
        this.communicationSystem = communicationSystem

        if (Core.settings.dataDirectory.child("privateKey.txt").exists() && Core.settings.dataDirectory.child("publicKey.txt").exists()) {
            keyPair = AsymmetricCipherKeyPair(
                    base64public(Core.settings.dataDirectory.child("publicKey.txt").readString())!! as AsymmetricKeyParameter,
                    base64private(Core.settings.dataDirectory.child("privateKey.txt").readString())!! as AsymmetricKeyParameter
            )
            Log.info("Loaded keypair")

            Events.on(EventType.SendChatMessageEvent::class.java) { event ->
                keyPair ?: return@on
                sign(event.message, keyPair!!.private)
            }
        }

        Events.on(EventType.PlayerChatEventClient::class.java) { event ->
            player = PlayerTriple((event.player ?: return@on).id, Instant.now().epochSecond, event.message)
            check(player, received)
        }
        communicationSystem.listeners.add { input, sender ->
            received = ReceivedTriple(sender, Instant.now().epochSecond, input)
            check(player, received)
        }
    }

    fun base64public(): String? {
        keyPair?: return null
        return Base64Coder.encode(Crypto.serializePublic(keyPair!!.public as Ed25519PublicKeyParameters)).toString()
    }

    fun base64private(): String? {
        keyPair?: return null
        return Base64Coder.encode(Crypto.serializePrivate(keyPair!!.private as Ed25519PrivateKeyParameters)).toString()
    }

    /** Checks the validity of a message given two triples, see above. */
    private fun check(player: PlayerTriple, received: ReceivedTriple) {
        if (player.id == 0 || player.time == 0L || player.message == "") return
        if (received.id == 0 || received.time == 0L || received.signature.isEmpty()) return
        val time = Instant.now().epochSecond
        if (abs(player.time - time) > 3 || abs(received.time - time) > 3) {
            return
        }

        if (player.id != received.id) {
            return
        }

        for (key in KeyFolder.keys) {
            val match = verify(player.message, player.id, received.signature, key.key)
            if (match) {
                val message = Vars.ui.chatfrag.messages.find { it.message.contains(player.message) } ?: break
                message.backgroundColor = Color.green.cpy().mul(if (key.official) 0.75f else 0.4f)
                message.verifiedSender = key.name
                message.format()
                break
            }
        }
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
    fun sign(message: String, key: AsymmetricKeyParameter) {
        val time = Instant.now().epochSecond
        val out = ByteBuffer.allocate(Crypto.signatureSize + 12)
        out.putLong(time)
        out.putInt(communicationSystem.id)
        val signature = Crypto.sign(stringToSendable(message, communicationSystem.id, time),
            key as Ed25519PrivateKeyParameters
        )
        out.put(signature)
        communicationSystem.send(out.array())
    }

    /** Verifies an incoming message. */
    fun verify(message: String, sender: Int, signature: ByteArray, key: Ed25519PublicKeyParameters): Boolean {
        if (signature.size != Crypto.signatureSize + 12) {
            return false
        }
        val buffer = ByteBuffer.wrap(signature)
        val time = buffer.long
        val senderId = buffer.int
        val bytes = buffer.array()
        val signatureBytes = bytes.takeLast(Crypto.signatureSize).toByteArray()

        val original = stringToSendable(message, sender, time)

        return try {
            val valid = Crypto.verify(original, signatureBytes, key)
            abs(Instant.now().epochSecond - time) < 3 &&
                    sender == senderId &&
                    valid
        } catch (ignored: Exception) {
            false
        }
    }
}
