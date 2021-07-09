package mindustry.client

import arc.*
import arc.math.geom.*
import arc.struct.*
import arc.util.*
import mindustry.*
import mindustry.client.antigrief.*
import mindustry.client.communication.*
import mindustry.client.crypto.*
import mindustry.client.navigation.*
import mindustry.client.ui.*
import mindustry.client.utils.*
import mindustry.entities.units.*
import mindustry.game.*
import mindustry.input.*

object Main : ApplicationListener {
    lateinit var communicationSystem: SwitchableCommunicationSystem
    lateinit var messageCrypto: MessageCrypto
    lateinit var communicationClient: Packets.CommunicationClient
    private var dispatchedBuildPlans = mutableListOf<BuildPlan>()
    private val buildPlanInterval = Interval()

    /** Run on client load. */
    override fun init() {
        Crypto.initializeAlways()
        if (Core.app.isDesktop) {
            communicationSystem = SwitchableCommunicationSystem(MessageBlockCommunicationSystem)
            communicationSystem.init()

            TileRecords.initialize()
        } else {
            communicationSystem = SwitchableCommunicationSystem(DummyCommunicationSystem(mutableListOf()))
            communicationSystem.init()
        }
        communicationClient = Packets.CommunicationClient(communicationSystem)
        messageCrypto = MessageCrypto()
        messageCrypto.init(communicationClient)
        KeyFolder.initializeAlways()

        Navigation.navigator = AStarNavigator

        Events.on(EventType.WorldLoadEvent::class.java) {
            dispatchedBuildPlans.clear()
        }
        Events.on(EventType.ServerJoinEvent::class.java) {
//            if (Groups.build.contains { it is LogicBlock.LogicBuild && it.code.startsWith(ProcessorCommunicationSystem.PREFIX) }) {
                communicationSystem.activeCommunicationSystem = MessageBlockCommunicationSystem
//            } else {
//                communicationSystem.activeCommunicationSystem = MessageBlockCommunicationSystem
//            }
        }

        communicationClient.addListener { transmission, senderId ->
            when (transmission) {
                is BuildQueueTransmission -> {
                    if (senderId == communicationSystem.id) return@addListener
                    val path = Navigation.currentlyFollowing as? BuildPath ?: return@addListener
                    if (path.queues.contains(path.networkAssist)) {
                        val positions = IntSet()
                        for (plan in path.networkAssist) positions.add(Point2.pack(plan.x, plan.y))

                        for (plan in transmission.plans.sortedByDescending { it.dst(Vars.player) }) {
                            if (path.networkAssist.size > 1000) return@addListener  // too many plans, not accepting new ones
                            if (positions.contains(Point2.pack(plan.x, plan.y))) continue
                            path.networkAssist.add(plan)
                        }
                    }
                }
            }
        }
    }

    /** Run once per frame. */
    override fun update() {
        communicationClient.update()

        if (Core.scene.keyboardFocus == null && Core.input?.keyTap(Binding.send_build_queue) == true) {
            ClientVars.dispatchingBuildPlans = !ClientVars.dispatchingBuildPlans
        }

        if (ClientVars.dispatchingBuildPlans && !communicationClient.inUse && buildPlanInterval.get(5 * 60f)) {
            sendBuildPlans()
        }
    }

    fun setPluginNetworking(enable: Boolean) {
        when {
            enable -> {
                communicationSystem.activeCommunicationSystem = MessageBlockCommunicationSystem //FINISHME: Re-implement packet plugin
            }
            Core.app?.isDesktop == true -> {
                communicationSystem.activeCommunicationSystem = MessageBlockCommunicationSystem
            }
            else -> {
                communicationSystem.activeCommunicationSystem = DummyCommunicationSystem(mutableListOf())
            }
        }
    }

    fun floatEmbed(): Vec2 {
        return when {
            Navigation.currentlyFollowing is AssistPath && Core.settings.getBool("displayasuser") ->
                Vec2(
                    FloatEmbed.embedInFloat(Vars.player.unit().aimX, ClientVars.FOO_USER),
                    FloatEmbed.embedInFloat(Vars.player.unit().aimY, ClientVars.ASSISTING)
                )
            Navigation.currentlyFollowing is AssistPath ->
                Vec2(
                    FloatEmbed.embedInFloat(Vars.player.unit().aimX, ClientVars.ASSISTING),
                    FloatEmbed.embedInFloat(Vars.player.unit().aimY, ClientVars.ASSISTING)
                )
            Core.settings.getBool("displayasuser") ->
                Vec2(
                    FloatEmbed.embedInFloat(Vars.player.unit().aimX, ClientVars.FOO_USER),
                    FloatEmbed.embedInFloat(Vars.player.unit().aimY, ClientVars.FOO_USER)
                )
            else -> Vec2(Vars.player.unit().aimX, Vars.player.unit().aimY)
        }
    }

    private fun sendBuildPlans(num: Int = 500) {
        val toSend = Vars.player.unit().plans.toList().takeLast(num).toTypedArray()
        if (toSend.isEmpty()) return
        communicationClient.send(BuildQueueTransmission(toSend), { Toast(3f).add(Core.bundle.format("client.sentplans", toSend.size)) }, { Toast(3f).add("@client.nomessageblock")})
        dispatchedBuildPlans.addAll(toSend)
    }

    /** Run when the object is disposed. */
    override fun dispose() {}
}
