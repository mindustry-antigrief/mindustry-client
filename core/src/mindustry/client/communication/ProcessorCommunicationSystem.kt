package mindustry.client.communication

import arc.Events
import arc.struct.Seq
import mindustry.Vars
import mindustry.client.ClientVars
import mindustry.client.crypto.Base32768Coder
import mindustry.client.utils.base32678
import mindustry.game.EventType
import mindustry.gen.Call
import mindustry.gen.Groups
import mindustry.logic.LExecutor
import mindustry.world.blocks.logic.LogicBlock
import java.io.IOException

object ProcessorCommunicationSystem : CommunicationSystem() {
    override val listeners: MutableList<(input: ByteArray, sender: Int) -> Unit> = mutableListOf()

    override val id: Int
        get() = Vars.player.id

    private const val MAX_PRINT_LENGTH = 34
    override val MAX_LENGTH = Base32768Coder.availableBytes((LExecutor.maxInstructions - 3) * MAX_PRINT_LENGTH)
    override val RATE = MessageBlockCommunicationSystem.RATE
    const val PREFIX = "end\nprint \"client networking, do not edit/remove\""

    private fun findProcessor(): LogicBlock.LogicBuild? {
        for (build in Groups.build) {
            val b = build as? LogicBlock.LogicBuild ?: continue
            if (b.code.startsWith(PREFIX)) {
                return b
            }
        }
        return null
    }

    override fun send(bytes: ByteArray) {
        val processor = findProcessor() ?: throw IOException("No matching processor found!")
        val value = bytes.base32678().chunked(MAX_PRINT_LENGTH).joinToString("\n", prefix = PREFIX) { "print \"it\"" }
        Call.tileConfig(Vars.player, processor, LogicBlock.compress(value, Seq()))
    }

    override fun init() {
        Events.on(EventType.ConfigEvent::class.java) { event ->
            event ?: return@on
            event.tile ?: return@on
            event.value ?: return@on
            if (event.tile.block !is LogicBlock) return@on

            val message = LogicBlock.decompress(event.value as ByteArray)
            if (!message.startsWith(PREFIX)) return@on

            val id = if (event.player == null) -1 else event.player.id

            val bytes: ByteArray
            try {
                bytes = Base32768Coder.decode(
                    message.removePrefix(ClientVars.MESSAGE_BLOCK_PREFIX)
                        .split("\n").joinToString("") {
                            it.removePrefix("print \"").removeSuffix("\"")
                        }
                )
            } catch (exception: Exception) {
                return@on
            }

            listeners.forEach {
                it.invoke(bytes, id)
            }
        }
    }
}