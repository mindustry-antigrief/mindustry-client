package com.github.blahblahbloopster

import arc.math.geom.Vec2
import arc.struct.Queue
import arc.util.CommandHandler
import arc.util.Ratekeeper
import mindustry.client.ClientMode
import mindustry.client.ClientVars
import mindustry.client.antigrief.ConfigRequest

object ClientVarsImpl : ClientVars {

    private var mode: ClientMode = ClientMode.normal
    private var configs: Queue<ConfigRequest> = Queue()
    private var showingTurrets: Boolean = false
    private var hideUnits: Boolean = false
    private var hidingBlocks: Boolean = false
    private var dispatchingBuildPlans: Boolean = false
    private var lastSyncTime: Long = 0L
    private val fooCommands = CommandHandler("!")
    private val configRateLimit = Ratekeeper()
    private var lastSentPos: Vec2 = Vec2()

    override fun getMode() = mode
    override fun setMode(mode: ClientMode) { this.mode = mode }

    override fun getConfigs() = configs

    override fun getShowingTurrets() = showingTurrets
    override fun setShowingTurrets(showingTurrets: Boolean) { this.showingTurrets = showingTurrets }

    override fun getHideUnits() = hideUnits
    override fun setHideUnits(hideUnits: Boolean) { this.hideUnits = hideUnits }

    override fun getHidingBlocks() = hidingBlocks
    override fun setHidingBlocks(hidingBlocks: Boolean) { this.hidingBlocks = hidingBlocks }

    override fun getDispatchingBuildPlans() = dispatchingBuildPlans
    override fun setDispatchingBuildPlans(dispatchingBuildPlans: Boolean) { this.dispatchingBuildPlans = dispatchingBuildPlans }

    override fun getLastSyncTime() = lastSyncTime
    override fun setLastSyncTime(lastSyncTime: Long) { this.lastSyncTime = lastSyncTime }

    override fun getFooCommands() = fooCommands

    override fun getConfigRateLimit() = configRateLimit

    override fun getLastSentPos() = lastSentPos

    override fun getMessageBlockCommunicationPrefix() = "IN USE FOR CHAT AUTHENTICATION, do not use"

    override fun getMapping() = ClientMapping()

    override fun getFooUser() = 0b10101010.toByte()

    override fun getAssisting() = 0b01010101.toByte()
}
