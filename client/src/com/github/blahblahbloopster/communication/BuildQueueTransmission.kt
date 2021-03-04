package com.github.blahblahbloopster.communication

import arc.util.io.*
import mindustry.entities.units.BuildPlan
import mindustry.io.TypeIO
import java.io.*
import kotlin.random.Random

class BuildQueueTransmission : Transmission {
    override var id = Random.nextLong()
    val plans: Array<BuildPlan>

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
