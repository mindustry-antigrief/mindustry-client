package mindustry.client.communication

import java.security.cert.X509Certificate
import mindustry.client.navigation.Navigation
import mindustry.client.utils.buffer
import mindustry.client.utils.toBytes
import kotlin.random.Random

class CommandTransmission(val type: Commands?) : Transmission {

    override val secureOnly: Boolean = true

    enum class Commands(val builtinOnly: Boolean = false, val lambda: (CommandTransmission, X509Certificate) -> Unit) {
        STOP_PATH(false, { _, _ -> Navigation.stopFollowing() }),
        UPDATE(true, { _, _ -> /*FINISHME*/ })
    }

    override var id = Random.nextLong()

    constructor(input: ByteArray, id: Long) : this(Commands.values().getOrNull(input.buffer().int)) {
        this.id = id
    }

    override fun serialize() = type?.ordinal?.toBytes() ?: 0.toBytes()
}
