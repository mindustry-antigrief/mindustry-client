package mindustry.client;

import arc.Core;
import mindustry.gen.Player;

import static arc.Core.camera;

public class Spectate {
    public static Player user;

    public static void update() {
        if (user != null) {
            camera.position.lerpDelta(user, Core.settings.getBool("smoothcamera") ? 0.08f:1f);
        }
    }
}