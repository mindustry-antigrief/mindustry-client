package client

import arc.util.Reflect
import mindustry.client.communication.DummyCommunicationSystem
import mindustry.client.communication.Packets
import mindustry.client.communication.syncing.Syncer
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.DataInputStream

class SyncerTests {

    @Test
    fun test() {
        val pool = mutableListOf<DummyCommunicationSystem>()

        val aComms = DummyCommunicationSystem(pool)
        val bComms = DummyCommunicationSystem(pool)

        val a = Packets.CommunicationClient(aComms)
        val b = Packets.CommunicationClient(bComms)

        val aSync = Syncer({ i, d -> d.writeInt(i) }, DataInputStream::readInt, a, 12L)
        val bSync = Syncer({ i, d -> d.writeInt(i) }, DataInputStream::readInt, b, 12L)

        aSync.added(listOf(Pair(123, 0)))

        repeat(20) {
            aSync.update()
            bSync.update()

            a.update()
            b.update()
        }

        aSync.added(listOf(Pair(456, 0)))

        repeat(20) {
            aSync.update()
            bSync.update()

            a.update()
            b.update()
        }

        aSync.added(listOf(Pair(789, 1)))
        Reflect.get<MutableList<Int>>(bSync, "internalList").removeAt(0)

        repeat(20) {
            aSync.update()
            bSync.update()

            a.update()
            b.update()
        }

        Assertions.assertEquals(aSync.list, bSync.list)

        println(aSync.list)
    }
}
