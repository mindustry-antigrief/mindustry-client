package mindustry.graphics;

import arc.math.Mathf;
import mindustry.client.*;

public final class Transparency {

    public static float convertTransparency(float input) {
        return Mathf.map(input, 0f, Client.xray ? 0.2f : 1f);
    }
}
