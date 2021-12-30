package client

import mindustry.Vars
import mindustry.client.communication.ClientAssistManager
import mindustry.client.communication.DummyCommunicationSystem
import mindustry.client.communication.Packets
import mindustry.content.Blocks
import mindustry.content.Bullets
import mindustry.content.Items
import mindustry.content.Liquids
import mindustry.core.ContentLoader
import mindustry.core.World
import mindustry.entities.bullet.BulletType
import mindustry.entities.units.BuildPlan
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class ClientAssistTest {
    @Test
    fun testSyncing() {
        if (Blocks.air == null) {
            Vars.content = ContentLoader()
            Items().load()
            Liquids().load()
            BulletType().load()
            Bullets().load()
            Blocks().load()
            Vars.world = World()
            Vars.world.resize(10, 10)
            Vars.world.tiles.fill()
        }

        val p = mutableListOf<DummyCommunicationSystem>()

        val a = DummyCommunicationSystem(p)
        val b = DummyCommunicationSystem(p)

        val aClient = Packets.CommunicationClient(a)
        val bClient = Packets.CommunicationClient(b)

        val aManager = ClientAssistManager(b.id, aClient, true)
        val bManager = ClientAssistManager(a.id, bClient, false)

        val aQueue = mutableListOf<BuildPlan>()
        val bQueue = mutableListOf<BuildPlan>()

        aClient.addListener { transmission, senderId ->
//            println("b -> a: $transmission")
            aManager.received(transmission, aQueue)
        }

        bClient.addListener { transmission, senderId ->
//            println("a -> b: $transmission")
            bManager.received(transmission, bQueue)
        }

        var added = mutableListOf(BuildPlan(1, 2, 2, Blocks.message, "aaa"))
        aQueue.addAll(added)
        aManager.plansAddedRemoved(added, true, aQueue)

        repeat(10) {
            aClient.update()
            bClient.update()
        }

        added = mutableListOf(BuildPlan(3, 4, 0, Blocks.switchBlock), BuildPlan(1, 1, 2, Blocks.battery))
        aQueue.addAll(added)
        aManager.plansAddedRemoved(added, true, aQueue)

        repeat(10) {
            aClient.update()
            bClient.update()
        }

        added = mutableListOf(BuildPlan(3, 4, 0, Blocks.switchBlock), BuildPlan(1, 1, 2, Blocks.battery))
        aQueue.removeAll(added)
        aManager.plansAddedRemoved(added, false, aQueue)

        repeat(10) {
            aClient.update()
            bClient.update()
        }

//        println("expected: $aQueue")
//        println("actual:   $bQueue")

        Assertions.assertEquals(aQueue.size, bQueue.size)

        val eq = aQueue.zip(bQueue).all { (a, b) ->
            a.breaking == b.breaking &&
                    a.x == b.x &&
                    a.y == b.y &&
                    a.block == b.block &&
                    a.rotation == b.rotation &&
                    a.config == b.config
        }

        Assertions.assertTrue(eq)
    }
}
