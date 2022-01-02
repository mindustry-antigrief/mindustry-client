package mindustry.client.ui

import arc.ApplicationListener
import arc.Core
import arc.Events
import arc.files.Fi
import arc.graphics.Pixmap
import arc.graphics.Texture
import arc.graphics.g2d.TextureRegion
import arc.input.KeyCode
import arc.scene.ui.Image
import arc.scene.ui.ImageButton
import arc.scene.ui.TextButton
import arc.scene.ui.layout.Table
import mindustry.Vars
import mindustry.client.Main
import mindustry.client.communication.BlockCommunicationSystem
import mindustry.client.communication.ImageTransmission
import mindustry.client.communication.InvisibleCharCoder
import mindustry.client.navigation.clientThread
import mindustry.client.utils.pixmapFromClipboard
import mindustry.client.utils.row
import mindustry.game.EventType
import mindustry.gen.Icon
import mindustry.ui.dialogs.BaseDialog

object UploadDialog : BaseDialog("@client.uploadtitle") {
    private val pane: Table
    private val images = mutableListOf<Pixmap>()
    private val intermImages = mutableListOf<Pixmap>()

    init {
        buttons.defaults().size(210f, 64f)
        buttons.button("@cancel", Icon.cancel) {
            this.hide()
            images.clear()
            intermImages.clear()
        }.size(210f, 64f)

        closeOnBack {
            images.clear()
            images.addAll(intermImages)
            intermImages.clear()
        }

        Core.app.addListener(object : ApplicationListener {
            override fun fileDropped(file: Fi?) {
                if (!Vars.state.isGame) return
                file ?: return
                try {
                    if (!isShown) show()
                    clientThread.post {
                        val pixmap = Pixmap(file)
                        Core.app.post {
                            imageAdded(pixmap)
                        }
                    }
                } catch (e: Exception) {
                    return
                }
            }
        })

        cont.add(ImageButton(Icon.upload)).center().get().clicked {
            Vars.platform.showMultiFileChooser({
                try {
                    clientThread.post {
                        val pixmap = Pixmap(it)
                        Core.app.post {
                            imageAdded(pixmap)
                        }
                    }
                } catch (e: Exception) {
                    Vars.ui.showInfoFade(Core.bundle["client.failedtoloadimage"])
                }
            },"png", "jpg", "jpeg")
        }
        cont.row()

        keyDown(KeyCode.v) {  // FINISHME: global, not just when dialog open
            if (Core.input.ctrl()) {
                clientThread.post {
                    val pixmap = pixmapFromClipboard() ?: return@post
                    Core.app.post {
                        imageAdded(pixmap)
                    }
                }
            }
        }

        pane = Table()

        cont.pane(pane)

        buttons.add(TextButton("@client.uploadtitle").apply {
            clicked {
                images.clear()
                images.addAll(intermImages)
                intermImages.clear()
                hide()
                pane.clear()
            }
        })

        Events.on(EventType.SendChatMessageEvent::class.java) {
            val id = InvisibleCharCoder.decode(it.message.takeLast(2)).run { (get(0).toUByte().toUInt() shl 8) or get(1).toUByte().toUInt() }.toShort()
            if (images.isNotEmpty()) Vars.ui.showInfoFade(Core.bundle.format("client.uploadingimages", images.size))
            var doneCount = 0
            val len = images.size
            val imgs = images.toList()
            if (!BlockCommunicationSystem.logicAvailable) Vars.ui.chatfrag.addMessage(Core.bundle["client.placelogic"])
            clientThread.post {
                for (image in imgs) {
                    if (image.width * image.height > (1920 * 1080)) {
                        Vars.ui.chatfrag.addMessage(Core.bundle["client.imagetoobig"])
                        continue
                    }

                    Main.send(ImageTransmission(id, image)) {
                        doneCount++
                        if (doneCount == len) Vars.ui.showInfoFade(Core.bundle["client.finisheduploading"])
                    }
                }
            }
            images.clear()
            pane.clear()
        }

        Events.on(EventType.WorldLoadEvent::class.java) {
            images.clear()
            intermImages.clear()
        }
    }

    fun clearImages() {
        intermImages.clear()
        images.clear()
    }

    private fun imageAdded(image: Pixmap) {
        val t = Table()
        t.add(Image(Texture(image)))
        t.add(ImageButton(Icon.cancel)).top().right().get().clicked {
            intermImages.remove(image)
            pane.removeChild(t)
        }
        pane.row(t)
        intermImages.add(image)
    }

//    override fun show(): Dialog {
//        pane.clear()
//        images.clear()
//        return super.show()
//    }
}
