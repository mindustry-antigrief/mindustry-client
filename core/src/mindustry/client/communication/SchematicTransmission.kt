package mindustry.client.communication

import arc.*
import mindustry.Vars.schematics
import mindustry.Vars.ui
import mindustry.game.*
import mindustry.game.EventType.*
import mindustry.gen.*
import mindustry.ui.fragments.ChatFragment.ChatMessage
import java.io.*
import kotlin.random.*


class SchematicTransmission : Transmission {

    override var id: Long = Random.nextLong()
    override val secureOnly = false
    var schematic: Schematic;
    var senderID: Int = -1;

    constructor(schematic: Schematic) {
        this.schematic = schematic
    }

    @Suppress("UNUSED_PARAMETER")
    constructor(byteArray: ByteArray, id: Long, senderID: Int) {
        this.schematic = Schematics.read(ByteArrayInputStream(byteArray));
        this.senderID = senderID;
    }

    override fun serialize(): ByteArray {
        val stream = ByteArrayOutputStream()
        Schematics.write(this.schematic, stream)
        return stream.toByteArray()
    }

    fun addToChat() {

        val text = "${Core.bundle.get("schematic")}: [${schematic.name()}]"
        val message: ChatMessage = ui.chatfrag.addMessage(text, Groups.player.getByID(this.senderID).name,
                null, "", text)
        message.addButton(schematic.name()) {
            ui.schematics.showInfo(schematic);
            schematics.add(schematic);
        }
    }

}