package com.github.blahblahbloopster

import arc.ApplicationListener
import arc.Core
import arc.util.Timer
import com.github.blahblahbloopster.crypto.CommunicationSystem
import com.github.blahblahbloopster.crypto.Crypto
import com.github.blahblahbloopster.crypto.MessageBlockCommunicationSystem
import com.github.blahblahbloopster.crypto.MessageCrypto

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
    }

    /** Run once per frame. */
    override fun update() {}

    /** Run when the object is disposed. */
    override fun dispose() {}
}
