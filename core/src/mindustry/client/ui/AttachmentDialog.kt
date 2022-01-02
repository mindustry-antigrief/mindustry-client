package mindustry.client.ui

import arc.scene.ui.Image
import mindustry.client.utils.row
import mindustry.ui.dialogs.BaseDialog

// FINISHME: sender
class AttachmentDialog(message: String, attachments: List<Image>) : BaseDialog("@client.attachments") {
    init {
        addCloseButton()
        cont.add(message).center()
        cont.row()
        cont.pane {
            for (item in attachments) it.row(item).grow()
        }
        show()
    }
}
