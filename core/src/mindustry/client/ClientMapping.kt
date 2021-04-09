package mindustry.client

import arc.Core
import arc.math.geom.Vec2
import arc.util.serialization.Base64Coder
import mindustry.client.communication.PluginCommunicationSystem
import mindustry.client.crypto.Crypto
import mindustry.client.crypto.DummyCommunicationSystem
import mindustry.client.crypto.MessageBlockCommunicationSystem
import mindustry.client.navigation.AssistPath
import mindustry.client.ui.ChangelogDialog
import mindustry.client.ui.FeaturesDialog
import mindustry.client.ui.FindDialog
import mindustry.client.ui.KeyShareDialog
import mindustry.Vars
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
                    FloatEmbed.embedInFloat(Vars.player.unit().aimX, ClientVars.FOO_USER),
                    FloatEmbed.embedInFloat(Vars.player.unit().aimY, ClientVars.ASSISTING)
                )
            Core.settings.getBool("displayasuser") ->
                Vec2(
                    FloatEmbed.embedInFloat(Vars.player.unit().aimX, ClientVars.FOO_USER),
                    FloatEmbed.embedInFloat(Vars.player.unit().aimY, ClientVars.FOO_USER)
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
                Main.communicationSystem.activeCommunicationSystem = PluginCommunicationSystem
            }
            Core.app?.isDesktop == true -> {
                Main.communicationSystem.activeCommunicationSystem = MessageBlockCommunicationSystem
            }
            else -> {
                Main.communicationSystem.activeCommunicationSystem = DummyCommunicationSystem(mutableListOf())
            }
        }
    }
}
