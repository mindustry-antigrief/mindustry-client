package client

import mindustry.client.crypto.*
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
import org.bouncycastle.tls.TlsClientProtocol
import org.bouncycastle.tls.TlsServerProtocol
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.security.Security

class TlsTest {

    @Test
    fun testTls() {
        val bc = BouncyCastleProvider()
        Security.addProvider(bc)
        Security.addProvider(BouncyCastleJsseProvider(bc))

        provider.setProvider(bc)

        val caKey = genKey()
        val caCert = genCert(caKey, null, "ca")

        val clientKey = genKey()
        val clientCert = genCert(clientKey, Pair(caCert, caKey.private), "clientnamehere")

        val serverKey = genKey()
        val serverCert = genCert(serverKey, null, "servernamehere")

        val clientProto = TlsClientProtocol()
        val client = TlsClientImpl(clientCert, listOf(caCert), serverCert, clientKey.private)

        val serverProto = TlsServerProtocol()
        val server = TlsServerImpl(serverCert, listOf(serverCert), caCert, serverKey.private)

        clientProto.connect(client)
        serverProto.accept(server)

        var passed = false

        for (i in 0 until 100) {
            clientProto.offerInput(serverProto.pollOutput())
            serverProto.offerInput(clientProto.pollOutput())

            if (i == 25) {
                val out = "Hello, world!".encodeToByteArray()
                clientProto.writeApplicationData(out, 0, out.size)
            }

            if (i >= 25) {
                val avail = serverProto.applicationDataAvailable()
                if (avail == 0) continue
                val arr = ByteArray(avail)
                serverProto.readApplicationData(arr, 0, avail)
                passed = arr.decodeToString() == "Hello, world!"
            }
        }

        Assertions.assertTrue(passed)
    }
}