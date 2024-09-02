package mindustry.client.ui

import arc.*
import arc.scene.ui.*
import mindustry.ui.dialogs.*

object ChangelogDialog : BaseDialog("Changelog") {
    private var init = false
    override fun show(): Dialog {
        if (!init) {
            init = true
            cont.pane(StupidMarkupParser.format(Core.files.internal("changelog").readString("UTF-8"))).growX().scrollX(false)
            addCloseButton()
        }
        return super.show()
    }
}