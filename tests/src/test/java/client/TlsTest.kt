package client

import mindustry.*
import mindustry.client.communication.*
import mindustry.client.crypto.*
import mindustry.client.utils.*
import org.bouncycastle.jce.provider.*
import org.bouncycastle.jsse.provider.*
import org.bouncycastle.tls.*
import org.junit.jupiter.api.*
import java.security.*
import java.util.concurrent.atomic.*
import kotlin.random.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TlsTest {

    @BeforeAll
    fun initialize() {
        val bc = BouncyCastleProvider()
        Security.addProvider(bc)
        Security.addProvider(BouncyCastleJsseProvider(bc))

        provider.setProvider(bc)

        Vars.loadLogger()
    }

    @Test
    fun testTls() {
//        val caKey = genKey()
//        val caCert = genCert(caKey, null, "ca")

        val clientKey = genKey()
        val clientCert = genCert(clientKey, null, "clientnamehere")

        val serverKey = genKey()
        val serverCert = genCert(serverKey, null, "servernamehere")

        val clientProto = TlsClientProtocol()
        val client = TlsClientImpl(clientCert, listOf(clientCert), serverCert, clientKey.private)

        val serverProto = TlsServerProtocol()
        val server = TlsServerImpl(serverCert, listOf(serverCert), clientCert, serverKey.private)

        val handshakeDone = AtomicInteger()

        client.onHandshakeFinish = { handshakeDone.incrementAndGet() }
        server.onHandshakeFinish = { handshakeDone.incrementAndGet() }

        clientProto.connect(client)
        serverProto.accept(server)

        var passed = false

        var handshakeDoneLast = false

        for (i in 0 until 100) {
            clientProto.offerInput(serverProto.pollOutput())
            serverProto.offerInput(clientProto.pollOutput())

            if (handshakeDoneLast) {
                val avail = serverProto.applicationDataAvailable()
                if (avail == 0) continue
                val arr = ByteArray(avail)
                serverProto.readApplicationData(arr, 0, avail)
                passed = arr.decodeToString() == "Hello, world!"
            }

            if (handshakeDone.get() == 2 && !handshakeDoneLast) {
                val out = "Hello, world!".encodeToByteArray()
                clientProto.writeApplicationData(out, 0, out.size)
                handshakeDoneLast = true
            }
        }

        Assertions.assertTrue(passed)
    }

    @Test
    fun testEscape() {
        val escape = '\\'
        val toEscape = "+-"

        val original = "aaa\\aaaa+aaa-aaa--++"
        val escaped = original.toCharArray().toList().escape(escape, *toEscape.toCharArray().toTypedArray())
        val unescaped = escaped.unescape(escape, *toEscape.toCharArray().toTypedArray()).joinToString("")
        Assertions.assertTrue(original == unescaped)
    }

    @Test
    fun testTlsComms() {

//        val caKey = genKey()
//        val caCert = genCert(caKey, null, "ca")

        val clientKey = genKey()
        val clientCert = genCert(clientKey, null, "clientnamehere")

        val serverKey = genKey()
        val serverCert = genCert(serverKey, null, "servernamehere")

        val pool = mutableListOf<DummyCommunicationSystem>()

        val clientUnderlying = Packets.CommunicationClient(DummyCommunicationSystem(pool))
        val serverUnderlying = Packets.CommunicationClient(DummyCommunicationSystem(pool))

        val clientPeer = TlsClientHolder(clientCert, listOf(clientCert), serverCert, clientKey.private)
        val clientComms = TlsCommunicationSystem(clientPeer, clientUnderlying, clientCert)
        val clientSecured = Packets.CommunicationClient(clientComms)

        val serverPeer = TlsServerHolder(serverCert, listOf(serverCert), clientCert, serverKey.private)
        val serverComms = TlsCommunicationSystem(serverPeer, serverUnderlying, serverCert)
        val serverSecured = Packets.CommunicationClient(serverComms)

        var gotten: ByteArray? = null

        for (i in 0 until 200) {
            clientComms.update()
            serverComms.update()
        }

        val bytes = Random.Default.nextBytes(1234)

        clientSecured.send(DummyTransmission(bytes))
        serverSecured.addListener { transmission, _ ->
            val content = (transmission as DummyTransmission).content
            gotten = content
        }
        for (i in 0 until 500) {
            clientComms.update()
            serverComms.update()

            clientSecured.update()
            serverSecured.update()
        }

        Assertions.assertArrayEquals(gotten, bytes)
    }
}
