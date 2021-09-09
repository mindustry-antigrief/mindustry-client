package mindustry.client.communication

import arc.util.io.Reads
import arc.util.io.Writes
import mindustry.entities.units.BuildPlan
import mindustry.io.TypeIO
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.random.Random

class BuildQueueTransmission : Transmission {
    override var id = Random.nextLong()
    val plans: Array<BuildPlan>
    override val secureOnly: Boolean = false

    constructor(plans: Array<BuildPlan>) {
        this.plans = plans
    }

    constructor(input: ByteArray, id: Long) {
        this.id = id
        val reads = Reads.get(DataInputStream(input.inputStream()))
        plans = TypeIO.readRequests(reads) ?: throw IllegalArgumentException("Invalid request array!")
        reads.close()
    }

    override fun serialize(): ByteArray {
        val stream = ByteArrayOutputStream()
        val writes = Writes(DataOutputStream(stream))
        TypeIO.writeRequests(writes, plans)
        val array = stream.toByteArray()
        writes.close()
        return array
    }
}
