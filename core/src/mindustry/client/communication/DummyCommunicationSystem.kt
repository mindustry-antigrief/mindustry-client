package mindustry.client.communication

import java.util.*
import java.util.function.Consumer

/** A dummy [CommunicationSystem] for tests. */
class DummyCommunicationSystem(private val pool: MutableList<DummyCommunicationSystem>) : CommunicationSystem() {
    override val listeners: MutableList<(ByteArray, Int) -> Unit> = mutableListOf()
    override val id = Random().nextInt()
    override val MAX_LENGTH: Int = 64
    override val RATE: Float = 0f // Unlimited

    private fun received(bytes: ByteArray, sender: Int) {
        listeners.forEach(Consumer { it.invoke(bytes, sender) })
    }

    override fun send(bytes: ByteArray) {
        pool.forEach(Consumer { item: DummyCommunicationSystem ->
            if (item !== this) {
                item.received(bytes, id)
            }
        })
    }

    override fun init() {}

    init {
        pool.add(this)
    }
}