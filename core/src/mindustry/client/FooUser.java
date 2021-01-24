package mindustry.client;

import arc.math.geom.Vec2;
import mindustry.client.utils.FloatEmbed;
import mindustry.gen.Player;

public class FooUser {
    private static final byte FOO_USER = (byte) 0b10101010;
    private static final byte ASSISTING = (byte) 0b01010101;

    public static boolean isPlayerUser(Player user) { // Check if user is using foo's client
        if (user == null) return false;
        return isUser(new Vec2(user.mouseX, user.mouseY));
    }

    public static boolean isAssisting(Player user) { // Check if user is using foo's client
        if (user == null) return false;
        return isAssisting(new Vec2(user.mouseX, user.mouseY));
    }

    public static boolean isUser(Vec2 mousePos) { // Check if user is using foo's client
        if (mousePos == null) return false;
        return FloatEmbed.isEmbedded(mousePos.x, FOO_USER) && (FloatEmbed.isEmbedded(mousePos.y, FOO_USER) || FloatEmbed.isEmbedded(mousePos.y, ASSISTING));
    }

    public static boolean isAssisting(Vec2 mousePos) { // Check if user is using foo's client
        if (mousePos == null) return false;
        return FloatEmbed.isEmbedded(mousePos.x, FOO_USER) && FloatEmbed.isEmbedded(mousePos.y, ASSISTING);
    }

    public static Vec2 encode(float x, float y, boolean assisting) {
        return new Vec2(FloatEmbed.embedInFloat(x, FOO_USER), FloatEmbed.embedInFloat(y, assisting ? ASSISTING : FOO_USER));
    }
}
