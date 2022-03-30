package mindustry.client.ui

import arc.*
import arc.graphics.*
import arc.input.*
import arc.scene.ui.*
import arc.scene.ui.layout.*
import arc.util.*
import mindustry.*
import mindustry.client.*
import mindustry.client.communication.*
import mindustry.client.navigation.*
import mindustry.client.utils.*
import mindustry.game.*
import mindustry.gen.*
import mindustry.ui.*
import mindustry.ui.dialogs.*

object UploadDialog : BaseDialog("@client.uploadtitle") {
    private val images = mutableListOf<Pixmap>()

    init {
        addCloseButton()
        buttons.button("@clear", Icon.trash, ::clearImages)
        buttons.button("@add", Icon.upload) {
            Vars.platform.showMultiFileChooser({
                try {
                    clientThread.post {
                        val pixmap = Pixmap(it)
                        Core.app.post {
                            addImage(pixmap)
                        }
                    }
                } catch (e: Exception) {
                    Vars.ui.showInfoToast(Core.bundle["client.failedtoloadimage"], 3f)
                }
            }, "png", "jpg", "jpeg")
        }

        keyDown {
            if (Core.input.ctrl() && it == KeyCode.v) { // For some reason, it seems that interacting with the clipboard breaks sdl on Mac
                if (OS.isMac) Vars.ui.showInfoToast("Image pasting does not work on mac.", 3f)
                else clientThread.post {
                    val pixmap = pixmapFromClipboard() ?: return@post
                    Core.app.post {
                        addImage(pixmap)
                    }
                }
            }
        }

        Events.on(EventType.SendChatMessageEvent::class.java) {
            if (images.isEmpty()) return@on
            val id = InvisibleCharCoder.decode(it.message.takeLast(2)).run { (get(0).toUByte().toUInt() shl 8) or get(1).toUByte().toUInt() }.toShort()
            Vars.ui.showInfoToast(Core.bundle.format("client.uploadingimages", images.size), 3f)
            var doneCount = 0
            val len = images.size
            val imgs = images.filterNot { img -> img.width * img.height > 1920 * 1080 }
            if (!BlockCommunicationSystem.logicAvailable && ClientVars.pluginVersion == -1) {
                Vars.ui.chatfrag.addMessage(Core.bundle["client.placelogic"])
            } else {
                if (imgs.size != len) Vars.ui.chatfrag.addMessage(Core.bundle["client.imagetoobig"]) // Any of the images was removed for being too large.
                clientThread.post {
                    for (image in imgs) {
                        Main.send(ImageTransmission(id, image)) {
                            doneCount++
                            if (doneCount == imgs.size) Core.app.post { Vars.ui.showInfoToast(Core.bundle["client.finisheduploading"], 3f) } // Thread safety doesn't exist
                        }
                    }
                }
            }
            images.clear()
            updateImages()
        }

        Events.on(EventType.WorldLoadEvent::class.java) {
            images.clear()
            updateImages()
        }
    }

    fun clearImages() {
        images.clear()
        updateImages()
    }

    fun hasImage() = images.any()

    private fun addImage(image: Pixmap) {
        images.add(image)
        updateImages()
    }

    private fun updateImages() {
        cont.clearChildren()
        cont.pane { pane ->
            images.forEach {
                pane.stack(
                    Image(Texture(it)),
                    Table { t ->
                        t.button(Icon.cancel.tint(Color.red), Styles.emptyi) {
                            images.remove(it)
                            updateImages()
                        }.expand().top().left().pad(10f)
                    }
                ).row()
            }
        }.fill()
    }
}