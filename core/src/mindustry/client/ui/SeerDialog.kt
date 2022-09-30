package mindustry.client.ui

import arc.Core
import mindustry.Vars
import mindustry.client.antigrief.Seer
import mindustry.client.utils.dialog
import mindustry.client.utils.label
import mindustry.client.utils.wrap
import mindustry.ui.dialogs.BaseDialog
import java.time.temporal.ChronoUnit

object SeerDialog : BaseDialog("Seer") {
    init {
        cont.button("Cached players") {
            dialog("Seer cached players") {
                cont.label("Click on player to copy their id").center().grow()
                cont.row()

                cont.pane { t ->
                    for (data in Seer.players) {
                        t.button(
                            "<${data.score}> ${data.lastInstance.name}[] (${data.id}) [${data.firstJoined.truncatedTo(
                                ChronoUnit.MINUTES)}m]") {
                            Core.app.clipboardText = data.id.toString()
                        }.wrap(false)
                        t.row()
                    }
                }.pad(10f).grow()
                addCloseButton()
            }.show()
        }.size(200f, 50f)

        cont.button("Settings") {
            // FINISHME: Separate UI
            Vars.ui.settings.visible(3) // Activate client settings
        }.grow()

        addCloseButton()
    }
}
