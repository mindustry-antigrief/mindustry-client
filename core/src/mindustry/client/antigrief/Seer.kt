package mindustry.client.antigrief

import arc.Core
import arc.struct.Seq
import mindustry.Vars
import mindustry.client.utils.dialog
import mindustry.client.utils.label
import mindustry.client.utils.wrap
import mindustry.gen.Call
import mindustry.gen.Player
import mindustry.world.Tile
import java.time.Instant
import java.time.temporal.ChronoUnit

object Seer {
    val players = Seq<PlayerData>()

    object Settings {
        // Still beta stuff duh
        var enabled = false

        var reactorScore = 3f
        var reactorDistance = 5

        var configScore = 0.3f

        var warnScoreThreshold = 15f
        var enableAutoKick = false
        var kickScoreThreshold = 30f

        var scoreDecay = 10f
    }

    fun showDialog() {
        dialog("Seer") {
            cont.button("Cached players") {
                dialog("Seer cached players") {
                    cont.label("Click on player to copy their id").grow()
                    cont.row()

                    cont.pane { t ->
                        for (data in players) {
                            t.button(
                                "<${data.score}> ${data.lastInstance.name} (${data.id}) [${data.firstJoined.truncatedTo(ChronoUnit.MINUTES)}m]") {
                                Core.app.clipboardText = data.id.toString()
                            }.wrap(false)
                            t.row()
                        }
                    }.grow()
                }
            }
        }
    }

    fun registerPlayer(player: Player) {
        val data = players.firstOrNull { it.id == player.id || it.lastInstance == player }
        if (data == null) players.add(PlayerData(player, Instant.now()))
        else {
            data.id = player.id
            data.lastInstance = player
        }
    }

    fun decayScores() {
        players.each {
            it.score = (it.score - Settings.scoreDecay).coerceAtLeast(0f)
        }
    }

    private fun getData(player: Player): PlayerData {
        return players.find { it.id == player.id || it.lastInstance == player }
    }

    private fun warnIfNeeded(data: PlayerData, player: Player) {
        if (!Settings.enabled) return
        if (data.score >= Settings.warnScoreThreshold) {
            Vars.player.sendMessage("${player.coloredName()} [accent]exceeded warn threshold! ${data.score}")
        }
        if (Settings.enableAutoKick && data.score >= Settings.kickScoreThreshold) {
            Call.sendChatMessage("/votekick " + player.name)
        }
    }

    fun thoriumReactor(player: Player?, distance: Float) {
        if (player == null) return
        val data = getData(player)
        data.score += Settings.reactorScore * distance / Settings.reactorDistance
        warnIfNeeded(data, player)
    }

    fun blockConfig(player: Player, tile: Tile) {
        val data = getData(player)
        data.score += Settings.configScore
        warnIfNeeded(data, player)
    }

    class PlayerData(
        var id: Int,
        var lastInstance: Player,
        val firstJoined: Instant
    ) {
        var score: Float = 0f

        constructor(instance: Player, timestamp: Instant): this(instance.id, instance, timestamp)
    }
}
