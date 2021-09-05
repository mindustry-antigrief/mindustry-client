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
import mindustry.game.Teams.*
import mindustry.gen.*
import mindustry.input.*
import java.nio.file.Files
import java.security.cert.*
import java.util.Timer
import java.util.concurrent.*
import kotlin.concurrent.*
import kotlin.random.Random

object Main : ApplicationListener {
    private lateinit var communicationSystem: SwitchableCommunicationSystem
    private lateinit var communicationClient: Packets.CommunicationClient
    private var dispatchedBuildPlans = mutableListOf<BuildPlan>()
    private val buildPlanInterval = Interval()
    val tlsPeers = CopyOnWriteArrayList<Pair<Packets.CommunicationClient, TlsCommunicationSystem>>()
    lateinit var keyStorage: KeyStorage
    lateinit var signatures: Signatures
    lateinit var ntp: NTP

    /** Run on client load. */
    override fun init() {
        if (Core.app.isDesktop) {
            ntp = NTP()
            communicationSystem = SwitchableCommunicationSystem(MessageBlockCommunicationSystem)
            communicationSystem.init()

            keyStorage = KeyStorage(Core.settings.dataDirectory.file())
            signatures = Signatures(keyStorage, ntp.clock)

            TileRecords.initialize()
        } else {
            keyStorage = KeyStorage(Files.createTempDirectory("keystorage").toFile())
            communicationSystem = SwitchableCommunicationSystem(DummyCommunicationSystem(mutableListOf()))
            communicationSystem.init()
        }
        communicationClient = Packets.CommunicationClient(communicationSystem)

        Navigation.navigator = AStarNavigator

        Events.on(EventType.WorldLoadEvent::class.java) {
            dispatchedBuildPlans.clear()
        }
        Events.on(EventType.ServerJoinEvent::class.java) {
                communicationSystem.activeCommunicationSystem = MessageBlockCommunicationSystem
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

                is TlsRequestTransmission -> {
                    val cert = keyStorage.cert() ?: return@addListener
                    if (transmission.destinationSN != cert.serialNumber) return@addListener

                    val key = keyStorage.key() ?: return@addListener
                    val chain = keyStorage.chain() ?: return@addListener
                    val expected = keyStorage.findTrusted(transmission.sourceSN) ?: return@addListener

                    val peer = TlsClientHolder(cert, chain, expected, key)
                    val comms = TlsCommunicationSystem(peer, communicationClient, cert)
                    val commsClient = Packets.CommunicationClient(comms)

                    registerTlsListeners(commsClient, comms)

                    tlsPeers.add(Pair(commsClient, comms))
                }

                // tls peers handle data transmissions internally

                is SignatureTransmission -> {
                    var isValid = check(transmission)
                    next(EventType.PlayerChatEventClient::class.java, repetitions = 3) {
                        if (isValid) return@next
                        isValid = check(transmission)
                    }
                }
            }
        }
    }

    /**
     * returns if it's done or not, NOT if it's valid
     */
    private fun check(transmission: SignatureTransmission): Boolean {
        val ending = InvisibleCharCoder.encode(transmission.messageId.toBytes())
        val msg = Vars.ui.chatfrag.messages.lastOrNull { it.message.endsWith(ending) } ?: return false
        if (!Core.settings.getBool("highlightcryptomsg")) return true
        val output = signatures.verifySignatureTransmission(msg.message.encodeToByteArray(), transmission)
        when (output.first) {
            Signatures.VerifyResult.VALID -> {
                msg.sender = output.second?.readableName
                msg.backgroundColor = ClientVars.verified
                msg.prefix = "${Iconc.ok} "
                msg.format()
                return true
            }
            Signatures.VerifyResult.INVALID -> {
                msg.sender = output.second?.readableName?.stripColors()?.plus("[scarlet] impersonator")
                msg.backgroundColor = ClientVars.invalid
                msg.prefix = "${Iconc.cancel} "
                msg.format()
                return true
            }
            Signatures.VerifyResult.UNKNOWN_CERT -> {
                return true
            }
        }
    }

    fun sign(content: String): String {
        if (!Core.settings.getBool("signmessages")) return content
        if (content.startsWith("/")) return content
        val msgId = Random.nextInt().toShort()
        val contentWithId = content + InvisibleCharCoder.encode(msgId.toBytes())
        communicationClient.send(signatures.signatureTransmission(
            contentWithId.encodeToByteArray(),
            communicationSystem.id,
            msgId) ?: return content)
        return contentWithId
    }

    /** Run once per frame. */
    override fun update() {
        communicationClient.update()

        if (Core.scene.keyboardFocus == null && Core.input?.keyTap(Binding.send_build_queue) == true) {
            ClientVars.dispatchingBuildPlans = !ClientVars.dispatchingBuildPlans
        }

        if (ClientVars.dispatchingBuildPlans) {
            if (!Vars.net.client()) Vars.player.unit().plans.each { addBuildPlan(it) } // Player plans -> block ghosts in single player
            if (!communicationClient.inUse && Groups.player.size() > 1 && buildPlanInterval.get(5 * 60f)) sendBuildPlans()
        }

        for (peer in tlsPeers) {
            if (peer.second.isClosed) tlsPeers.remove(peer)
            peer.second.update()
            peer.first.update()
        }
    }

    fun connectTls(dstCert: X509Certificate, onFinish: ((Packets.CommunicationClient) -> Unit)? = null, onError: (() -> Unit)? = null) {
        val cert = keyStorage.cert() ?: return
        val key = keyStorage.key() ?: return
        val chain = keyStorage.chain() ?: return

        val peer = TlsServerHolder(cert, chain, dstCert, key)
        val comms = TlsCommunicationSystem(peer, communicationClient, cert)

        val commsClient = Packets.CommunicationClient(comms)
        registerTlsListeners(commsClient, comms)

        peer.onHandshakeFinish = {
            onFinish?.invoke(commsClient)
        }

        communicationClient.send(TlsRequestTransmission(cert.serialNumber, dstCert.serialNumber), onError = onError)
        Timer().schedule(500L) { tlsPeers.add(Pair(commsClient, comms)) }
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

    private fun addBuildPlan(plan: BuildPlan) {
        if (plan.breaking) return
        if (plan.isDone) {
            Vars.player.unit().plans.remove(plan)
            return
        }

        val data: TeamData = Vars.player.team().data()
        for (i in 0 until data.blocks.size) {
            val b = data.blocks[i]
            if (b.x == plan.x.toShort() && b.y == plan.y.toShort()) {
                data.blocks.removeIndex(i)
                break
            }
        }
        data.blocks.addFirst(BlockPlan(plan.x, plan.y, plan.rotation.toShort(), plan.block.id, plan.config))
    }

    private fun registerTlsListeners(commsClient: Packets.CommunicationClient, system: TlsCommunicationSystem) {
        commsClient.addListener { transmission, _ ->
            when (transmission) {
                is MessageTransmission -> {
                    ClientVars.lastCertName = system.peer.expectedCert.readableName
                    Vars.ui.chatfrag.addMessage(transmission.content, system.peer.expectedCert.readableName + "[] -> " + (keyStorage.cert()?.readableName ?: "you"), ClientVars.encrypted).prefix = "${Iconc.ok} "
                }
            }
        }
    }

    /** Run when the object is disposed. */
    override fun dispose() {}
}
