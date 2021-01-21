package mindustry.client;

import arc.Core;
import arc.math.geom.Position;
import mindustry.gen.Player;
import mindustry.input.DesktopInput;

import static arc.Core.camera;

public class Spectate {
    public static Position pos;

    public static void update() {
        if (pos != null) {
            camera.position.lerpDelta(pos, Core.settings.getBool("smoothcamera") ? 0.08f:1f);
        }
    }

    public static void spectate(Position pos) {
        DesktopInput.panning = true;
        Spectate.pos = pos;
    }
}