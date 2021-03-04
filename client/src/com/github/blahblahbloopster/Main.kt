package com.github.blahblahbloopster

import arc.*
import arc.math.geom.Point2
import arc.struct.IntSet
import arc.util.Interval
import com.github.blahblahbloopster.communication.*
import com.github.blahblahbloopster.crypto.*
import com.github.blahblahbloopster.navigation.AStarNavigator
import mindustry.Vars
import mindustry.client.Client
import mindustry.client.Client.dispatchingBuildPlans
import mindustry.client.navigation.*
import mindustry.entities.units.BuildPlan
import mindustry.game.EventType
import mindustry.input.Binding

object Main : ApplicationListener {
    lateinit var communicationSystem: CommunicationSystem
    lateinit var messageCrypto: MessageCrypto
    lateinit var communicationClient: Packets.CommunicationClient
    private var dispatchedBuildPlans = mutableListOf<BuildPlan>()
    private val buildPlanInterval = Interval(2)

    /** Run on client load. */
    override fun init() {
        Client.mapping = ClientMapping()
        Crypto.initializeAlways()
        if (Core.app.isDesktop) {
            communicationSystem = MessageBlockCommunicationSystem()
            communicationSystem.init()

            Client.fooCommands.register("e", "<destination> <message...>", "Send an encrypted chat message") { args ->
                val dest = args[0]
                val message = args[1]

                for (key in messageCrypto.keys) {
                    if (key.name.equals(dest, true)) {
                        messageCrypto.encrypt(message, key)
                        return@register
                    }
                }
                Vars.ui.chatfrag.addMessage("[scarlet]Invalid key! They are listed in the \"manage keys\" section of the pause menu", null)
            }
        } else {
            communicationSystem = DummyCommunicationSystem(mutableListOf())
        }
        messageCrypto = MessageCrypto()
        communicationClient = Packets.CommunicationClient(communicationSystem)
        messageCrypto.init(communicationClient)
        KeyFolder.initializeAlways()

        Navigation.navigator = AStarNavigator

        communicationClient.addListener { transmission, senderId ->
            when (transmission) {
                is BuildQueueTransmission -> {
                    if (senderId == communicationSystem.id) return@addListener
                    val path = Navigation.currentlyFollowing as? BuildPath ?: return@addListener
                    if (path.queues.contains(path.networkAssist)) {
                        val positions = IntSet()
                        for (plan in path.networkAssist) positions.add(Point2.pack(plan.x, plan.y))

                        for (plan in transmission.plans) {
                            if (path.networkAssist.size > 500) return@addListener  // too many plans, not accepting new ones
                            if (positions.contains(Point2.pack(plan.x, plan.y))) continue
                            path.networkAssist.add(plan)
                        }
                    }
                }
            }
        }

        Events.on(EventType.WorldLoadEvent::class.java) {
            dispatchedBuildPlans.clear()
        }
    }

    /** Run once per frame. */
    override fun update() {
        communicationClient.update()

        if (Core.input?.keyTap(Binding.send_build_queue) == true) {
            dispatchingBuildPlans = !dispatchingBuildPlans
            if (!communicationClient.inUse) {
                sendBuildPlans()
            }
        }

        val lowEnough = if (buildPlanInterval.get(1, 30f)) Vars.player.unit().plans.intersect(dispatchedBuildPlans).size < 20 else false
        if (dispatchingBuildPlans && lowEnough && !communicationClient.inUse && buildPlanInterval.get(10 * 60f)) {
            sendBuildPlans()
        }
    }

    private fun sendBuildPlans(num: Int = 300) {
        val toSend = Vars.player.unit().plans.toList().takeLast(num).toTypedArray()
        if (toSend.isEmpty()) return
        communicationClient.send(BuildQueueTransmission(toSend)) {
            Vars.ui.chatfrag.addMessage("Finished sending ${toSend.size} buildplans", "client")
        }
        dispatchedBuildPlans.addAll(toSend)
    }

    /** Run when the object is disposed. */
    override fun dispose() {}
}
