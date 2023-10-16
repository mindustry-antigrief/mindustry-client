package mindustry.client.ui

import arc.*
import arc.scene.ui.*
import arc.scene.ui.layout.*
import mindustry.*
import mindustry.client.antigrief.*
import mindustry.client.utils.*
import mindustry.ui.dialogs.*
import java.time.temporal.*

object SeerDialog : BaseDialog("Seer") { // FINISHME: Bundle
    init {
        cont.button("Cached players") {
            showCachedPlayers()
        }.size(200f, 50f)

        cont.button("Settings") {
            // FINISHME: Separate UI
            Vars.ui.settings.visible(3) // Activate client settings
        }.size(200f, 50f)

        addCloseButton()
    }

    private fun showCachedPlayers() {
        dialog("Seer cached players") {
            fun createPane(search: String = "") =
                Table { t ->
                    for (data in Seer.players.select { search.isBlank() || BiasedLevenshtein.biasedLevenshtein(search, it.lastInstance.name) < 3 }) {
                        t.button(
                            "<${data.score}> ${data.lastInstance.name}[white] (${data.id}) [${data.firstJoined.truncatedTo(
                                ChronoUnit.MINUTES)}m]") {
                            Core.app.clipboardText = data.id.toString()
                        }.wrap(false)
                        t.marginBottom(10f)
                        t.row()
                    }
                }

            val pane = ScrollPane(createPane())

            var search = ""
            cont.table { table ->
                table.label("@search")
                table.field(search) { search = it; pane.widget = createPane(search) }
            }.center().row()

            cont.label("Click on player to copy their id").center().row()
            cont.add(pane)

            addCloseButton()
        }.show()
    }
}
