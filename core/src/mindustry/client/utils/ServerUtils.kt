@file:JvmName("ServerUtils")

package mindustry.client.utils

import arc.*
import arc.func.*
import arc.util.*
import mindustry.Vars.*
import mindustry.client.*
import mindustry.client.utils.CustomMode.*
import mindustry.content.*
import mindustry.content.UnitTypes.*
import mindustry.entities.*
import mindustry.game.EventType.*
import mindustry.gen.*
import mindustry.net.*
import mindustry.net.Packets.*
import mindustry.ui.dialogs.BaseDialog
import mindustry.ui.fragments.ChatFragment.*

sealed class Server(
    private val groupName: String? = null,
    private val mapVote: MapVote? = null,
    @JvmField val whisper: Cmd = Cmd("/w", -1), // FINISHME: This system still sucks despite my best efforts at making it good
    private val rtv: Cmd = Cmd("/rtv", -1),
    @JvmField val freeze: Cmd = Cmd("/freeze", -1),
    @JvmField val thaw: Cmd = Cmd("/thaw", -1),
    @JvmField val mute: Cmd = Cmd("/mute", -1),
    @JvmField val unmute: Cmd = Cmd("/unmute", -1),
    @JvmField val ghost: Boolean = false,
    val networkTileLogs: Boolean = false,
    private val votekickString: String = "Type [orange]/vote <y/n>[] to agree."
) {
    /** Converts a player to a copyable server-specific player identifier. Alt-click in the tab list will copy to clipboard. */
    open val playerIDCopy: Func<Player, String?>? = null

    /** Server-specific rate limits */
    protected open val ratelimitMax = Core.settings.getInt("ratelimitmax", Administration.Config.interactRateLimit.num()) // The max number of configs per ratelimit window

    val name: String get() = this::class.simpleName!!

    @JvmName("b") operator fun invoke() = current === this

    /** @return whether this is the server we just joined */
    protected open fun isJoinedServer(group: List<String>?, host: Host?): Boolean = group?.contains(host?.address) == true

    /** Run when a server is joined (or when returning to menu, this is called on [other]) */
    protected open fun joined() {
        ClientVars.ratelimitMax = ratelimitMax
        ClientVars.ratelimitRemaining = ratelimitMax
    }

    /** Converts a player object into a string for use in commands */
    open fun playerString(p: Player) = p.id.toString()

    /** Handle clickable buttons */
    open fun handleButtons(msg: ChatMessage) {
        if (rtv.canRun()) msg.addButton(rtv.str, rtv::invoke) // FINISHME: I believe cn has a no option? not too sure
//        if (kick.canRun()) msg.addButton(kick.str, kick::invoke) FINISHME: Implement votekick buttons here
//        FINISHME: Add cn excavate buttons
    }

    /** Run when banning [p] */
    open fun handleBan(p: Player) = Call.adminRequest(p, AdminAction.ban, null)

    /** Run when freezing [p] */
    open fun handleFreeze(p: Player) {}

    /** Run when muting [p] */
    open fun handleMute(p: Player) {}

    /** Whether the player has access to the admin ui in the player list */
    open fun adminui() = player.admin

    /** Map like/dislike */
    fun mapVote(i: Int) {
        if (mapVote != null) Call.sendChatMessage(mapVote[i] ?: run { Log.err("Invalid vote $i"); return })
        else Log.warn("Map votes are not available on server $name")
    }

    fun isVotekick(msg: String) = votekickString in msg

    /** Handles a message on a server. If true is returned, the message will be discarded and not printed. */
    open fun blockMessage(msg: String?, unformatted: String?, sender: Player?): Boolean = false

    /** Used to block effects on servers that spam them. */
    open fun blockEffect(fx: Effect, rot: Float): Boolean = false

    // FINISHME: Encourage servers to add a packet just for id. Maybe even one packet that gets all member ids. That would be nice.
    open fun getStats(player: Player, force: Boolean = false) {}

    open fun updateRank() {}

    companion object {
        // Create a variable for each server. This is abhorrent, but it's the best way to avoid the .INSTANCE call in java.
        @JvmField val other = Other
        @JvmField val nydus = Nydus
        @JvmField val cn = CN
        @JvmField val korea = Korea
        @JvmField val fish = Fish
        @JvmField val darkdustry = Darkdustry
        @JvmField val corium = Corium

        private val servers = listOf(other, nydus, cn, korea, fish, darkdustry, corium)

        open class Cmd(val str: String, private val rank: Int = 0) { // 0 = anyone, -1 = disabled
            val enabled = rank != -1

            open fun canRun() = rank == 0 || enabled && ClientVars.rank >= rank

            operator fun invoke(p: Player, vararg args: String) = invoke(current.playerString(p), *args)

            open operator fun invoke(vararg args: String) = when {
                !enabled -> Log.err("Command $str is disabled on this server.")
                !canRun() -> Log.err("You do not have permission to run $str on this server.")
                else -> run(*args)
            }

            protected open fun run(vararg args: String) = Call.sendChatMessage("$str ${args.joinToString(" ")}")
        }

        class MapVote(down: String = "/downvote", none: String = "/novote", up: String = "/upvote") {
            val options = arrayOf(down, none, up)
            operator fun get(i: Int) = options.getOrNull(i)
        }

        @JvmField var current: Server = Other
//        val ghostList by lazy { Core.settings.getJson("ghostmodeservers", Seq::class.java, String::class.java) { Seq<String>() } as Seq<String> }

        @JvmStatic
        fun onServerJoin() { // Called once on server join before WorldLoadEvent (and by extension ServerJoinEvent), the player will not be added here, hence the need for ServerJoinEvent
            val grouped = ui.join.communityHosts.groupBy({ it.group }) { it.address }
            servers.forEach {
                if (it.isJoinedServer(if (it.groupName == null) emptyList() else grouped[it.groupName], ui.join.lastHost) ) {
                    current = it
                    return@forEach
                }
            }
            current.joined()
            Log.debug("Joining server, override set to: ${current.name}")
        }

        init {
            Events.on(MenuReturnEvent::class.java) {
                current = Other
                current.joined()
                Log.debug("Returning to menu, server, mode override cleared")
            }
        }

        /** The destination ip and port of the server that we will be sent to by [mindustry.core.NetClient.connect] */
        @JvmField var destinationServer: String? = null
    }
}




