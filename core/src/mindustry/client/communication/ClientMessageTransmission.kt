package mindustry.client.communication

import arc.graphics.*
import mindustry.*
import mindustry.client.*
import mindustry.client.crypto.*
import mindustry.client.crypto.Signatures.VerifyResult.*
import mindustry.client.utils.*
import mindustry.core.*
import mindustry.gen.*
import java.math.*
import java.nio.*
import java.security.cert.*
import java.time.*
import kotlin.random.*

class ClientMessageTransmission : Transmission {

    override var id = Random.nextLong()
    override val secureOnly: Boolean = false
    val sender: String  // not serialized
    private val senderID: Int  // not serialized
    val message: String
    val certSN: ByteArray?
    val signature: ByteArray?
    val timestamp: Instant
    val validity: Signatures.VerifyResult

    constructor(input: ByteArray, id: Long, senderID: Int) {
        this.id = id
        this.senderID = senderID
        val buf = input.buffer()
        message = buf.string
        certSN = buf.byteArray.run { if (isEmpty()) null else this }
        signature = buf.byteArray.run { if (isEmpty()) null else this }
        timestamp = buf.instant
        val res = verify()
        validity = res.second
        sender = res.first?.run { Main.keyStorage.aliasOrName(this) } ?: Groups.player.getByID(senderID).name
    }

    constructor(message: String) {
        sender = Main.keyStorage.cert()?.readableName ?: Vars.player.name
        senderID = Vars.player.id
        timestamp = Instant.now()
        this.message = message
        this.certSN = Main.keyStorage.cert()?.serialNumber?.toByteArray()
        this.signature = Main.signatures.sign(toSignable(senderID, message, timestamp))
        validity = VALID
    }

    companion object {
        private fun toSignable(senderID: Int, message: String, timestamp: Instant): ByteArray {
            val buf = ByteBuffer.allocate(Int.SIZE_BYTES + Int.SIZE_BYTES + message.encodeToByteArray().size + Long.SIZE_BYTES)
            buf.putInt(senderID)
            buf.putString(message)
            buf.putInstantSeconds(timestamp)
            return buf.array()
        }
    }

    override fun serialize(): ByteArray {
        val buf = ByteBuffer.allocate(Int.SIZE_BYTES + message.encodeToByteArray().size + Int.SIZE_BYTES + (certSN?.size ?: 0) + Int.SIZE_BYTES + (signature?.size ?: 0) + Long.SIZE_BYTES)
        buf.putString(message)
        buf.putByteArray(certSN ?: byteArrayOf())
        buf.putByteArray(signature ?: byteArrayOf())
        buf.putInstantSeconds(timestamp)
        return buf.array()
    }

    private fun verify(): Pair<X509Certificate?, Signatures.VerifyResult> {
        val cert = if (certSN != null) Main.keyStorage.findTrusted(BigInteger(certSN)) else null

        // if it's too old, it's invalid even if the cert is unknown or nonexistent
        if (timestamp.age() > Signatures.SIGNATURE_EXPIRY_SECONDS) return cert to INVALID

        // if the cert is unknown/nonexistent but timed correctly it's merely unknown
        cert ?: return null to UNKNOWN_CERT

        // if the cert is known, and it isn't signed, it's invalid
        if (signature == null) return cert to INVALID

        val signable = toSignable(senderID, message, timestamp)

        return cert to if (Signatures.rawVerify(signable, signature, cert.publicKey)) VALID else INVALID
    }

    fun addToChatfrag() {
        val background = when (validity) {
            VALID -> /* FINISHME: builtin check */ ClientVars.verified
            INVALID -> ClientVars.invalid
            UNKNOWN_CERT -> Color.darkGray
        }
        val prefix = "[accent]<[white]F[]>[] ${when (validity) { VALID -> Iconc.ok; INVALID -> Iconc.cancel; UNKNOWN_CERT -> "" }} ".replace("  ", " ") // No double spaces. Cursed

        val newMsg = NetClient.processCoords(message, true)
        Vars.ui.chatfrag.addMessage(newMsg, sender, background, prefix, newMsg).findCoords().findLinks()
    }

    override fun toString(): String {
        return "ClientMessageTransmission(id=$id, secureOnly=$secureOnly, sender='$sender', senderID=$senderID, message='$message', certSN=${certSN?.contentToString()}, signature=${signature?.contentToString()}, timestamp=$timestamp, validity=$validity)"
    }
}
