package mindustry.client.communication

import arc.*
import arc.files.Fi
import arc.scene.ui.*
import arc.scene.ui.layout.Scl
import arc.util.*
import mindustry.Vars.*
import mindustry.game.*
import mindustry.gen.*
import mindustry.ui.fragments.ChatFragment.ChatMessage
import java.io.*
import java.util.zip.InflaterInputStream
import kotlin.random.*


class SchematicTransmission : Transmission {

    companion object {
        var tempFile: Fi? = null
        var spamPeople = false
    }
    override var id: Long = Random.nextLong()
    override val secureOnly = false
    var senderID: Int = -1
    var byteArray: ByteArray? = null

    constructor(schematic: Schematic) {
        val stream = ByteArrayOutputStream()
        Schematics.write(schematic, stream)
        byteArray = stream.toByteArray()
    }

    @Suppress("UNUSED_PARAMETER")
    constructor(byteArray: ByteArray, id: Long, senderID: Int) {
        this.senderID = senderID
        this.byteArray = byteArray
    }

    override fun serialize(): ByteArray {
        return byteArray!!
    }

    fun deserialize(byteArray: ByteArray): Schematic {
        try {
            return Schematics.read(ByteArrayInputStream(byteArray))
        } catch (ex: Exception) {
            Log.err(ex);
            throw ex
        }
    }

    fun addButtonToMsg(message: ChatMessage, schematic: Schematic) {
        message.message = Core.bundle.format("schematic.chatsharemessage", Groups.player.getByID(this.senderID).name, schematic.name())
        message.format()
        message.addButton(schematic.name()) {
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
                schematic.file = tempFile
            }

            ui.schematics.showInfo(schematic)
            ui.schematics.info.cont.row()

            if (inSchematics) {
                ui.schematics.info.cont.button("@ok", Icon.ok) {}.disabled(true).growX().bottom()
            } else {
                ui.schematics.info.cont.button("@save", Icon.save) {}.growX().bottom().get().apply {
                    val cells = this.cells
                    this.clicked {
                        schematic.file = null // Remove the link to the temporary file, so that it can get linked to a real file
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

    fun addToChat() {
        val message: ChatMessage = ui.chatfrag.addMsg(
            Core.bundle.format("schematic.chatsharemessage", Groups.player.getByID(this.senderID).name, Core.bundle.get("loading") + "[]")
        )
        if (byteArray === null) {
            Log.err("ByteArray in SchematicTransmission is null!")
            return
        }
        mainExecutor.submit {
            val bArray = byteArray!!.clone()
            val byteCountStream = InflaterInputStream(ByteArrayInputStream(byteArray, 5, byteArray!!.size - 5))
            val countStartTime = Time.millis()
            // Count the number of bytes in the stream, without actually storing it
            var length = 0L
            while(byteCountStream.available() == 1) length += byteCountStream.skip(Int.MAX_VALUE.toLong())
            Log.debug("Byte count took @ms", Time.timeSinceMillis(countStartTime))
            byteArray = null // Unusable now, so just invalidate it

            val needsUserConfirmation = length > (1L shl 20) // around 1MB would be Suspicious
            if (!needsUserConfirmation) { // If the schematic is short enough, just deserialize it to make it instantly ready
                val schematic = deserialize(bArray)
                Core.app.post {
                    addButtonToMsg(message, schematic)
                }
            } else { // if the schematic is humungous, display the estimated size and allow people to still click
                val mb = (length.toDouble()) / (1 shl 20).toDouble() // Get size in MB
                val sizeString = String.format("[yellow]%.2fMB[]", mb)
                Core.app.post {
                    message.message = Core.bundle.format("schematic.chatsharemessage", Groups.player.getByID(this.senderID).name, sizeString)
                    message.format()
                    message.addButton(sizeString) {
                        // When clicked, open the schematic. Any misbehaviour is on the user.
                        mainExecutor.submit {
                            val schematic = deserialize(bArray)
                            Core.app.post {
                                message.clearButtons()
                                addButtonToMsg(message, schematic)
                            }
                        }
                    }
                }
            }
        }
    }
}