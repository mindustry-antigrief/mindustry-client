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
        Vars.player.unit() ?: return 0
        return Vars.player.unit().id
    }

    override fun init() {
        println("AIOJOIFOIEOFIOIEFOOEIFOASFDACAAAA")
        Events.on(EventType.ConfigEvent::class.java) { event ->
            println("AAAAAAA")
            event ?: return@on
            event.tile ?: return@on
            event.tile.block ?: return@on
            if (event.tile.block !is MessageBlock) return@on

            val message = event.value as String
            if (!message.startsWith(Client.messageCommunicationPrefix)) return@on
            println("Getting something...")

            val id = if (event.player == null) 0 else event.player.id
            val bytes: ByteArray
            try {
                bytes = Base64Coder.decode(message.removePrefix(Client.messageCommunicationPrefix))!!
                println("Got ${bytes.contentToString()}")
            } catch (exception: Exception) {
                exception.printStackTrace()
                return@on
            }

            listeners.forEach {
                println("Running listener $it")
                it.invoke(bytes, id)
            }
        }
    }

    override fun send(bytes: ByteArray) {
        for (block in Vars.world.tiles) {
//            val block = Vars.world.tile(tile)
            if (block == null || block.block() !is MessageBlock) {
//                Client.messageBlockPositions.remove(tile)
                continue
            }
            val build: MessageBlock.MessageBuild = block.build as MessageBlock.MessageBuild
            if (!build.message.startsWith(Client.messageCommunicationPrefix)) {
                continue
            }

            Call.tileConfig(Vars.player, block.build, Client.messageCommunicationPrefix + String(Base64Coder.encode(bytes)))
            println("Sent ${bytes.contentToString()}")
            break
        }
    }
}
