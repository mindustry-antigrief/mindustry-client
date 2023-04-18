package mindustry.client.claj

import arc.*
import arc.func.*
import arc.net.*
import arc.net.Server
import arc.scene.*
import arc.scene.ui.*
import arc.struct.*
import arc.util.*
import arc.util.serialization.*
import arc.util.serialization.Json.*
import mindustry.*
import mindustry.client.claj.ClajManagerDialog.*
import mindustry.client.utils.*
import mindustry.game.*
import mindustry.game.EventType.*
import mindustry.gen.*
import mindustry.io.*
import mindustry.net.ArcNetProvider.*
import mindustry.net.Net.*
import java.io.*
import java.nio.*

/** Implements the protocol defined in https://github.com/xzxADIxzx/Scheme-Size/blob/main/src/java/scheme/ClajIntegration.java */
object ClajSupport {
    val clients: Seq<Client> = Seq(false, 0)
    private lateinit var dispatchListener: NetListener
    private var roomInt = 1

    fun load() {
        Events.on(MenuReturnEvent::class.java) { clear() }

        val prov = Reflect.get<NetProvider>(Vars.net, "provider").let { if (Vars.steam) Reflect.get(it, "provider") else it }
        val serv = Reflect.get<Server>(prov, "server")
        dispatchListener = Reflect.get(serv, "dispatchListener")
    }

    @Throws(IOException::class)
    @JvmStatic
    fun createRoom(ip: String, port: Int, room: Room?, code: String? = null): Client {
        val client = Client(8192, 8192, ClajSerializer())
        Threads.daemon("Claj Room ${roomInt++}", client)

        client.addListener(dispatchListener)
        client.addListener(object : NetListener {
            var clientInt = 1
            override fun connected(connection: Connection?) {
                if (code == null) client.sendTCP("new")
                else client.sendTCP("generate $code")
            }

            override fun disconnected(connection: Connection?, reason: DcReason?) {
                room?.close() ?: client.close()
            }

            override fun received(connection: Connection?, obj: Any?) {
                room ?: return
                if (obj is String) { // Cursed custom json packets
                    if (obj.startsWith("CLaJ")) {
                        room.link = "$obj#$ip:$port"
                        room.find<Label>("link").setText(room.link)
                    } else if (obj == "new") {
                        try {
                            createRedirector(ip, port, room.link.substringBefore('#'), roomInt, clientInt++)
                        } catch (e: Exception) {
                            Log.err("Exception caught while creating redirector", e)
                        }
                    }
                    else Call.sendMessage(obj)
                }
            }
        })

        client.connect(5000, ip, port, port)
        clients.add(client)

        return client
    }

    /** Creates a new redirector connection for the room */
    private fun createRedirector(ip: String, port: Int, key: String, roomInt: Int, clientInt: Int) {
        val client = Client(8192, 8192, ClajSerializer())
        Threads.daemon("Claj Room $roomInt Client $clientInt", client)

        client.addListener(dispatchListener)
        client.addListener(object : NetListener {
            override fun connected(connection: Connection?) {
                client.sendTCP("host$key")
            }
        })

        client.connect(5000, ip, port, port)
        clients.add(client)
    }

    @JvmStatic
    fun joinRoom(ip: String, port: Int, key: String, success: Runnable) {
        Vars.logic.reset()
        Vars.net.reset()

        Vars.netClient.beginConnecting()
        Log.debug("Joining room")
        Vars.net.connect(ip, port) {
            Log.debug("Room joined")
            if (!Vars.net.client()) return@connect
            success.run()

            val buffer = ByteBuffer.allocate(8192)
            buffer.put(ClajSerializer.linkID)
            TypeIO.writeString(buffer, "join$key")

            (buffer.limit(buffer.position()) as Buffer).position(0) // Hack to make this work on java 8
            Vars.net.send(buffer, true)
        }
    }

    fun clear() {
        clients.each { it.close() }
        clients.clear().shrink()
    }

    @Throws(IOException::class)
    @JvmStatic
    fun parseLink(link: String): Link {
        val trimmed = link.trim()

        if (!trimmed.startsWith("CLaJ")) throw IOException("@client.claj.missingprefix")
        val hash = trimmed.indexOf('#')
        if (hash != 42 + 4) throw IOException("@client.claj.wrongkeylength")
        val colon = trimmed.indexOf(':')
        if (colon == -1) throw IOException("@client.claj.colonnotfound")

        val port = Strings.parseInt(trimmed.substringAfter(':'))
        if (port == Int.MIN_VALUE) throw IOException("@client.claj.portnotfound")

        return Link(trimmed.substring(0, hash), link.substring(hash + 1, colon), port)
    }

    data class Link(@JvmField val key: String, @JvmField val ip: String, @JvmField val port: Int)
}


class ClajSerializer : PacketSerializer() {
    override fun write(buffer: ByteBuffer, obj: Any) {
        if (obj is String) {
            buffer.put(linkID)
            TypeIO.writeString(buffer, obj)
        } else super.write(buffer, obj)
    }

    override fun read(buffer: ByteBuffer): Any {
        if (buffer.get() == linkID) return TypeIO.readString(buffer)
        buffer.position(buffer.position() - 1)
        return super.read(buffer)
    }

    companion object {
        const val linkID: Byte = -3
    }
}
