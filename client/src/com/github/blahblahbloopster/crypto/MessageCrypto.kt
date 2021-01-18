package com.github.blahblahbloopster.crypto

import arc.Core
import arc.Events
import arc.graphics.Color
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
    val validKeys = mutableListOf<PublicKey>()
    var keyPair: KeyPair? = null

    fun init(communicationSystem: CommunicationSystem) {
        this.communicationSystem = communicationSystem
        validKeys.add(Crypto.deserializePublic(Core.files.internal("fooKey").readBytes()))

        if (Core.settings.dataDirectory.child("privateKey").exists() && Core.settings.dataDirectory.child("publicKey").exists()) {
            keyPair = KeyPair(
                    Crypto.deserializePublic(Core.settings.dataDirectory.child("publicKey").readBytes()),
                    Crypto.deserializePrivate(Core.settings.dataDirectory.child("privateKey").readBytes())
            )
            println("Loaded keypair")

            Events.on(EventType.SendChatMessageEvent::class.java) { event ->
                sign(event.message, (keyPair ?: return@on).private)
            }
        }

        var player = Triple<Int, Long, String>(0, 0, "")  // Maps player ID to last sent message
        var recieved = Triple<Int, Long, ByteArray>(0, 0, ByteArray(0))  // Maps player ID to last sent message
        Events.on(EventType.PlayerChatEventClient::class.java) { event ->
            player = Triple((event.player ?: return@on).id, Instant.now().epochSecond, event.message)
            println("Got message from ${player.first} at ${player.second} with content ${player.third}")
            check(player, recieved)
        }
        communicationSystem.listeners.add { input, sender ->
            recieved = Triple(sender, Instant.now().epochSecond, input)
            println("Got signature from ${recieved.first} at ${recieved.second} with content ${recieved.third.contentToString()}")
            check(player, recieved)
        }
    }

    private fun check(player: Triple<Int, Long, String>, recieved: Triple<Int, Long, ByteArray>) {
        if (player.first == 0 || player.second == 0L || player.third == "") return
        if (recieved.first == 0 || recieved.second == 0L || recieved.third.isEmpty()) return

        val time = Instant.now().epochSecond
        if (abs(player.second - time) > 3 || abs(recieved.second - time) > 3) {
            println("Too old ${abs(player.second - time)}  ${abs(recieved.second - time)}")
            return
        }

        if (player.first != recieved.first) {
            println("IDs don't match")
            return
        }

        if (validKeys.map { verify(player.third, player.first, recieved.third, it) }.contains(true)) {
            println("Valid!")
            Vars.ui.chatfrag.messages.findLast { it.message == player.third }?.backgroundColor = Color.green.cpy().mul(0.75f)
        } else {
            println("Invalid signature")
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
            println("Incorrect size")
            return false
        }
        val buffer = ByteBuffer.wrap(signature)
        val time = buffer.long
        val senderId = buffer.int
        val signatureBytes = buffer.array()

        val original = stringToSendable(message, sender, time)

        if (sender != senderId) {
            println("$sender doesn't match $senderId!")
        }

        return try {
            abs(Instant.now().epochSecond - time) < 3 &&
                    sender == senderId &&
                    Crypto.verify(original, signatureBytes, key as EdDSAPublicKey)
        } catch (ignored: Exception) {
            false
        }
    }
}
