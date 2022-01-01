package mindustry.client.ui

import arc.ApplicationListener
import arc.Core
import arc.Events
import arc.files.Fi
import arc.graphics.Pixmap
import arc.graphics.Texture
import arc.input.KeyCode
import arc.scene.ui.Dialog
import arc.scene.ui.Image
import arc.scene.ui.ImageButton
import arc.scene.ui.TextButton
import arc.scene.ui.layout.Table
import mindustry.Vars
import mindustry.client.Main
import mindustry.client.communication.ImageTransmission
import mindustry.client.communication.InvisibleCharCoder
import mindustry.client.utils.pixmapFromClipboard
import mindustry.client.utils.row
import mindustry.game.EventType
import mindustry.gen.Icon
import mindustry.ui.dialogs.BaseDialog

object UploadDialog : BaseDialog("upload") {
    private val pane: Table
    private val images = mutableListOf<Pixmap>()

    init {
        addCloseButton()

        Core.app.addListener(object : ApplicationListener {
            override fun fileDropped(file: Fi?) {
                if (!Vars.state.isGame) return
                file ?: return
                try {
                    if (!isShown) show()
                    imageAdded(Pixmap(file))
                } catch (e: Exception) {
                    return
                }
            }
        })

        buttons.add(TextButton("Upload")/* FINISHME: bundle */.apply {
            clicked {
                Vars.platform.showFileChooser(true, "png") {
                    try {
                        imageAdded(Pixmap(it))
                    } catch (e: Exception) {
                        Vars.ui.showInfoFade("Failed to load image") /* FINISHME: bundle */
                    }
                }
            }
        })

        cont.add(ImageButton(Icon.upload)).center()
        cont.row()

        keyDown(KeyCode.v) {  // FINISHME: global, not just when dialog open
            if (Core.input.ctrl()) {
                imageAdded(pixmapFromClipboard() ?: return@keyDown)
            }
        }

        pane = Table()

        cont.pane(pane)

        Events.on(EventType.SendChatMessageEvent::class.java) {
//            println("a" + InvisibleCharCoder.decode(it.message.takeLast(2)).contentToString())
//            println("b" + InvisibleCharCoder.decode(it.message.takeLast(2)).run {(get(1).toUInt() shl 8) or get(0).toUInt()}.toShort())
//            println("c" + InvisibleCharCoder.decode(it.message.takeLast(2)).run {(get(0).toUByte().toUInt() shl 8) or get(1).toUByte().toUInt()}.toShort())
            val id = InvisibleCharCoder.decode(it.message.takeLast(2)).run { (get(0).toUByte().toUInt() shl 8) or get(1).toUByte().toUInt() }.toShort()
            println("d $id")
            if (images.isNotEmpty()) Vars.ui.showInfoFade("Uploading ${images.size} images...")  // FINISHME: bundle
            var doneCount = 0
            val len = images.size
            for (image in images) {
                Main.send(ImageTransmission(id, image)) {
                    doneCount++
                    if (doneCount == len) Vars.ui.showInfoFade("Finished uploading")
                }
            }
            images.clear()
        }
    }

    private fun imageAdded(image: Pixmap) {
        val t = Table()
        t.add(Image(Texture(image)))
        t.add(ImageButton(Icon.cancel)).top().right().get().clicked {
            images.remove(image)
            pane.removeChild(t)
        }
        pane.row(t)
        images.add(image)
    }

    override fun show(): Dialog {
        pane.clear()
        images.clear()
        return super.show()
    }
}
