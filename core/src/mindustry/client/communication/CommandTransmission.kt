package mindustry.client.communication

import arc.util.*
import mindustry.*
import mindustry.client.*
import mindustry.client.navigation.*
import mindustry.client.utils.*
import java.security.cert.*
import kotlin.random.*

class CommandTransmission(val type: Commands?) : Transmission {

    override val secureOnly: Boolean = true

    companion object {
        var lastStopTime : Long = 0
    }
    enum class Commands(val builtinOnly: Boolean = false, val lambda: (CommandTransmission, X509Certificate) -> Unit) {
        STOP_PATH(false, { _, cert ->
            if (Navigation.currentlyFollowing != null && Time.timeSinceMillis(lastStopTime) > Time.toMinutes * 1 || Main.keyStorage.builtInCerts.contains(cert)) { // FINISHME: Scale time with number of requests or something?
                lastStopTime = Time.millis()
                var oldPath = Navigation.currentlyFollowing
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

    override var id = Random.nextLong()

    constructor(input: ByteArray, id: Long) : this(Commands.values().getOrNull(input.buffer().int)) {
        this.id = id
    }

    override fun serialize() = type?.ordinal?.toBytes() ?: 0.toBytes()
}
