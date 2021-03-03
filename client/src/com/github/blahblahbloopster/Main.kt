package com.github.blahblahbloopster

import arc.ApplicationListener
import arc.Core
import com.github.blahblahbloopster.crypto.*
import com.github.blahblahbloopster.navigation.AStarNavigator
import mindustry.Vars
import mindustry.client.Client
import mindustry.client.navigation.Navigation

object Main : ApplicationListener {
    lateinit var communicationSystem: CommunicationSystem
    lateinit var messageCrypto: MessageCrypto

    /** Run on client load. */
    override fun init() {
        Client.mapping = ClientMapping()
        Crypto.initializeAlways()
        if (Core.app.isDesktop) {
            communicationSystem = MessageBlockCommunicationSystem()
            communicationSystem.init()

            Client.fooCommands.register("e", "<destination> <message...>", "Send an encrypted chat message") { args ->
                val dest = args[0]
                val message = args[1]

                for (key in messageCrypto.keys) {
                    if (key.name.equals(dest, true)) {
                        messageCrypto.encrypt(message, key)
                        return@register
                    }
                }
                Vars.ui.chatfrag.addMessage("[scarlet]Invalid key! They are listed in the \"manage keys\" section of the pause menu", null)
            }
        } else {
            communicationSystem = DummyCommunicationSystem(mutableListOf())
        }
        messageCrypto = MessageCrypto()
        messageCrypto.init(communicationSystem)
        KeyFolder.initializeAlways()

        Navigation.navigator = AStarNavigator
    }

    /** Run once per frame. */
    override fun update() {}

    /** Run when the object is disposed. */
    override fun dispose() {}
}
