package mindustry.client.communication

import arc.*
import arc.struct.*
import mindustry.*
import mindustry.client.*
import mindustry.client.utils.*
import mindustry.content.*
import mindustry.entities.*
import mindustry.game.*
import mindustry.gen.*
import mindustry.logic.*
import mindustry.world.blocks.logic.*
import java.io.*
import kotlin.math.*

object BlockCommunicationSystem : CommunicationSystem() {
    override val listeners: MutableList<(input: ByteArray, sender: Int) -> Unit> = mutableListOf()
    override val id: Int get() = run {
        Vars.player ?: return -1
        return Vars.player.id
    }
    private var logicAvailable = false
    private var messagesAvailable = false
    override val MAX_LENGTH get() = when {
        logicAvailable -> Base32768Coder.availableBytes(min(4000, (LExecutor.maxInstructions - 3) * MAX_PRINT_LENGTH))
        messagesAvailable -> Base32768Coder.availableBytes((Blocks.message as MessageBlock).maxTextLength - ClientVars.MESSAGE_BLOCK_PREFIX.length)
        else -> 512
    }
    override val RATE: Float = 30f // 500ms

    private const val MAX_PRINT_LENGTH = 34
    const val LOGIC_PREFIX = "end\nprint \"client networking, do not edit/remove\""

    init {
        BuildPlanCommunicationSystem  // make sure it adds the listeners
    }

    fun findProcessor(): LogicBlock.LogicBuild? { // FINISHME: are these new implementations actually faster than the old ones? They sure are cleaner
        val build = Units.findAllyTile(Vars.player.team(), Vars.player.x, Vars.player.y, Float.MAX_VALUE / 2) { tile ->
            val build = tile as? LogicBlock.LogicBuild ?: return@findAllyTile false
            build.code.startsWith(LOGIC_PREFIX)
        } as? LogicBlock.LogicBuild
        logicAvailable = build != null
        return build
    }

    fun findMessage(): MessageBlock.MessageBuild? {
        val build = Units.findAllyTile(Vars.player.team(), Vars.player.x, Vars.player.y, Float.MAX_VALUE / 2) { tile ->
            val build = tile as? MessageBlock.MessageBuild ?: return@findAllyTile false
            build.message.startsWith(ClientVars.MESSAGE_BLOCK_PREFIX)
        } as? MessageBlock.MessageBuild
        messagesAvailable = build != null
        return build
    }

    private fun logicEvent(event: EventType.ConfigEvent) {
        event.tile ?: return
        event.value ?: return
        event.player ?: return
        if (event.tile.block !is LogicBlock) return

        val message = LogicBlock.decompress(event.value as? ByteArray ?: return)
        if (!message.startsWith(LOGIC_PREFIX)) return
        logicAvailable = true

        val id = event.player.id

        val bytes: ByteArray
        try {
            bytes = Base32768Coder.decode(
                message.removePrefix(ClientVars.MESSAGE_BLOCK_PREFIX + "\n")
                    .split("\n").joinToString("") {
                        it.removePrefix("print \"").removeSuffix("\"")
                    }
            ).run { sliceArray(0 until size - 1) }  // FINISHME wtf

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
        messagesAvailable = true

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
            try {
                sendMessageBlock(bytes)
            } catch (e: IOException) {
                BuildPlanCommunicationSystem.send(bytes)
            }
        }
    }
}
