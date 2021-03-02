package com.github.blahblahbloopster.crypto

import arc.Events
import arc.util.Time
import arc.util.Timer
import mindustry.Vars
import mindustry.client.Client
import mindustry.content.Blocks
import mindustry.game.EventType
import mindustry.gen.Call
import mindustry.world.blocks.logic.MessageBlock
import kotlin.random.Random

class MessageBlockCommunicationSystem : CommunicationSystem {
    override val listeners: MutableList<(input: ByteArray, sender: Int) -> Unit> = mutableListOf()
    override val id: Int get() = run {
        Vars.player ?: return -1
        return Vars.player.id
    }
//    private val incomingMessages = mutableListOf<IncomingMessage>()
//
//    private class IncomingMessage(val totalTransmissions: Int, val sender: Int, val transmissionId: Int) {
//        val received = MutableList<String?>(totalTransmissions) { null }
//        private var current = 0
//
//        fun onReceive(messageNoPrefix: String, senderId: Int): Boolean {
//            if (messageNoPrefix.length > 12 && senderId == sender) {
//                val packetIndex = messageNoPrefix.substring(0 until 3).toIntOrNull() ?: return false
//                val totalCount = messageNoPrefix.substring(3 until 6).toIntOrNull() ?: return false
//                val id = messageNoPrefix.substring(6 until 12).toIntOrNull() ?: return false
//
//                if (packetIndex < 0 || packetIndex > totalTransmissions) return false
//                if (totalCount < 0) return false
//                if (id != transmissionId) return false
//
//                val content = messageNoPrefix.removeRange(0 until 12)
//                if (totalCount == totalTransmissions && packetIndex == current) {
//                    received[current] = content
//                    current++
//                    return true
//                }
//            }
//            return false
//        }
//
//        fun contentIfReady(): String? {
//            if (received.size == totalTransmissions) {
//                return received.joinToString("")
//            }
//            return null
//        }
//    }

    /** Initializes listeners. */
    override fun init() {
        Events.on(EventType.ConfigEvent::class.java) { event ->
            event ?: return@on
            event.tile ?: return@on
            event.value ?: return@on
            if (event.tile.block !is MessageBlock) return@on

            val message = event.value as String
            if (!message.startsWith(Client.messageCommunicationPrefix)) return@on

            val id = if (event.player == null) 0 else event.player.id

//            val match = incomingMessages.find { it.onReceive(message.removePrefix(Client.messageCommunicationPrefix), id) } ?: IncomingMessage()

            val bytes: ByteArray
            try {
                bytes = Base32768Coder.decode(message.removePrefix(Client.messageCommunicationPrefix))
            } catch (exception: Exception) {
                return@on
            }

            listeners.forEach {
                it.invoke(bytes, id)
            }
        }
    }

    override fun send(bytes: ByteArray) {
        for (tile in Vars.world.tiles) {
            if ((tile.build as? MessageBlock.MessageBuild)?.message?.startsWith(Client.messageCommunicationPrefix) == true) {
                Call.tileConfig(Vars.player, tile.build, Client.messageCommunicationPrefix + Base32768Coder.encode(bytes))
                break
            }
        }
    }
}
