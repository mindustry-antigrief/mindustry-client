package com.github.blahblahbloopster

import arc.Core
import arc.math.geom.Vec2
import arc.util.serialization.Base64Coder
import com.github.blahblahbloopster.communication.PluginCommunicationSystem
import com.github.blahblahbloopster.crypto.Crypto
import com.github.blahblahbloopster.crypto.DummyCommunicationSystem
import com.github.blahblahbloopster.crypto.MessageBlockCommunicationSystem
import com.github.blahblahbloopster.navigation.AssistPath
import com.github.blahblahbloopster.ui.ChangelogDialog
import com.github.blahblahbloopster.ui.FeaturesDialog
import com.github.blahblahbloopster.ui.FindDialog
import com.github.blahblahbloopster.ui.KeyShareDialog
import mindustry.Vars
import mindustry.client.Client
import mindustry.client.ClientInterface
import mindustry.client.navigation.Navigation
import mindustry.client.utils.FloatEmbed
import mindustry.gen.Player

class ClientMapping : ClientInterface {

    override fun showFindDialog() {
        FindDialog.show()
    }

    override fun showChangelogDialog() {
        ChangelogDialog.show()
    }

    override fun showFeaturesDialog() {
        FeaturesDialog.show()
    }

    override fun setAssistPath(player: Player?) {
        Navigation.follow(AssistPath(player))
    }

    override fun floatEmbed(): Vec2 {
        return when {
            Navigation.currentlyFollowing is AssistPath && Core.settings.getBool("displayasuser") ->
                Vec2(
                    FloatEmbed.embedInFloat(Vars.player.unit().aimX, Client.FOO_USER),
                    FloatEmbed.embedInFloat(Vars.player.unit().aimY, Client.ASSISTING)
                )
            Core.settings.getBool("displayasuser") ->
                Vec2(
                    FloatEmbed.embedInFloat(Vars.player.unit().aimX, Client.FOO_USER),
                    FloatEmbed.embedInFloat(Vars.player.unit().aimY, Client.FOO_USER)
                )
            else -> Vec2(Vars.player.unit().aimX, Vars.player.unit().aimY)
        }
    }

    override fun generateKey() {
        val quad = Crypto.generateKeyQuad()
        Core.settings.dataDirectory.child("key.txt").writeString((Base64Coder.encode(quad.serialize()).concatToString()), false)
        Main.messageCrypto.keyQuad = quad
    }

    override fun shareKey() {
        KeyShareDialog(Main.messageCrypto).show()
    }

    override fun setPluginNetworking(enable: Boolean) {
        when {
            enable -> {
                Main.initializeCommunication(PluginCommunicationSystem)
            }
            Core.app?.isDesktop == true -> {
                Main.initializeCommunication(MessageBlockCommunicationSystem())
            }
            else -> {
                Main.initializeCommunication(DummyCommunicationSystem(mutableListOf()))
            }
        }
    }
}
