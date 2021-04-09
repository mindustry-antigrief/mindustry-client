package mindustry.client.crypto

import arc.*
import arc.util.*
import mindustry.*
import mindustry.client.*
import mindustry.content.*
import mindustry.game.*
import mindustry.gen.*
import mindustry.world.blocks.logic.*
import java.io.*

object MessageBlockCommunicationSystem : CommunicationSystem() {
    override val listeners: MutableList<(input: ByteArray, sender: Int) -> Unit> = mutableListOf()
    override val id: Int get() = run {
        Vars.player ?: return -1
        return Vars.player.id
    }
    override val MAX_LENGTH = Base32768Coder.availableBytes((Blocks.message as MessageBlock).maxTextLength - ClientVars.MESSAGE_BLOCK_PREFIX.length)
    override val RATE: Float = 30f // 500ms

    /** Initializes listeners. */
    override fun init() {
        Events.on(EventType.ConfigEvent::class.java) { event ->
            event ?: return@on
            event.tile ?: return@on
            event.value ?: return@on
            if (event.tile.block !is MessageBlock) return@on

            val message = event.value as String
            if (!message.startsWith(ClientVars.MESSAGE_BLOCK_PREFIX)) return@on

            val id = if (event.player == null) -1 else event.player.id

            val bytes: ByteArray
            try {
                bytes = Base32768Coder.decode(message.removePrefix(ClientVars.MESSAGE_BLOCK_PREFIX))
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
            val build = tile.build as? MessageBlock.MessageBuild ?: continue // If it isn't a message block with the prefix, continue
            if (!build.message.startsWith(ClientVars.MESSAGE_BLOCK_PREFIX)) continue

            Call.tileConfig(Vars.player, build, ClientVars.MESSAGE_BLOCK_PREFIX + Base32768Coder.encode(bytes))
            return
        }
        throw IOException() // Throws an exception when no valid block is found
    }
}
