package mindustry.client.ui

import arc.graphics.*
import arc.scene.ui.*
import arc.struct.*
import mindustry.client.utils.*
import mindustry.ui.dialogs.*

class AttachmentDialog(message: String, attachments: Seq<Texture>) : BaseDialog("@client.attachments") {
    init {
        addCloseButton()
        cont.add(message).center()
        cont.row()
        cont.pane { pane ->
            attachments.forEach {
                pane.row(Image(it))
            }
        }.fill()
        show()
    }
}