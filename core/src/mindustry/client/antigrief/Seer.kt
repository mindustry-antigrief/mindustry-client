package mindustry.client.antigrief

import arc.*
import arc.struct.*
import arc.util.*
import mindustry.*
import mindustry.gen.*
import mindustry.world.*
import mindustry.world.blocks.logic.*
import java.io.*
import java.time.*
import java.util.zip.*

object Seer { // FINISHME: Fully implement and test this
    val players = Seq<PlayerData>()
    val timer = Interval()

    fun registerPlayer(player: Player) {
        val data = player.getData()
        if (data == null) players.add(PlayerData(player, Instant.now()))
        else {
            data.id = player.id
            data.lastInstance = player
        }
    }

    fun update() {
        // per 30 seconds * 60 frames
        if (timer[Core.settings.getInt("seer-scoredecayinterval") * 900f]) {
            decayScores()
        }
    }

    private fun decayScores() {
        players.each {
            if (!Groups.player.contains { p -> p.id == it.id}) return@each // Do not score decay players who've left
            it.score = (it.score - Core.settings.getInt("seer-scoredecay")).coerceAtLeast(0f)
        }
    }

    private fun Player.getData(): PlayerData? {
        return players.find { it.id == id || it.lastInstance == this }
    }

    private fun warnIfNeeded(data: PlayerData, player: Player) { // FINISHME: Bundles
        if (!Core.settings.getBool("seer-enabled")) return
        if (data.score >= Core.settings.getInt("seer-warnthreshold")) {
            Vars.player.sendMessage("${player.coloredName()} [accent]exceeded warn threshold! ${data.score}")
        }
        if (Core.settings.getBool("seer-autokick") && data.score >= Core.settings.getInt("seer-autokickthreshold")) {
            Call.sendChatMessage("/votekick #${player.id}")
        }
    }

    fun thoriumReactor(player: Player?, distance: Float) {
        if (!Core.settings.getBool("seer-enabled")) return
        val data = player?.getData() ?: return
        data.score += Core.settings.getInt("seer-reactorscore") * distance / Core.settings.getInt("seer-reactordistance")
        warnIfNeeded(data, player)
    }

    fun blockConfig(player: Player, tile: Tile, config: Any?) {
        if (!Core.settings.getBool("seer-enabled")) return
        val data = player.getData() ?: return
        data.score += Core.settings.getInt("seer-configscore") * tile.dst(player) / (Core.settings.getInt("seer-configdistance") * 5f) // 5f per config score
        if (tile.block() is LogicBlock && config != null) handleLogicConfig(player, tile, config)
        warnIfNeeded(data, player)
    }

    private fun handleLogicConfig(player: Player, tile: Tile, config: Any) {
        // Code taken from LogicBlock
        if (config is ByteArray) {
            try {
                DataInputStream(InflaterInputStream(ByteArrayInputStream(config))).use { stream ->
                    val version = stream.read()
                    val bytelen = stream.readInt()
                    if (bytelen > LogicBlock.maxByteLen) throw IOException("Malformed logic data! Length: $bytelen")
                    val bytes = ByteArray(bytelen)
                    stream.readFully(bytes)
                    val total = stream.readInt()
                    if (version == 0) {
                        //old version just had links, ignore those
                        for (i in 0 until total) stream.readInt()
                    } else if (total >= Core.settings.getInt("seer-proclinkthreshold")) procLinkSpam(player)
                }
            } catch (_: Exception) {} // Do nothing about it if its malformed data
        } else if (config is Int) {
            if (!(tile.block() as LogicBlock).accessible()) return // Ignore if world proc
            val entity = tile.build as LogicBlock.LogicBuild // For simplicity sake
            if (entity.links.size + 1 >= Core.settings.getInt("seer-proclinkthreshold")) procLinkSpam(player)
        }
    }

    private fun procLinkSpam(player: Player) {
        val data = player.getData() ?: return
        data.score += Core.settings.getInt("seer-proclinkscore")
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
