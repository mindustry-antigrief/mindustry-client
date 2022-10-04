package mindustry.client.communication.syncing

import arc.util.io.*
import mindustry.client.communication.*
import mindustry.entities.units.*
import mindustry.io.*
import java.io.*

class SyncedBuildQueue(comms: Packets.CommunicationClient, id: Long, mode: Syncer.Mode) : SyncedQueue<BuildPlan>(Syncer(serializer, deserializer, comms, id, mode)) {
    companion object {
        private val serializer = { plan: BuildPlan, out: DataOutputStream ->
            TypeIO.writePlan(Writes.get(out), plan)
        }

        private val deserializer = { inp: DataInputStream ->
            TypeIO.readPlan(Reads.get(inp))
        }
    }
}
