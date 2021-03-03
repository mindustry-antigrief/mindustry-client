package com.github.blahblahbloopster

import arc.*
import com.github.blahblahbloopster.communication.*
import com.github.blahblahbloopster.crypto.*
import com.github.blahblahbloopster.navigation.AStarNavigator
import mindustry.Vars
import mindustry.client.Client
import mindustry.client.navigation.Navigation

object Main : ApplicationListener {
    lateinit var communicationSystem: CommunicationSystem
    lateinit var messageCrypto: MessageCrypto
    lateinit var communicationClient: Packets.CommunicationClient

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

            Client.fooCommands.register("c", "<message...>", "Send a message TESTING") { args ->
                val message = args[0]

                communicationClient.send(DummyTransmission(message.toByteArray()))
            }
        } else {
            communicationSystem = DummyCommunicationSystem(mutableListOf())
        }
        messageCrypto = MessageCrypto()
        communicationClient = Packets.CommunicationClient(communicationSystem)
        messageCrypto.init(communicationClient)
        KeyFolder.initializeAlways()

        Navigation.navigator = AStarNavigator

        communicationClient.addListener { transmission, _ ->
            if (transmission is DummyTransmission) {
                println("GOT TRANSMISSION: ${transmission.content.decodeToString()}")
            }
        }
    }

    /** Run once per frame. */
    override fun update() {
        communicationClient.update()
    }

    /** Run when the object is disposed. */
    override fun dispose() {}
}
