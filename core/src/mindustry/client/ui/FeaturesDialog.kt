package mindustry.client.ui

import arc.*
import arc.scene.ui.*
import mindustry.client.*
import mindustry.input.*
import mindustry.ui.dialogs.*

object FeaturesDialog : BaseDialog("@client.features") {
    override fun show(): Dialog {
        cont.clear()
        buttons.clear()
        clearListeners()

        var str = Core.files.internal("features").readString("UTF-8")
        str = str.replace("\\{\\w+}".toRegex()) { res ->
            val value = res.value.removeSurrounding("{", "}")
            if (value == "p") return@replace ClientVars.clientCommandHandler.prefix // {p} becomes the client command prefix
            try {
                val bind = Binding.valueOf(value)
                Core.keybinds[bind].key.value
            } catch (ignored: Exception) { // If this isn't a keybinding, we will keep it as is.
                return@replace res.value
            }
        }
        cont.pane(StupidMarkupParser.format(str)).growX().get()
            .setScrollingDisabled(true, false)
        addCloseButton()

        return super.show()
    }
}