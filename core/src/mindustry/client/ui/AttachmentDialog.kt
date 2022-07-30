package mindustry.client.ui

import arc.scene.ui.*
import mindustry.client.utils.*
import mindustry.ui.dialogs.*

class AttachmentDialog(message: String, attachments: List<Image>) : BaseDialog("@client.attachments") {
    init {
        addCloseButton()
        cont.add(message).center()
        cont.row()
        cont.pane { pane ->
            attachments.forEach {
                pane.row(it)
            }
        }.fill()
        show()
    }
}