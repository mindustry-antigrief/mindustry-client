package com.github.blahblahbloopster.crypto

import arc.Events
import mindustry.Vars
import mindustry.client.Client
import mindustry.game.EventType
import mindustry.world.blocks.logic.MessageBlock

class MessageBlockCommunicationSystem : CommunicationSystem {
    override val listeners: MutableList<(input: ByteArray, sender: Int) -> Unit> = mutableListOf()
    override val id: Int get() = run {
        Vars.player ?: return 0
        Vars.player.unit() ?: return 0
        return Vars.player.unit().id
    }
    private val messageBlockIndex = mutableSetOf<Int>()

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
                return@on
            }

            listeners.forEach {
                it.invoke(bytes, id)
            }
        }
    }

    override fun send(bytes: ByteArray) {
//        val output = Base256Coder.encode(bytes)
//        var i = 0
        for (block in Vars.world.tiles) {
//            println("Block: $block")
            if (block == null || block.block() !is MessageBlock) {
//                Client.messageBlockPositions.remove(block)
                continue
            }
            val build: MessageBlock.MessageBuild = block.build as MessageBlock.MessageBuild
            if (!build.message.startsWith(Client.messageCommunicationPrefix)) {
                println("Doesn't match index")
                continue
            }

            build.configure(Client.messageCommunicationPrefix)
            println("Configured ${(Client.messageCommunicationPrefix + Base256Coder.encode(bytes)).length}")
            break
        }
    }
}
