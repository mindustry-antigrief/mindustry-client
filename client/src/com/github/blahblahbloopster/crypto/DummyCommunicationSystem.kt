package com.github.blahblahbloopster.crypto

import java.util.*
import java.util.function.*

/** A dummy [CommunicationSystem] for tests.  */
class DummyCommunicationSystem : CommunicationSystem {
    override val listeners: MutableList<(ByteArray, Int) -> Unit> = mutableListOf()
    override val id = Random().nextInt()

    private fun received(bytes: ByteArray, sender: Int) {
        listeners.forEach(Consumer { it.invoke(bytes, sender) })
    }

    override fun send(bytes: ByteArray) {
        systems.forEach(Consumer { item: DummyCommunicationSystem ->
            if (item !== this) {
                item.received(bytes, id)
            }
        })
    }

    override fun init() {}

    companion object {
        private val systems = ArrayList<DummyCommunicationSystem>()
    }

    init {
        systems.add(this)
    }
}