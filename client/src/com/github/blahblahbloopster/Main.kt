package com.github.blahblahbloopster

import arc.ApplicationListener
import arc.Core
import com.github.blahblahbloopster.crypto.*
import com.github.blahblahbloopster.navigation.AStarNavigator
import mindustry.client.Client
import mindustry.client.navigation.Navigation

object Main : ApplicationListener {
    lateinit var communicationSystem: CommunicationSystem
    lateinit var messageCrypto: MessageCrypto

    /** Run on client load. */
    override fun init() {
        Crypto.init()
        KeyFolder.initializeAlways()
        if (Core.app.isDesktop) {
            communicationSystem = MessageBlockCommunicationSystem()
            communicationSystem.init()
        } else {
            communicationSystem = DummyCommunicationSystem()
        }
        messageCrypto = MessageCrypto()
        messageCrypto.init(communicationSystem)
        Client.mapping = ClientMapping()

        Navigation.navigator = AStarNavigator
    }

    /** Run once per frame. */
    override fun update() {}

    /** Run when the object is disposed. */
    override fun dispose() {}
}
