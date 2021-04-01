package com.github.blahblahbloopster.communication

import com.github.blahblahbloopster.crypto.CommunicationSystem

class SwitchableCommunicationSystem(val systems: List<CommunicationSystem>) : CommunicationSystem() {

    constructor(vararg communicationSystems: CommunicationSystem) : this(communicationSystems.toMutableList())

    var activeCommunicationSystem: CommunicationSystem = systems[0]

    override val listeners: MutableList<(input: ByteArray, sender: Int) -> Unit> = mutableListOf()

    override val id get() = activeCommunicationSystem.id
    override val MAX_LENGTH get() = activeCommunicationSystem.MAX_LENGTH
    override val RATE get() = activeCommunicationSystem.RATE

    override fun send(bytes: ByteArray) {
        activeCommunicationSystem.send(bytes)
    }

    override fun init() {
        for (system in systems) {
            system.init()
        }
    }

    override fun clearListeners() {
        listeners.clear()
        syncListeners()
    }

    override fun addListener(listener: (input: ByteArray, sender: Int) -> Unit) {
        listeners.add(listener)
        syncListeners()
    }

    override fun addAllListeners(items: Collection<(input: ByteArray, sender: Int) -> Unit>) {
        listeners.addAll(items)
        syncListeners()
    }

    private fun syncListeners() {
        for (system in systems) {
            system.clearListeners()
            system.addAllListeners(listeners)
        }
    }
}