object Other : Server()

object Nydus : Server(groupName = "nydus") {
    override fun isJoinedServer(group: List<String>?, host: Host?) = host?.name?.contains("nydus") == true
}

object CN : Server(groupName = "Chaotic Neutral", rtv = Companion.Cmd("/rtv"))

object Korea : Server(groupName = "Korea", ghost = true)

object Fish : Server(
    groupName = "Fish",
    whisper = Companion.Cmd("/msg"),
    networkTileLogs = true
) {
    init {
        Events.on(PlayerJoin::class.java) {
            ohno()
        }

        Events.on(WorldLoadEvent::class.java) {
            ohno() // Fine to do after a sync probably, right?
        }
    }

    @JvmField var blockAnnoyances = Core.settings.getBool("blockannoyances")
    private var ohnoTask: Timer.Task? = null


    override fun blockMessage(msg: String?, unformatted: String?, sender: Player?): Boolean {
        msg ?: return false
        if (sender == null && ohnoTask != null) { // Very hacky way of handling autoOhno
            if ("Too close to an enemy tile!" in msg || "You cannot spawn ohnos while dead." in msg) return true // We don't care honestly
            if ("Sorry, the max number of ohno units has been reached." in msg || "Ohnos have been temporarily disabled." in msg || "Ohnos are disabled in PVP." in msg || "Ohnos cannot survive in this map." in msg) {
                Time.run(60f) { // Null it out a second later, this is just to prevent any additional messages from bypassing the return below (only if it's the same one we just canceled).
                    if (ohnoTask?.isScheduled != true) ohnoTask = null
                }
                ohnoTask!!.cancel()
                return true
            }
        }

        if (sender == null && "Fish Membership" in msg) return true // Adblock

        return false // All other messages are okay
    }

    /** Fish staff spam obnoxious particle rings */
    override fun blockEffect(fx: Effect, rot: Float): Boolean {
        return blockAnnoyances && rot == 0F && fx == Fx.pointBeam
    }

    /** Support for fish testing server */
    override fun isJoinedServer(group: List<String>?, host: Host?) = super.isJoinedServer(group, host) || host?.name?.contains("[white]>|||>[#6a00ff]F[#5400c9]i[#3e0191]s[#0]h.") == true

    /** Automatically creates enough ohnos to fill the cap */
    fun ohno(force: Boolean = false) {
        if (!force && !Core.settings.getBool("autoohno", false)) return
        ohnoTask?.cancel()
        ohnoTask = Timer.schedule({ if (!this()) ohnoTask!!.cancel() else if (!player.blockOn().solid && alpha.supportsEnv(state.rules.env)) Call.sendChatMessage("/ohno") }, 3f, 0.5f)
    }
}

