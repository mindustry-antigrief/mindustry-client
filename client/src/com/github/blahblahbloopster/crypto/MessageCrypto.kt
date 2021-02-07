package com.github.blahblahbloopster.crypto

import arc.Core
import arc.Events
import arc.graphics.Color
import arc.util.Log
import arc.util.serialization.Base64Coder
import mindustry.Vars
import mindustry.game.EventType
import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.EdDSAPublicKey
import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.time.Instant
import kotlin.math.abs

/** Provides the interface between [Crypto] and a [CommunicationSystem] */
class MessageCrypto {
    lateinit var communicationSystem: CommunicationSystem
    var keyPair: KeyPair? = null

    companion object {
        fun base64public(input: PublicKey): String {
            return Base64Coder.encode(Crypto.serializePublic(input as EdDSAPublicKey)).toString()
        }

        fun base64public(input: String): PublicKey? {
            return try {
                Crypto.deserializePublic(Base64Coder.decode(input))
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        fun base64private(input: String): PrivateKey? {
            return try {
                Crypto.deserializePrivate(Base64Coder.decode(input))
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        fun base64private(input: PrivateKey): String {
            return Base64Coder.encode(Crypto.serializePrivate(input as EdDSAPrivateKey)).toString()
        }
    }

    fun init(communicationSystem: CommunicationSystem) {
        this.communicationSystem = communicationSystem


        if (Core.settings.dataDirectory.child("privateKey.txt").exists() && Core.settings.dataDirectory.child("publicKey.txt").exists()) {
            keyPair = KeyPair(
                    base64public(Core.settings.dataDirectory.child("publicKey.txt").readString())!!,
                    base64private(Core.settings.dataDirectory.child("privateKey.txt").readString())!!
            )
            Log.info("Loaded keypair")
            val original = Core.files.absolute("/home/max/.local/share/Mindustry/publicKey").readBytes()

            Events.on(EventType.SendChatMessageEvent::class.java) { event ->
                keyPair ?: return@on
                sign(event.message, keyPair!!.private)
            }
        }

        var player = Triple<Int, Long, String>(0, 0, "")  // Maps player ID to last sent message
        var received = Triple<Int, Long, ByteArray>(0, 0, ByteArray(0))  // Maps player ID to last sent message
        Events.on(EventType.PlayerChatEventClient::class.java) { event ->
            player = Triple((event.player ?: return@on).id, Instant.now().epochSecond, event.message)
            check(player, received)
        }
        communicationSystem.listeners.add { input, sender ->
            received = Triple(sender, Instant.now().epochSecond, input)
            check(player, received)
        }
    }

    fun base64public(): String? {
        keyPair?: return null
        return Base64Coder.encode(Crypto.serializePublic(keyPair!!.public as EdDSAPublicKey)).toString()
    }

    fun base64private(): String? {
        keyPair?: return null
        return Base64Coder.encode(Crypto.serializePrivate(keyPair!!.private as EdDSAPrivateKey)).toString()
    }

    /** Checks the validity of a message given two triples, see above. */
    private fun check(player: Triple<Int, Long, String>, received: Triple<Int, Long, ByteArray>) {
        if (player.first == 0 || player.second == 0L || player.third == "") return
        if (received.first == 0 || received.second == 0L || received.third.isEmpty()) return
        val time = Instant.now().epochSecond
        if (abs(player.second - time) > 3 || abs(received.second - time) > 3) {
            return
        }

        if (player.first != received.first) {
            return
        }

        for (key in KeyFolder.keys) {
            val match = verify(player.third, player.first, received.third, key.key)
            if (match) {
                val message = Vars.ui.chatfrag.messages.findLast { it.message == player.third } ?: return
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
    fun sign(message: String, key: PrivateKey) {
        val time = Instant.now().epochSecond
        val out = ByteBuffer.allocate(Crypto.signatureSize + 12)
        out.putLong(time)
        out.putInt(communicationSystem.id)
        val signature = Crypto.sign(stringToSendable(message, communicationSystem.id, time), key as EdDSAPrivateKey)
        out.put(signature)
        communicationSystem.send(out.array())
    }

    /** Verifies an incoming message. */
    fun verify(message: String, sender: Int, signature: ByteArray, key: PublicKey): Boolean {
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
            val valid = Crypto.verify(original, signatureBytes, key as EdDSAPublicKey)
            abs(Instant.now().epochSecond - time) < 3 &&
                    sender == senderId &&
                    valid
        } catch (ignored: Exception) {
            false
        }
    }
}
