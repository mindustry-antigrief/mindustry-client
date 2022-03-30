package client

import arc.util.*
import mindustry.client.communication.*
import mindustry.client.communication.syncing.*
import org.junit.jupiter.api.*
import java.io.*

class SyncerTests {

    @Test
    fun test() {
        val pool = mutableListOf<DummyCommunicationSystem>()

        val aComms = DummyCommunicationSystem(pool)
        val bComms = DummyCommunicationSystem(pool)

        val a = Packets.CommunicationClient(aComms)
        val b = Packets.CommunicationClient(bComms)

        val aSync = Syncer({ i, d -> d.writeInt(i) }, DataInputStream::readInt, a, 12L, Syncer.Mode.BOTH)
        val bSync = Syncer({ i, d -> d.writeInt(i) }, DataInputStream::readInt, b, 12L, Syncer.Mode.BOTH)

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
