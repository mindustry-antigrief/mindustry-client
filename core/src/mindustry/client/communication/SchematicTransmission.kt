package mindustry.client.communication

import arc.*
import arc.files.Fi
import arc.scene.ui.*
import arc.scene.ui.layout.Scl
import mindustry.Vars.*
import mindustry.game.*
import mindustry.gen.*
import mindustry.ui.fragments.ChatFragment.ChatMessage
import java.io.*
import kotlin.random.*


class SchematicTransmission : Transmission {

    companion object {
        var tempFile: Fi? = null
    }
    override var id: Long = Random.nextLong()
    override val secureOnly = false
    var schematic: Schematic? = null
    var bytes: ByteArray
    var senderID: Int = -1

    constructor(schematic: Schematic) {
        val stream = ByteArrayOutputStream()
        Schematics.write(schematic, stream)
        this.bytes = stream.toByteArray()
        this.schematic = schematic
    }

    @Suppress("UNUSED_PARAMETER")
    constructor(byteArray: ByteArray, id: Long, senderID: Int) {
        this.bytes = byteArray
        this.senderID = senderID
    }

    override fun serialize(): ByteArray {
        return bytes
    }

    fun addToChat() {
        val message: ChatMessage = ui.chatfrag.addMsg(
            Core.bundle.format("schematic.chatsharemessage", Groups.player.getByID(this.senderID).name)
        )

        message.addButton(0, message.message.length) {
            //Parse the schematic
            this.schematic = Schematics.read(ByteArrayInputStream(bytes))
            val inSchematics = senderID == player.id || schematics.all().contains(schematic) // FINISHME: The communication ID might not be player id
            if (!inSchematics) {
                // This is incredibly cursed, if anyone has a better way please suggest
                // Basically we create a temporary file because the schematics code expects every schematic to be linked to a file
                    // (in particular, a change in tags will immediately be written to file)
                // but sometimes we just want to not save the schematic in file, so we use a temp file
                if (tempFile === null) {
                    tempFile = Fi.tempFile("clientcomm_msch")
                    tempFile!!.file().deleteOnExit()
                }
                this.schematic!!.file = tempFile
            }

            ui.schematics.showInfo(schematic)
            ui.schematics.info.cont.row()

            if (inSchematics) {
                ui.schematics.info.cont.button("@ok", Icon.ok) {}.disabled(true).growX().bottom()
            } else {
                ui.schematics.info.cont.button("@save", Icon.save) {}.growX().bottom().get().apply {
                    val cells = this.cells
                    this.clicked {
                        schematic!!.file = null // Remove the link to the temporary file, so that it can get linked to a real file
                        schematics.add(schematic)
                        // The rest is visual - make it unclickable
                        val size = cells[0].get().minHeight
                        cells[0].setElement<Image>(Image(Icon.ok)).size(size / Scl.scl(1f))
                        (cells[1].get() as Label).setText("@ok")
                        this.isDisabled = true
                    }
                }
            }
        }
    }
}