package mindustry.client.utils

import arc.*
import arc.util.*
import arc.util.io.*
import mindustry.*
import mindustry.client.Spectate
import mindustry.gen.Groups
import mindustry.ui.fragments.ChatFragment.*
import java.io.*
import java.security.*

/** Allow the server to send clickable chat buttons. These were previously all client side which was obviously not ideal. */
object NetworkChatButtons {
    private const val VERSION = 1
    private val inp = ReusableByteInStream()
    private val reads = Reads(DataInputStream(inp))

    /** Actions a button can run locally instead of a chat command. */
    private val actions = mapOf<String, (String) -> Unit>(
        "%copy" to { text -> Core.app.clipboardText = text },
        "%spectate" to { text -> Groups.player.getByID(text.toInt())?.apply { Spectate.spectate(this) } }
    )

    fun init() {
        Vars.netClient.addBinaryPacketHandler("fooChatButton") {
            inp.setBytes(it)
            runCatching { read() }.onFailure { e -> Log.err("Error reading fooChatButton packet", e) }
        }
    }

    private fun read() {
        if (reads.b().toInt() != VERSION) return // Unsupported version, ignore
        val messageHash = reads.l()
        // The message is sent first and both are sent over tcp so it should already be in the list, but we check multiple messages just to make sure. We match by the first 8 bytes of the sha2 hash since there's no proper id system.
        val msg = findRecentMessage { it.message != null && hash(it.message) == messageHash }

        val buttonCount = reads.b().toInt()
        repeat(buttonCount) {
            val start = reads.i()
            val end = reads.i()
            val action = reads.str()
            msg?.addButton(start, end) { run(action) }
        }

        val appendCount = reads.b().toInt()
        repeat(appendCount) {
            val separator = reads.str()
            val text = reads.str()
            val action = reads.str()
            msg?.append(separator, text) { run(action) }
        }
    }

    /** Appends [separator] + [text] to this message and makes [text] clickable. */
    private fun ChatMessage.append(separator: String, text: String, clicked: () -> Unit) {
        val start = Strings.stripColors(formattedMessage).length + Strings.stripColors(separator).length
        message = (message ?: "") + separator + text
        unformatted += separator + text
        formattedMessage += separator + text
        addButton(start, start + Strings.stripColors(text).length) { clicked() }
    }

    private fun run(actionOrCommand: String) {
        Log.debug("Running server button: $actionOrCommand")
        val action = actions[actionOrCommand.substringBefore(' ')]
        if (action != null) action(actionOrCommand.substringAfter(' '))
        else if (actionOrCommand.getOrNull(0) == '/') handleClientCommand(actionOrCommand, false)
    }

    /** First 8 bytes of the SHA-256 digest of [message], as a long. */
    private fun hash(message: String): Long {
        val digest = MessageDigest.getInstance("SHA-256").digest(message.toByteArray())
        var hash = 0L
        for (i in 0 until 8) hash = (hash shl 8) or (digest[i].toLong() and 0xFF)
        return hash
    }
}
