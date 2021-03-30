package com.github.blahblahbloopster

import arc.*
import arc.math.geom.*
import arc.struct.*
import arc.util.*
import com.github.blahblahbloopster.antigrief.TileRecords
import com.github.blahblahbloopster.communication.*
import com.github.blahblahbloopster.crypto.*
import com.github.blahblahbloopster.navigation.*
import mindustry.*
import mindustry.client.*
import mindustry.client.Client.*
import mindustry.client.navigation.*
import mindustry.client.ui.*
import mindustry.entities.units.*
import mindustry.game.*
import mindustry.input.*

object Main : ApplicationListener {
    lateinit var communicationSystem: SwitchableCommunicationSystem
    lateinit var messageCrypto: MessageCrypto
    lateinit var communicationClient: Packets.CommunicationClient
    private var dispatchedBuildPlans = mutableListOf<BuildPlan>()
    private val buildPlanInterval = Interval(2)

    init {
        mapping = ClientMapping()
    }

    /** Run on client load. */
    override fun init() {
        Crypto.initializeAlways()
        if (Core.app.isDesktop) {
            communicationSystem = SwitchableCommunicationSystem(MessageBlockCommunicationSystem, PluginCommunicationSystem)
            communicationSystem.init()

            ClientVars.clientCommandHandler.register("e", "<destination> <message...>", "Send an encrypted chat message") { args ->
                val dest = args[0]
                val message = args[1]

                for (key in messageCrypto.keys) {
                    if (key.name.equals(dest, true)) {
                        messageCrypto.encrypt(message, key)
                        return@register
                    }
                }
                Toast(3f).add("@client.invalidkey")
            }
            TileRecords.initialize()
        } else {
            communicationSystem = SwitchableCommunicationSystem(DummyCommunicationSystem(mutableListOf()))
            communicationSystem.init()
        }
        messageCrypto = MessageCrypto()
        initializeCommunication(communicationSystem)
        messageCrypto.init(communicationClient)
        KeyFolder.initializeAlways()

        Navigation.navigator = AStarNavigator

        Events.on(EventType.WorldLoadEvent::class.java) {
            dispatchedBuildPlans.clear()
        }
        Events.on(EventType.ServerJoinEvent::class.java) {
            communicationSystem.activeCommunicationSystem = MessageBlockCommunicationSystem
            communicationSystem.init()
            initializeCommunication(communicationSystem)
        }
    }

    private fun initializeCommunication(communicationSystem: CommunicationSystem) {
        communicationSystem.clearListeners()
        this.messageCrypto = MessageCrypto()
        communicationClient = Packets.CommunicationClient(communicationSystem)
        communicationClient.addListener { transmission, senderId ->
            when (transmission) {
                is BuildQueueTransmission -> {
                    if (senderId == Main.communicationSystem.id) return@addListener
                    val path = Navigation.currentlyFollowing as? BuildPath ?: return@addListener
                    if (path.queues.contains(path.networkAssist)) {
                        val positions = IntSet()
                        for (plan in path.networkAssist) positions.add(Point2.pack(plan.x, plan.y))

                        for (plan in transmission.plans.sortedByDescending { it.dst(Vars.player) }) {
                            if (path.networkAssist.size > 500) return@addListener  // too many plans, not accepting new ones
                            if (positions.contains(Point2.pack(plan.x, plan.y))) continue
                            path.networkAssist.add(plan)
                        }
                    }
                }
            }
        }
        messageCrypto.init(communicationClient)
    }

    /** Run once per frame. */
    override fun update() {
        communicationClient.update()

        if (Core.scene.keyboardFocus == null && Core.input?.keyTap(Binding.send_build_queue) == true) {
            ClientVars.dispatchingBuildPlans = !ClientVars.dispatchingBuildPlans
        }

        if (ClientVars.dispatchingBuildPlans && !communicationClient.inUse && buildPlanInterval.get(10 * 60f)) {
            sendBuildPlans()
        }
    }

    private fun sendBuildPlans(num: Int = 300) {
        val toSend = Vars.player.unit().plans.toList().takeLast(num).toTypedArray()
        if (toSend.isEmpty()) return
        communicationClient.send(BuildQueueTransmission(toSend), { Toast(3f).add(Core.bundle.format("client.sentplans", toSend.size)) }, { Toast(3f).add("@client.nomessageblock")})
        dispatchedBuildPlans.addAll(toSend)
    }

    /** Run when the object is disposed. */
    override fun dispose() {}
}
