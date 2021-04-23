package mindustry.client.crypto

import arc.*
import arc.graphics.*
import arc.util.*
import arc.util.serialization.*
import mindustry.*
import mindustry.client.*
import mindustry.client.communication.*
import mindustry.client.ui.*
import mindustry.client.utils.*
import mindustry.core.*
import mindustry.game.*
import mindustry.gen.*
import java.nio.*
import java.time.*
import java.util.zip.*

/** Provides the interface between [Crypto] and a [CommunicationSystem], and handles some UI stuff. */
class MessageCrypto {
    lateinit var keyQuad: KeyQuad
    lateinit var communicationClient: Packets.CommunicationClient

    private var player: PlayerTriple? = null     // Maps player ID to last sent message
    private var received: ReceivedTriple? = null // Maps player ID to last sent message
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

        class EncryptedMessageEvent(val sender: Int, val senderKey: KeyHolder, val message: String?, val senderName: String) : MessageCryptoEvent()

        fun base64public(input: String): PublicKeyPair? {
            return try {
                PublicKeyPair(Base64Coder.decode(input))
            } catch (e: Exception) { null }
        }

        /** Uses [Core.settings], do not run in tests. */
        fun generateKey() {
            val quad = Crypto.generateKeyQuad()
            Core.settings.dataDirectory.child("key.txt").writeString((Base64Coder.encode(quad.serialize()).concatToString()), false)
            Main.messageCrypto.keyQuad = quad
        }
    }

    data class PlayerTriple(val id: Int, val time: Long, val message: String)

    data class ReceivedTriple(val id: Int, val time: Long, val transmission: SignatureTransmission)

    fun init(communicationClient: Packets.CommunicationClient) {
        this.communicationClient = communicationClient
        communicationClient.addListener(::handle)

        try { // Load key, generate if it doesn't exist
            if (Core.app.isDesktop) {
                if (!Core.settings.dataDirectory.child("key.txt").exists()) generateKey()
                else keyQuad = KeyQuad(Base64Coder.decode(Core.settings.dataDirectory.child("key.txt").readString()))
                Log.info("Loaded keypair")
            }
        } catch (ignored: Exception) {}

        Events.on(EventType.SendChatMessageEvent::class.java) { event ->
            if (ClientVars.signMessages) sign(event.message, keyQuad)
        }
        Events.on(EventType.PlayerChatEventClient::class.java) { event ->
            player = PlayerTriple((event.player ?: return@on).id, Instant.now().epochSecond, event.message)
            check(player, received)
        }

        if (Core.app?.isDesktop == true) {
            listeners.add {
                if (it is SignatureEvent && it.valid && it.message != null) {
                    val message = Vars.ui?.chatfrag?.messages?.find { msg -> msg.message.contains(it.message) }
                    message?.backgroundColor = Color.green.cpy().mul(if (it.senderKey?.official == true) .75f else if (Core.settings.getBool("highlightcryptomsg")) 0.4f else 0f)
                    message?.sender = NetClient.colorizeName(it.sender, it.senderKey?.name ?: return@add)
                    message?.prefix = Iconc.ok + " "
                    message?.format()
                } else if (it is EncryptedMessageEvent && it.message != null) {
                    Vars.ui?.chatfrag?.addMessage(it.message,
                            NetClient.colorizeName(it.sender, it.senderName),
                            Color.blue.cpy().mul(if (it.senderKey.official) .75f else if (Core.settings.getBool("highlightcryptomsg")) 0.4f else 0f),
                            "${Iconc.lock} ")
                }
            }
        }
    }

    fun base64public(): String {
        return Base64Coder.encode(PublicKeyPair(keyQuad).serialize()).concatToString()
    }

    /** Checks the validity of a message given two triples, see above. */
    private fun check(player: PlayerTriple?, received: ReceivedTriple?) {
        player ?: return
        received ?: return

        fun event(sender: Int = -1, keyHolder: KeyHolder? = null, message: String? = null, valid: Boolean = false) {
            fire(SignatureEvent(sender, keyHolder, message, valid))
        }
        if (Vars.player?.id == player.id || Vars.player?.id == received.id) return

        if (player.time.toInstant().age() > 15 || received.time.toInstant().age() > 15) {
            event(player.id, message = player.message)
            return
        }

        if (player.id != received.id) {
            event(message = player.message)
            return
        }

        for (key in keys) {
            val match = verify(player.message, received.id, received.transmission.signature, key.keys, received.transmission.time.epochSecond)
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
        val time = Instant.now()
        val signature = Crypto.sign(stringToSendable(message, communicationClient.communicationSystem.id, time.epochSecond), key.edPrivateKey)
        communicationClient.send(SignatureTransmission(signature, time))
    }

    fun encrypt(message: String, destination: KeyHolder) {
        val time = Instant.now().epochSecond
        val compressor = DeflaterInputStream(message.toByteArray().inputStream())
        val encoded = compressor.readBytes()
        val plaintext = ByteBuffer.allocate(encoded.size + Long.SIZE_BYTES + Byte.SIZE_BYTES)
        plaintext.putLong(time)
        plaintext.put(ENCRYPTION_VALIDITY)
        plaintext.put(encoded)
        val ciphertext = destination.crypto.encrypt(plaintext.array())

        communicationClient.send(EncryptedMessageTransmission(ciphertext), { if (Core.app?.isDesktop == true) Toast(3f).add("@client.encryptedsuccess") }, { if (Core.app?.isDesktop == true) Toast(3f).add("@client.nomessageblock") })
    }

    private fun handle(input: Transmission, sender: Int) {
        try {
            when (input) {
                is SignatureTransmission -> {
                    received = ReceivedTriple(sender, Instant.now().epochSecond, input)
                    check(player, received)
                }
                is EncryptedMessageTransmission -> {
                    for (key in keys) {
                        val crypto = key.crypto
                        try {
                            val decoded = crypto.decrypt(input.ciphertext)

                            val buffer = ByteBuffer.wrap(decoded)
                            val timeSent = buffer.long
                            val validity = buffer.get()
                            val plaintext = buffer.remainingBytes()

                            if (validity != ENCRYPTION_VALIDITY) continue

                            if (timeSent.toInstant().age() > 3 || input.timeSent.age() > 3) continue

                            val str = plaintext.inflate().decodeToString()

                            fire(
                                EncryptedMessageEvent(
                                    sender,
                                    key,
                                    str,
                                    if (sender == communicationClient.communicationSystem.id && Core.app?.isDesktop == true) Vars.player.name else key.name
                                )
                            )
                        } catch (ignored: Exception) {}
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
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

        return age < 15 && validSignature
    }
}
