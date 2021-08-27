package client

import mindustry.client.communication.*
import mindustry.client.communication.DummyCommunicationSystem
import org.junit.jupiter.api.*
import kotlin.random.Random

class PacketTests {

    @Test
    fun testSending() {
        val pool = mutableListOf<DummyCommunicationSystem>()
        val client1 = Packets.CommunicationClient(DummyCommunicationSystem(pool))
        val client2 = Packets.CommunicationClient(DummyCommunicationSystem(pool))

        val transmission1 = DummyTransmission(Random.nextBytes(1024))
        val transmission2 = DummyTransmission(Random.nextBytes(1024))
        val transmission3 = DummyTransmission(Random.nextBytes(1024))

        var output1: ByteArray? = null
        var output2: ByteArray? = null
        var output3: ByteArray? = null

        client1.send(transmission1)
        client1.send(transmission2)
        client2.send(transmission3)

        val listener = { t: Transmission, _: Int ->
            if (t is DummyTransmission) {
                when (t.id) {
                    transmission1.id -> output1 = t.content
                    transmission2.id -> output2 = t.content
                    transmission3.id -> output3 = t.content
                }
            }
        }

        client1.addListener(listener)
        client2.addListener(listener)

        for (i in 0..150) {
            client1.update()
            client2.update()
            Thread.sleep(10)
        }

        Assertions.assertArrayEquals(transmission1.content, output1)
        Assertions.assertArrayEquals(transmission2.content, output2)
        Assertions.assertArrayEquals(transmission3.content, output3)
    }
}
