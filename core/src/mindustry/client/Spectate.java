package mindustry.client;

import arc.*;
import arc.math.geom.*;
import mindustry.input.*;

import static arc.Core.*;

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