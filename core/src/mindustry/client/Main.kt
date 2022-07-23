package mindustry.client

import arc.*
import arc.graphics.*
import arc.math.geom.*
import arc.scene.ui.*
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
import mindustry.ui.fragments.*
import java.nio.file.Files
import java.security.cert.*
import java.util.Timer
import java.util.concurrent.*
import kotlin.concurrent.*
import kotlin.math.*
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
    private var planSendTime = 0L
    private var isSendingPlans = false

    /** Run on client load. */
    override fun init() {
        if (Core.app.isDesktop) {
            ntp = NTP()
            communicationSystem = SwitchableCommunicationSystem(BlockCommunicationSystem, PluginCommunicationSystem)
            communicationSystem.init()

            keyStorage = KeyStorage(Core.settings.dataDirectory.file())
            signatures = Signatures(keyStorage, ntp.clock)

            TileRecords.initialize()
        } else {
            keyStorage = KeyStorage(Files.createTempDirectory("keystorage").toFile())
            communicationSystem = SwitchableCommunicationSystem(DummyCommunicationSystem(mutableListOf()))
            communicationSystem.init()
        }

        Core.settings.getBoolOnce("displaydef") {
            Core.settings.put("displayasuser", true)
        }

        communicationClient = Packets.CommunicationClient(communicationSystem)

        Navigation.navigator = AStarNavigator

        Events.on(EventType.WorldLoadEvent::class.java) {
            if (!Vars.net.client()) { // This is so scuffed but shh
                setPluginNetworking(false)
                Call.serverPacketReliable("fooCheck", "")
            }
            dispatchedBuildPlans.clear()
        }

        Events.on(EventType.ServerJoinEvent::class.java) {
            communicationSystem.activeCommunicationSystem = BlockCommunicationSystem
            setPluginNetworking(false)
            Call.serverPacketReliable("fooCheck", "") // Request version info FINISHME: The server should just send this info on join
        }

        Vars.netClient.addPacketHandler("fooCheck") { version ->
            Log.debug("Server using client plugin version $version")
            if (!Strings.canParseInt(version)) return@addPacketHandler

            ClientVars.pluginVersion = Strings.parseInt(version)
            setPluginNetworking(true)
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

                is CommandTransmission -> {
                    transmission.type ?: return@addListener
                    if (transmission.verify()) transmission.type.lambda(transmission)
                }

                is ClientMessageTransmission -> {
                    if (senderId != Vars.player.id) transmission.addToChatfrag()
                }

                is ImageTransmission -> {
                    val msg = findMessage(transmission.message) ?: return@addListener
                    msg.attachments.add(Image(Texture(transmission.image)))
                }
            }
        }
    }

    private fun findMessage(id: Short): ChatFragment.ChatMessage? {
        val ending = InvisibleCharCoder.encode(id.toBytes())
        return Vars.ui.chatfrag.messages.lastOrNull { it.unformatted?.endsWith(ending) == true }
    }

    /** @return if it's done or not, NOT if it's valid */
    private fun check(transmission: SignatureTransmission): Boolean {
        fun invalid(msg: ChatFragment.ChatMessage, cert: X509Certificate?) {
            msg.sender = cert?.run { keyStorage.aliasOrName(this) }?.stripColors()?.plus("[scarlet] impersonator") ?: "Verification failed"
            msg.backgroundColor = ClientVars.invalid
            msg.prefix = "${Iconc.cancel} ${msg.prefix} "
            msg.format()
        }

        val msg = findMessage(transmission.messageId) ?: return false

        if (!msg.message.endsWith(msg.unformatted)) { invalid(msg, null); Log.debug("Does not end with unformatted!") }

        if (!Core.settings.getBool("highlightcryptomsg")) return true
        val output = signatures.verifySignatureTransmission(msg.unformatted.encodeToByteArray(), transmission)

        return when (output.first) {
            Signatures.VerifyResult.VALID -> {
                msg.sender = output.second?.run { keyStorage.aliasOrName(this) }
                msg.backgroundColor = ClientVars.verified
                msg.prefix = "${Iconc.ok} ${msg.prefix} "
                msg.format()
                true
            }
            Signatures.VerifyResult.INVALID -> {
                invalid(msg, output.second)
                true
            }
            Signatures.VerifyResult.UNKNOWN_CERT -> {
                true
            }
        }
    }

    fun sign(content: String): String {
        if (content.startsWith("/") && !(content.startsWith("/t ") || content.startsWith("/a "))) return content

        val msgId = Random.nextInt().toShort()
        val contentWithId = content + InvisibleCharCoder.encode(msgId.toBytes())

        communicationClient.send(signatures.signatureTransmission(
            contentWithId.replace("^/[t|a] ".toRegex(), "").encodeToByteArray(),
            communicationSystem.id,
            msgId) ?: return contentWithId)

        return contentWithId
    }

    /** Run once per frame. */
    override fun update() {
        communicationClient.update()

        if (Core.scene.keyboardFocus == null && Core.input?.keyTap(Binding.send_build_queue) == true) {
            ClientVars.dispatchingBuildPlans = !ClientVars.dispatchingBuildPlans
        }

        if (ClientVars.dispatchingBuildPlans) {
            if (!Vars.net.client()) Vars.player.unit().plans.each { if (BuildPlanCommunicationSystem.isNetworking(it)) return@each; addBuildPlan(it) } // Player plans -> block ghosts in single player
            if (!isSendingPlans && !communicationClient.inUse && Groups.player.size() > 1 && buildPlanInterval.get(max(5 * 60f, planSendTime / 16.666f + 3 * 60))) sendBuildPlans()
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
        if (!enable) ClientVars.pluginVersion = -1
        when {
            enable -> {
                communicationSystem.activeCommunicationSystem = PluginCommunicationSystem
            }
            Core.app?.isDesktop == true -> {
                communicationSystem.activeCommunicationSystem = BlockCommunicationSystem
            }
            else -> {
                communicationSystem.activeCommunicationSystem = DummyCommunicationSystem(mutableListOf())
            }
        }
    }

    fun send(transmission: Transmission, onFinish: (() -> Unit)? = null) {
        communicationClient.send(transmission, onFinish)
    }

    /** Uses [Tmp.v1], do not cache returned vec or call this function on non-main thread. */
    fun floatEmbed(): Vec2 {
        val show = Core.settings.getBool("displayasuser")
        return when {
            Navigation.currentlyFollowing is AssistPath && show ->
                Tmp.v1.set(
                    FloatEmbed.embedInFloat(Vars.player.unit().aimX, ClientVars.FOO_USER),
                    FloatEmbed.embedInFloat(Vars.player.unit().aimY, ClientVars.ASSISTING)
                )
            Navigation.currentlyFollowing is AssistPath ->
                Tmp.v1.set(
                    FloatEmbed.embedInFloat(Vars.player.unit().aimX, ClientVars.ASSISTING),
                    FloatEmbed.embedInFloat(Vars.player.unit().aimY, ClientVars.ASSISTING)
                )
            show ->
                Tmp.v1.set(
                    FloatEmbed.embedInFloat(Vars.player.unit().aimX, ClientVars.FOO_USER),
                    FloatEmbed.embedInFloat(Vars.player.unit().aimY, ClientVars.FOO_USER)
                )
            else -> Tmp.v1.set(Vars.player.unit().aimX, Vars.player.unit().aimY)
        }
    }

    private fun sendBuildPlans(num: Int = 500) {
        var count = 0
        val toSend = Vars.player.unit().plans.toList().takeLastWhile { !BuildPlanCommunicationSystem.isNetworking(it) && count++ < num }.toTypedArray()
        if (toSend.isEmpty()) return
        isSendingPlans = true
        val start = Time.millis()
        communicationClient.send(BuildQueueTransmission(toSend), { isSendingPlans = false; planSendTime = Time.timeSinceMillis(start); Toast(3f).add(Core.bundle.format("client.sentplans", toSend.size)) }, { Toast(3f).add("@client.nomessageblock")})
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
                    Vars.ui.chatfrag.addMessage(transmission.content, "[white]" + keyStorage.aliasOrName(system.peer.expectedCert) + "[accent] -> [coral]" + (keyStorage.cert()?.readableName ?: "you"), ClientVars.encrypted).run{ prefix = "${Iconc.ok} $prefix " }
                }

                is CommandTransmission -> {
                    transmission.type ?: return@addListener
                    if (transmission.verify()) transmission.type.lambda(transmission)
                }
            }
        }
    }

    /** Run when the object is disposed. */
    override fun dispose() {}
}
