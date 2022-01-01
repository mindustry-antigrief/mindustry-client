package mindustry.client.ui

import arc.scene.ui.Image
import mindustry.client.utils.row
import mindustry.ui.dialogs.BaseDialog

// FINISHME: sender
class AttachmentDialog(message: String, attachments: List<Image>) : BaseDialog("attachments") {
    init {
        addCloseButton()
        add(message).center()
        cont.pane {
            for (item in attachments) row(item).grow()
        }
        show()
    }
}
