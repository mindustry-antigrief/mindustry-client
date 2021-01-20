package com.github.blahblahbloopster.crypto

import arc.Events
import arc.util.serialization.Base64Coder
import mindustry.Vars
import mindustry.client.Client
import mindustry.game.EventType
import mindustry.gen.Call
import mindustry.world.blocks.logic.MessageBlock

class MessageBlockCommunicationSystem : CommunicationSystem {
    override val listeners: MutableList<(input: ByteArray, sender: Int) -> Unit> = mutableListOf()
    override val id: Int get() = run {
        Vars.player ?: return 0
        return Vars.player.id
    }

    /** Initializes listeners. */
    override fun init() {
        Events.on(EventType.ConfigEvent::class.java) { event ->
            event ?: return@on
            event.tile ?: return@on
            event.tile.block ?: return@on
            if (event.tile.block !is MessageBlock) return@on

            val message = event.value as String
            if (!message.startsWith(Client.messageCommunicationPrefix)) return@on

            val id = if (event.player == null) 0 else event.player.id
            val bytes: ByteArray
            try {
                bytes = Base256Coder.decode(message.removePrefix(Client.messageCommunicationPrefix))!!
            } catch (exception: Exception) {
                exception.printStackTrace()
                return@on
            }

            listeners.forEach {
                it.invoke(bytes, id)
            }
        }
    }

    override fun send(bytes: ByteArray) {
        for (block in Vars.world.tiles) {
            if (block == null || block.block() !is MessageBlock) {
                continue
            }
            val build: MessageBlock.MessageBuild = block.build as MessageBlock.MessageBuild
            if (!build.message.startsWith(Client.messageCommunicationPrefix)) {
                continue
            }

            Call.tileConfig(Vars.player, block.build, Client.messageCommunicationPrefix + Base256Coder.encode(bytes))
            break
        }
    }
}
