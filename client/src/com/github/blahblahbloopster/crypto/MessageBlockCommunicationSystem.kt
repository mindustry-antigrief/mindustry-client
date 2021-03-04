package com.github.blahblahbloopster.crypto

import arc.*
import mindustry.*
import mindustry.client.*
import mindustry.content.Blocks
import mindustry.game.*
import mindustry.gen.*
import mindustry.world.blocks.logic.*

class MessageBlockCommunicationSystem : CommunicationSystem {
    override val listeners: MutableList<(input: ByteArray, sender: Int) -> Unit> = mutableListOf()
    override val id: Int get() = run {
        Vars.player ?: return -1
        return Vars.player.id
    }
    override val MAX_LENGTH = Base32768Coder.availableBytes((Blocks.message as MessageBlock).maxTextLength - Client.messageCommunicationPrefix.length)
    override val RATE = 500L

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
            // if it isn't a message block or is null continue
            val build = tile.build as? MessageBlock.MessageBuild ?: continue

            if (!build.message.startsWith(Client.messageCommunicationPrefix)) {
                continue
            }

            Call.tileConfig(Vars.player, build, Client.messageCommunicationPrefix + Base32768Coder.encode(bytes))
            break
        }
    }
}
