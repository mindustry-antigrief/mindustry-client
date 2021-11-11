package mindustry.client.communication

import arc.util.*
import mindustry.*
import mindustry.client.*
import mindustry.client.crypto.Signatures
import mindustry.client.navigation.*
import mindustry.client.utils.*
import java.security.cert.*
import kotlin.random.*

class CommandTransmission : Transmission {

    val type: Commands?
    var certSN: ByteArray
    var signature: ByteArray

    constructor(type: Commands?, certSN: ByteArray, signature: ByteArray) {
        this.type = type
        this.certSN = certSN
        this.signature = signature
        this.id = Random.nextLong()
    }

    override val secureOnly: Boolean = false

    companion object {
        var lastStopTime : Long = 0
    }
    enum class Commands(val builtinOnly: Boolean = false, val lambda: (CommandTransmission, X509Certificate) -> Unit) {
        STOP_PATH(false, { _, cert ->
            if (Navigation.currentlyFollowing != null && Time.timeSinceMillis(lastStopTime) > Time.toMinutes * 1 || Main.keyStorage.builtInCerts.contains(cert)) { // FINISHME: Scale time with number of requests or something?
                lastStopTime = Time.millis()
                val oldPath = Navigation.currentlyFollowing
                Vars.ui.showCustomConfirm("Pathing Stopped",
                    "[accent]${Main.keyStorage.aliasOrName(cert)}[white] has stopped your pathing. Would you like to undo this and continue pathing?",
                    "Continue Pathing", "Stop Pathing", { Navigation.follow(oldPath) }, {})
                Navigation.stopFollowing()
            }
        }),

        UPDATE(true, { _, cert ->
            Vars.becontrol.checkUpdate({ Vars.becontrol.actuallyDownload(Main.keyStorage.aliasOrName(cert)) }, "mindustry-antigrief/mindustry-client-v7-builds")
        })
    }

    override var id: Long

    constructor(input: ByteArray, id: Long) {
        val buf = input.buffer()
        type = Commands.values().getOrNull(buf.int)
        signature = buf.bytes(Signatures.SIGNATURE_LENGTH)
        certSN = buf.remainingBytes()
        this.id = id
    }

    override fun serialize() = (type?.ordinal ?: 0).toBytes() + signature + certSN

    private fun toSignable() = (type?.ordinal ?: 0).toBytes() + certSN

    fun sign() {
        signature = Main.signatures.sign(toSignable()) ?: ByteArray(Signatures.SIGNATURE_LENGTH)
        certSN = Main.keyStorage.cert()?.serialNumber?.toByteArray() ?: byteArrayOf()
    }
}
