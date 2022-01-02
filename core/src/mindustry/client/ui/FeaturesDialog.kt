package mindustry.client.ui

import arc.*
import arc.scene.ui.Dialog
import mindustry.input.Binding
import mindustry.ui.dialogs.*

object FeaturesDialog : BaseDialog("@client.features") {
    override fun show(): Dialog {
        cont.clear()
        buttons.clear()
        clearListeners()

        var str = Core.files.internal("features").readString("UTF-8")
        str = str.replace("\\{\\w+}".toRegex()) { res ->
            val value = res.value.removeSurrounding("{", "}")
            val bind = Binding.valueOf(value)
            Core.keybinds[bind].key.value
        }
        cont.pane(StupidMarkupParser.format(str)).growX().get()
            .setScrollingDisabled(true, false)
        addCloseButton()

        return super.show()
    }
}