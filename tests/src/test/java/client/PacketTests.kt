package client

import com.github.blahblahbloopster.communication.DummyTransmission
import com.github.blahblahbloopster.communication.Packets
import com.github.blahblahbloopster.crypto.DummyCommunicationSystem
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.random.Random

class PacketTests {

    @Test
    fun testSending() {
        val client1 = Packets.CommunicationClient(DummyCommunicationSystem())
        val client2 = Packets.CommunicationClient(DummyCommunicationSystem())

        var output: ByteArray? = null

        val transmission = DummyTransmission(Random.nextBytes(1024))
        client1.send(transmission)
        client2.listeners.add { t ->
            if (t is DummyTransmission) {
                output = t.content
            }
        }

        for (i in 0..50) {
            client1.update()
            Thread.sleep(10)
        }

        Assertions.assertTrue(output?.contentEquals(transmission.content) == true)
    }
}
