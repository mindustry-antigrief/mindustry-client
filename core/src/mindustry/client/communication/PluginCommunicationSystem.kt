package mindustry.client.communication

import arc.util.*
import mindustry.*
import mindustry.gen.*

object PluginCommunicationSystem : CommunicationSystem() {
    override val listeners: MutableList<(input: ByteArray, sender: Int) -> Unit> = mutableListOf()
    override val id = Vars.player.id
    override val MAX_LENGTH = 5625 // 22kbps
    override val RATE = 15f // 250ms

    override fun send(bytes: ByteArray) {
        Call.serverPacketReliable("fooTransmission", Base32768Coder.encode(bytes))
    }

    override fun init() {
        Vars.netClient.addPacketHandler("fooTransmission") { data ->
            val sender = Strings.parseInt(data.substringBefore(' '))
            if (sender == Vars.player.id || !Groups.player.contains { it.id == sender }) return@addPacketHandler
            val input = Base32768Coder.decode(data.substringAfter(' '))
            listeners.forEach {
                it(input, sender)
            }
        }
    }
}