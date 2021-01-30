package com.github.blahblahbloopster

import arc.ApplicationListener
import arc.Core
import com.github.blahblahbloopster.crypto.CommunicationSystem
import com.github.blahblahbloopster.crypto.Crypto
import com.github.blahblahbloopster.crypto.MessageBlockCommunicationSystem
import com.github.blahblahbloopster.crypto.MessageCrypto
import com.github.blahblahbloopster.navigation.AStarNavigator
import mindustry.client.Client
import mindustry.client.navigation.Navigation

object Main : ApplicationListener {
    var communicationSystem: CommunicationSystem? = null
    var messageCrypto: MessageCrypto? = null

    /** Run on client load. */
    override fun init() {
        Crypto.init()
        if (Core.app.isDesktop) {
            communicationSystem = MessageBlockCommunicationSystem()
            communicationSystem!!.init()
            messageCrypto = MessageCrypto()
            messageCrypto!!.init(communicationSystem!!)
        }
        Client.mapping = ClientMapping()

        Navigation.navigator = AStarNavigator
    }

    /** Run once per frame. */
    override fun update() {}

    /** Run when the object is disposed. */
    override fun dispose() {}
}
