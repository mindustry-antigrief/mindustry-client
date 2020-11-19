package mindustry.client;

import mindustry.client.utils.FloatEmbed;
import mindustry.gen.Player;

public class FooUser {

    public static boolean IsUser(Player user) { // Check if user is using foo's client
        if (user == null) return false;
        return FloatEmbed.isEmbedded(user.mouseX) && FloatEmbed.isEmbedded(user.mouseY);
    }
}
