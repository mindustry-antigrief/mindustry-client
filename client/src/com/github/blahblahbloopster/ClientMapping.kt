package com.github.blahblahbloopster

import arc.*
import arc.math.geom.*
import arc.util.serialization.*
import com.github.blahblahbloopster.crypto.*
import com.github.blahblahbloopster.navigation.*
import com.github.blahblahbloopster.ui.*
import mindustry.*
import mindustry.client.*
import mindustry.client.navigation.*
import mindustry.client.utils.*
import mindustry.gen.*

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
}
