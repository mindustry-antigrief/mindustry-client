package mindustry.client.communication

import arc.util.*
import mindustry.*
import mindustry.client.*
import mindustry.client.crypto.Signatures
import mindustry.client.navigation.*
import mindustry.client.utils.*
import mindustry.gen.Player
import java.security.cert.*
import kotlin.random.*
import java.math.BigInteger
import java.time.Instant

class CommandTransmission : Transmission {

    val type: Commands?
    var certSN: ByteArray
    var signature: ByteArray
    var additionalInfo: ByteArray
    var destination: Int
    var timestamp: Instant

    constructor(type: Commands?, cert: X509Certificate, destination: Player, additionalInfo: ByteArray = byteArrayOf()) {
        this.type = type
        this.certSN = cert.serialNumber.toByteArray()
        id = Random.nextLong()
        signature = ByteArray(Signatures.SIGNATURE_LENGTH)
        this.additionalInfo = additionalInfo
        this.destination = destination.id
        timestamp = Main.ntp.instant()
        sign()
    }

    override val secureOnly: Boolean = false

    companion object {
        var lastStopTime : Long = 0
    }
    enum class Commands(val builtinOnly: Boolean = false, val lambda: (CommandTransmission) -> Unit) {
        STOP_PATH(false, {
            val cert = Main.keyStorage.findTrusted(BigInteger(it.certSN))!!
            if (Navigation.currentlyFollowing != null && Time.timeSinceMillis(lastStopTime) > Time.toMinutes * 1 || Main.keyStorage.builtInCerts.contains(cert)) { // FINISHME: Scale time with number of requests or something?
                lastStopTime = Time.millis()
                val oldPath = Navigation.currentlyFollowing
                Vars.ui.showCustomConfirm("Pathing Stopped",
                    "[accent]${Main.keyStorage.aliasOrName(cert)}[white] has stopped your pathing. Would you like to undo this and continue pathing?",
                    "Continue Pathing", "Stop Pathing", { Navigation.follow(oldPath) }, {})
                Navigation.stopFollowing()
            }
        }),

        UPDATE(true, {
            if (!Main.keyStorage.builtInCerts.contains(Main.keyStorage.cert())) {
                val cert = Main.keyStorage.findTrusted(BigInteger(it.certSN))!!
                Vars.becontrol.checkUpdate({ Vars.becontrol.actuallyDownload(Main.keyStorage.aliasOrName(cert)) }, "mindustry-antigrief/mindustry-client-v7-builds")
            }
        })
    }

    override var id: Long

    constructor(input: ByteArray, id: Long) {
        val buf = input.buffer()
        type = Commands.values().getOrNull(buf.int)
        signature = buf.bytes(Signatures.SIGNATURE_LENGTH)
        certSN = buf.bytes(buf.int)
        additionalInfo = buf.bytes(buf.int)
        destination = buf.int
        timestamp = buf.long.toInstant()
        this.id = id
    }

    override fun serialize() = (type?.ordinal ?: 0).toBytes() + signature + certSN.size.toBytes() + certSN + additionalInfo.size.toBytes() + additionalInfo + destination.toBytes() + timestamp.epochSecond.toBytes()

    private fun toSignable() = (type?.ordinal ?: 0).toBytes() + certSN + additionalInfo + id.toBytes() + timestamp.epochSecond.toBytes()

    fun sign() {
        signature = Main.signatures.sign(toSignable()) ?: ByteArray(Signatures.SIGNATURE_LENGTH)
        certSN = Main.keyStorage.cert()?.serialNumber?.toByteArray() ?: byteArrayOf()
    }

    fun verify(): Boolean {
        type ?: return false
        if (timestamp.age() > Signatures.SIGNATURE_EXPIRY_SECONDS) return false  // replay attacks are bad
        val res = Main.signatures.verify(toSignable(), signature, certSN)
        if (type.builtinOnly) {
            res.second ?: return false
            return (res.first == Signatures.VerifyResult.VALID) && Main.keyStorage.builtInCerts.any { it.encoded.contentEquals(res.second!!.encoded) }
        } else {
            return res.first == Signatures.VerifyResult.VALID
        }
    }
}
