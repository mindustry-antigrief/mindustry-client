package mindustry.client.ui

import arc.*
import mindustry.ui.dialogs.*

object FeaturesDialog : BaseDialog("@client.features") {
    init {
        cont.pane(StupidMarkupParser.format(Core.files.internal("features").readString("UTF-8"))).growX().get()
            .setScrollingDisabled(true, false)
        addCloseButton()
    }
}