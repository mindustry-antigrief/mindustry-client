package mindustry.client.ui

import arc.*
import mindustry.ui.dialogs.*

object ChangelogDialog : BaseDialog("Changelog") {
    init {
        cont.pane(StupidMarkupParser.format(Core.files.internal("changelog").readString("UTF-8"))).growX().get()
            .setScrollingDisabled(true, false)
        addCloseButton()
    }
}