package com.github.blahblahbloopster.ui

import arc.Core
import mindustry.ui.dialogs.BaseDialog
import mindustry.client.ui.StupidMarkupParser

object FeaturesDialog : BaseDialog("@client.features") {
    init {
        cont.pane(StupidMarkupParser.format(Core.files.internal("features").readString("UTF-8"))).growX().get()
            .setScrollingDisabled(true, false)
        addCloseButton()
    }
}