object Darkdustry : Server(groupName = "Mindurka")

object Corium : Server(
    whisper = Companion.Cmd("/w"),
    rtv = Companion.Cmd("/rtv"),
    freeze = Companion.Cmd("/freeze", 5),
    thaw = Companion.Cmd("/thaw", 5),
    mute = Companion.Cmd("/mute", 5),
    unmute = Companion.Cmd("/unmute", 5),
    networkTileLogs = true
) {
    private val moderationDurations = arrayOf("1d", "1w", "1mo", "1y", "5y")

    init {
        netClient.addPacketHandler("playerCode") {
            if (corium()) {
                val (id, code) = it.split(" ")
                Groups.player.getByID(id.toInt())?.serverID = code
            }
        }
    }

    val codeRegex = Regex("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}$")

    override val playerIDCopy = Func { p: Player -> p.serverID }

    override val ratelimitMax get() = if (ClientVars.rank < 1) 10 else super.ratelimitMax

    override fun handleBan(p: Player) {
        val playerId = p.id.toString()
        val rollbackId = p.trace?.uuid ?: p.serverID
        Call.serverPacketReliable("silentFreeze", playerId)

        val banMenu = BaseDialog("Ban ${p.coloredName()}")
        banMenu.setFillParent(false)
        var submitted = false

        banMenu.cont.add("Reason:").left()
        banMenu.cont.row()
        val reasonField = banMenu.cont.field("Griefing.") {}.width(400f).get()
        banMenu.cont.row()
        banMenu.cont.table { durations ->
            moderationDurations.forEach { duration ->
                durations.button(duration) {
                    submitted = true
                    Call.sendChatMessage("/ban ${playerString(p)} $duration ${reasonField.text}")
                    banMenu.hide()
                    if (rollbackId != null) {
                        ui.showConfirm("@confirm", "@client.rollback.title") {
                            Call.sendChatMessage("/undo $rollbackId 10")
                            Call.sendChatMessage("/undo f")
                        }
                    }
                }.size(70f, 50f).pad(2f)
            }
        }

        banMenu.buttons.button("@cancel", Icon.cancel, banMenu::hide).size(200f, 54f).pad(2f)
        banMenu.closeOnBack()
        banMenu.hidden {
            if (!submitted) Call.serverPacketReliable("silentThaw", playerId)
        }
        banMenu.show()
    }

    override fun handleFreeze(p: Player) {
        if (!p.serverModerationStateKnown) {
            getStats(p, true)
            return
        }

        if (p.serverFrozen) {
            ui.showConfirm("@confirm", Core.bundle.format("client.confirmthaw", p.coloredName())) {
                thaw(p)
            }
            return
        }

        val playerId = p.id.toString()
        Call.serverPacketReliable("silentFreeze", playerId)

        val freezeMenu = BaseDialog("Freeze ${p.coloredName()}")
        freezeMenu.setFillParent(false)
        var submitted = false

        freezeMenu.cont.add("Reason:").left()
        freezeMenu.cont.row()
        val reasonField = freezeMenu.cont.field("Griefing.") {}.width(400f).get()
        freezeMenu.cont.row()
        freezeMenu.cont.table { durations ->
            moderationDurations.forEach { duration ->
                durations.button(duration) {
                    submitted = true
                    freeze(p, duration, reasonField.text)
                    freezeMenu.hide()
                }.size(70f, 50f).pad(2f)
            }
        }.pad(4f)

        freezeMenu.buttons.button("@cancel", Icon.cancel, freezeMenu::hide).size(200f, 54f).pad(2f)
        freezeMenu.closeOnBack()
        freezeMenu.hidden {
            if (!submitted) Call.serverPacketReliable("silentThaw", playerId)
        }
        freezeMenu.show()
    }

    override fun handleMute(p: Player) {
        if (!p.serverModerationStateKnown) {
            getStats(p, true)
            return
        }

        if (p.serverMuted) {
            ui.showConfirm("@confirm", Core.bundle.format("client.confirmunmute", p.coloredName())) {
                unmute(p)
            }
            return
        }

        val playerId = p.id.toString()
        Call.serverPacketReliable("silentMute", playerId)

        val muteMenu = BaseDialog("Mute ${p.coloredName()}")
        muteMenu.setFillParent(false)
        var submitted = false

        muteMenu.cont.add("Reason:").left()
        muteMenu.cont.row()
        val reasonField = muteMenu.cont.field("Breaking chatting rules.") {}.width(400f).get()
        muteMenu.cont.row()
        muteMenu.cont.table { durations ->
            moderationDurations.forEach { duration ->
                durations.button(duration) {
                    submitted = true
                    mute(p, duration, reasonField.text)
                    muteMenu.hide()
                }.size(70f, 50f).pad(2f)
            }
        }.pad(4f)

        muteMenu.buttons.button("@cancel", Icon.cancel, muteMenu::hide).size(200f, 54f).pad(2f)
        muteMenu.closeOnBack()
        muteMenu.hidden {
            if (!submitted) Call.serverPacketReliable("silentUnmute", playerId)
        }
        muteMenu.show()
    }

    override fun adminui() = player.admin || ClientVars.rank >= 5

    override fun handleButtons(msg: ChatMessage) {
        super.handleButtons(msg)
        val message = msg.message
        val playerCodeMatches = codeRegex.findAll(message)
        playerCodeMatches.forEach { match ->
            match.groupValues[0].let { code ->
                msg.addButton(code) { Core.app.setClipboardText(code) }
            }
        }
        if (defense() && Core.bundle.get("client.io.shop-vote") in message) { // td upgrade voting
            val agree = Companion.Cmd("/agree", 0)
            msg.addButton(agree.str, agree::invoke)
            val disagree = Companion.Cmd("/disagree", 0)
            msg.addButton(disagree.str, disagree::invoke)
        }
    }

    override fun isJoinedServer(group: List<String>?, host: Host?) = host?.name?.contains("Corium") == true

    /** Gets player stats if needed. Otherwise, only requests the player code for serverID usage. */
    override fun getStats(player: Player, force: Boolean) = Call.serverPacketReliable(if (Core.settings.getBool("autostats") || force) "playerdata_by_id" else "getPlayerCodeById", player.id.toString())

    override fun updateRank() {
        ClientVars.ratelimitMax = ratelimitMax
        ClientVars.ratelimitRemaining = ratelimitMax
    }
}




fun handleKick(reason: String) {
    Log.debug("Kicked from server '${ui.join.lastHost?.name ?: "unknown"}' for: '$reason'.")
}