package mindustry.client.communication

import arc.util.io.*
import mindustry.entities.units.*
import mindustry.io.*
import java.io.*
import kotlin.random.*

class BuildQueueTransmission : Transmission {
    override var id = Random.nextLong()
    val plans: Array<BuildPlan>
    override val secureOnly: Boolean = false

    constructor(plans: Array<BuildPlan>) {
        this.plans = plans
    }

    constructor(input: ByteArray, id: Long, @Suppress("UNUSED_PARAMETER") senderID: Int) {
        this.id = id
        val reads = Reads.get(DataInputStream(input.inputStream()))
        plans = TypeIO.readPlans(reads) ?: throw IllegalArgumentException("Invalid request array!")
        reads.close()
    }

    override fun serialize(): ByteArray {
        val stream = ByteArrayOutputStream()
        val writes = Writes(DataOutputStream(stream))
        TypeIO.writePlans(writes, plans)
        val array = stream.toByteArray()
        writes.close()
        return array
    }
}
