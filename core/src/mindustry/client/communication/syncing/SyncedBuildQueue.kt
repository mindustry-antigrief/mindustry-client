package mindustry.client.communication.syncing

import arc.util.io.Reads
import arc.util.io.Writes
import mindustry.client.communication.Packets
import mindustry.entities.units.BuildPlan
import mindustry.io.TypeIO
import java.io.DataInputStream
import java.io.DataOutputStream

class SyncedBuildQueue(comms: Packets.CommunicationClient, id: Long) : SyncedQueue<BuildPlan>(Syncer(serializer, deserializer, comms, id)) {
    companion object {
        private val serializer = { plan: BuildPlan, out: DataOutputStream ->
            TypeIO.writeRequest(Writes(out), plan)
        }

        private val deserializer = { inp: DataInputStream ->
            TypeIO.readRequest(Reads(inp))
        }
    }
}
