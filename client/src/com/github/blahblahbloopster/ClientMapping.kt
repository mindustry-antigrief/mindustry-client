package com.github.blahblahbloopster

import arc.Core
import arc.math.geom.Vec2
import arc.util.serialization.Base64Coder
import com.github.blahblahbloopster.crypto.Crypto
import com.github.blahblahbloopster.ui.ChangelogDialog
import com.github.blahblahbloopster.ui.FeaturesDialog
import com.github.blahblahbloopster.ui.FindDialog
import mindustry.Vars
import mindustry.client.Client
import mindustry.client.ClientInterface
import com.github.blahblahbloopster.navigation.AssistPath
import com.github.blahblahbloopster.ui.KeyShareDialog
import mindustry.client.navigation.Navigation
import mindustry.client.utils.FloatEmbed
import mindustry.gen.Player
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters

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
        val generate = {
            val pair = Crypto.generateKeyQuad()
            Core.settings.dataDirectory.child("key.txt").writeString((Base64Coder.encode(pair.serialize()).concatToString()), false)
        }

        if (Main.messageCrypto?.keyQuad != null) {
            Vars.ui.showConfirm("Key Overwrite",
                "This will irreversibly overwrite your key.  Are you sure you want to do this?", generate)
        } else {
            generate()
        }
    }

    override fun shareKey() {
        KeyShareDialog().show()
    }

    override fun shouldAddZws(): Boolean {
        return Main.messageCrypto?.keyQuad != null
    }
}
