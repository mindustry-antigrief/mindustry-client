package mindustry.client.communication

import arc.util.*
import mindustry.*
import mindustry.client.utils.*
import mindustry.gen.*

object PluginCommunicationSystem : CommunicationSystem() {
    override val listeners: MutableList<(input: ByteArray, sender: Int) -> Unit> = mutableListOf()
    override val id = Vars.player.id
    override val MAX_LENGTH = 5625 // 22kbps
    override val RATE = 15f // 250ms

    override fun send(bytes: ByteArray) {
        Call.serverPacketReliable("fooTransmission", bytes.base64())
    }

    override fun init() {
        Vars.netClient.addPacketHandler("fooTransmission") { data ->
            val sender = Strings.parseInt(data.substringBefore(' '))
            if (!Groups.player.contains { it.id == sender }) return@addPacketHandler
            val input = data.substringAfter(' ').base64() ?: return@addPacketHandler
            Vars.player.locale
            listeners.forEach {
                it(input, sender)
            }
        }
    }
}