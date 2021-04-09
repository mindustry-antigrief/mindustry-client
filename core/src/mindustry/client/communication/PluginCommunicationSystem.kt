package mindustry.client.communication

import mindustry.client.crypto.CommunicationSystem
import mindustry.Vars
import mindustry.net.Net
import mindustry.net.Packets

object PluginCommunicationSystem : CommunicationSystem() {
    override val listeners: MutableList<(input: ByteArray, sender: Int) -> Unit> = mutableListOf()

    override val id: Int get() {
        Vars.player ?: return -1
        return Vars.player.id
    }

    override val MAX_LENGTH = 1024 // 1 KiB per packet

    override val RATE = 5f  // Once every 5 frames, 12 KiB/s

    override fun init() {
        Vars.net.handleClient(Packets.ClientNetworkPacket::class.java) { packet ->
            if (packet.sender == -1) return@handleClient
            listeners.forEach { it(packet.content, packet.sender) }
        }
    }

    override fun send(bytes: ByteArray) {
        Vars.net.send(Packets.ClientNetworkPacket(bytes), Net.SendMode.udp)
    }
}
