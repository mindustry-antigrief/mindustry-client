package com.github.blahblahbloopster.crypto

import arc.Core
import arc.Events
import arc.graphics.Color
import arc.util.Log
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
    private val validKeys = mutableListOf<PublicKey>()
    private var keyPair: KeyPair? = null

    fun init(communicationSystem: CommunicationSystem) {
        this.communicationSystem = communicationSystem
        validKeys.add(Crypto.deserializePublic(Core.files.internal("fooKey").readBytes()))  // load public key

        if (Core.settings.dataDirectory.child("privateKey").exists() && Core.settings.dataDirectory.child("publicKey").exists()) {
            keyPair = KeyPair(
                    Crypto.deserializePublic(Core.settings.dataDirectory.child("publicKey").readBytes()),
                    Crypto.deserializePrivate(Core.settings.dataDirectory.child("privateKey").readBytes())
            )
            Log.info("Loaded keypair")

            Events.on(EventType.SendChatMessageEvent::class.java) { event ->
                sign(event.message, (keyPair ?: return@on).private)
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

        val match = KeyFolder.keys.filter { verify(player.third, player.first, received.third, it.value) }
        val valid = match.any()
        val official = validKeys.map { verify(player.third, player.first, received.third, it) }.contains(true)
        if (valid) {
            val message = Vars.ui.chatfrag.messages.findLast { it.message == player.third } ?: return
            message.backgroundColor = Color.green.cpy().mul(if (official) 0.75f else 0.6f)
            message.sender += " ["
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
