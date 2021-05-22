package mindustry.client.communication

import arc.*
import arc.struct.*
import mindustry.*
import mindustry.client.*
import mindustry.client.crypto.*
import mindustry.client.utils.*
import mindustry.content.*
import mindustry.game.*
import mindustry.gen.*
import mindustry.logic.*
import mindustry.world.blocks.logic.*
import java.io.*

object MessageBlockCommunicationSystem : CommunicationSystem() {
    override val listeners: MutableList<(input: ByteArray, sender: Int) -> Unit> = mutableListOf()
    override val id: Int get() = run {
        Vars.player ?: return -1
        return Vars.player.id
    }
    private var logicAvailable = false
    override val MAX_LENGTH get() = if (logicAvailable) Base32768Coder.availableBytes((LExecutor.maxInstructions - 3) * MAX_PRINT_LENGTH) else
        Base32768Coder.availableBytes((Blocks.message as MessageBlock).maxTextLength - ClientVars.MESSAGE_BLOCK_PREFIX.length)
    override val RATE: Float = 30f // 500ms

    private const val MAX_PRINT_LENGTH = 34
    const val LOGIC_PREFIX = "end\nprint \"client networking, do not edit/remove\""

    fun findProcessor(): LogicBlock.LogicBuild? {
        for (build in Groups.build) {
            val b = build as? LogicBlock.LogicBuild ?: continue
            if (b.team == Vars.player.team() && b.code.startsWith(LOGIC_PREFIX)) {
                logicAvailable = true
                return b
            }
        }
        logicAvailable = false
        return null
    }

    fun findMessage(): MessageBlock.MessageBuild? {
        for (tile in Vars.world.tiles) {
            val build = tile.build as? MessageBlock.MessageBuild ?: continue
            if (build.team != Vars.player.team() || !build.message.startsWith(ClientVars.MESSAGE_BLOCK_PREFIX)) continue
            return build
        }
        return null
    }

    private fun logicEvent(event: EventType.ConfigEvent) {
        event.tile ?: return
        event.value ?: return
        event.player ?: return
        if (event.tile.block !is LogicBlock) return
        logicAvailable = true

        val message = LogicBlock.decompress(event.value as? ByteArray ?: return)
        if (!message.startsWith(LOGIC_PREFIX)) return

        val id = event.player.id

        val bytes: ByteArray
        try {
            bytes = Base32768Coder.decode(
                message.removePrefix(ClientVars.MESSAGE_BLOCK_PREFIX + "\n")
                    .split("\n").joinToString("") {
                        it.removePrefix("print \"").removeSuffix("\"")
                    }
            ).run { sliceArray(0 until size - 1) }  // todo wtf

        } catch (exception: Exception) {
            return
        }

        listeners.forEach {
            it.invoke(bytes, id)
        }
    }

    private fun messageEvent(event: EventType.ConfigEvent) {
        event.tile ?: return
        event.value ?: return
        if (event.tile.block !is MessageBlock) return

        val message = event.value as? String ?: return // Some modded blocks use non strings
        if (!message.startsWith(ClientVars.MESSAGE_BLOCK_PREFIX)) return

        val id = if (event.player == null) -1 else event.player.id

        val bytes: ByteArray
        try {
            bytes = Base32768Coder.decode(message.removePrefix(ClientVars.MESSAGE_BLOCK_PREFIX))
        } catch (exception: Exception) {
            return
        }

        listeners.forEach {
            it.invoke(bytes, id)
        }
    }

    /** Initializes listeners. */
    override fun init() {
        Events.on(EventType.ConfigEvent::class.java) { event ->
            event ?: return@on

            logicEvent(event)
            messageEvent(event)
        }
    }

    private fun sendMessageBlock(bytes: ByteArray) {
        val message = findMessage() ?: throw IOException() // Throws an exception when no valid block is found
        Call.tileConfig(Vars.player, message, ClientVars.MESSAGE_BLOCK_PREFIX + Base32768Coder.encode(bytes))
    }

    private fun sendLogic(bytes: ByteArray) {
        val processor = findProcessor() ?: throw IOException("No matching processor found!") // Throws an exception when no valid block is found
        val value = bytes.plus(12).base32678().chunked(MAX_PRINT_LENGTH).joinToString("\n", prefix = LOGIC_PREFIX + "\n") { "print \"$it\"" }.removeSuffix("\n")
        Call.tileConfig(Vars.player, processor, LogicBlock.compress(value, Seq()))
    }

    override fun send(bytes: ByteArray) {
        try {
            sendLogic(bytes)
        } catch (e: IOException) {
            sendMessageBlock(bytes)
        }
    }
}
