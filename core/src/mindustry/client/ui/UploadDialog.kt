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
import mindustry.client.utils.*
import mindustry.game.*
import mindustry.gen.*
import mindustry.ui.*
import mindustry.ui.dialogs.*

object UploadDialog : BaseDialog("@client.uploadtitle") { // FINISHME: Somehow somewhere one of the pixmaps made here is still not disposed of correctly... I can't be bothered to fix it honestly
    private val images = mutableListOf<Pixmap>()
    private val textures = mutableListOf<Texture>()

    init {
        addCloseButton()
        buttons.button("@clear", Icon.trash, ::clearImages)
        buttons.button("@add", Icon.upload) {
            Vars.platform.showMultiFileChooser({
                try {
                    addImage(Pixmap(it))
                } catch (e: Exception) {
                    Vars.ui.showInfoToast(Core.bundle["client.failedtoloadimage"], 3f)
                }
            }, "png", "jpg", "jpeg")
        }

        keyDown {
            if (Core.input.ctrl() && it == KeyCode.v) { // For some reason, it seems that interacting with the clipboard breaks sdl on Mac
                if (OS.isMac) Vars.ui.showInfoToast("Image pasting does not work on mac.", 3f)
                else {
                    addImage(pixmapFromClipboard() ?: return@keyDown)
                }
            }
        }

        Events.on(EventType.SendChatMessageEvent::class.java) {
            if (images.isEmpty()) return@on
            val id = InvisibleCharCoder.decode(it.message.takeLast(2)).run { (get(0).toUByte().toUInt() shl 8) or get(1).toUByte().toUInt() }.toShort()
            Vars.ui.showInfoToast(Core.bundle.format("client.uploadingimages", images.size), 3f)
            var doneCount = 0
            val imgs = images.groupBy { img -> img.width * img.height > 1920 * 1080 }
            imgs[true]?.forEach(Pixmap::dispose) // Anything over 1920 * 1080
            if (!BlockCommunicationSystem.logicAvailable && ClientVars.pluginVersion == -1F) {
                Vars.ui.chatfrag.addMessage(Core.bundle["client.placelogic"])
                imgs[false]?.forEach(Pixmap::dispose)
            } else if (false in imgs) {
                if (true in imgs) Vars.ui.chatfrag.addMessage(Core.bundle["client.imagetoobig"]) // Any of the images was removed for being too large.
                for (image in imgs[false]!!) {
                    Main.send(ImageTransmission(id, image)) {
                        image.dispose()
                        doneCount++
                        if (doneCount == imgs[false]!!.size) Core.app.post { Vars.ui.showInfoToast(Core.bundle["client.finisheduploading"], 3f) } // Thread safety doesn't exist
                    }
                }
            }
            images.clear()
            updateImages()
        }

        Events.on(EventType.WorldLoadEvent::class.java) {
            images.forEach(Pixmap::dispose)
            images.clear()
            updateImages()
        }
    }

    fun clearImages() {
        images.forEach(Pixmap::dispose)
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
        textures.forEach(Texture::dispose)
        textures.clear()
        cont.pane { pane ->
            images.forEach {
                val tex = Texture(it)
                textures.add(tex)
                pane.stack(
                    Image(tex),
                    Table { t ->
                        t.button(Icon.cancel.tint(Color.red), Styles.emptyi) {
                            it.dispose()
                            images.remove(it)
                            updateImages()
                        }.expand().top().left().pad(10f)
                    }
                ).row()
            }
        }.fill()
    }
}