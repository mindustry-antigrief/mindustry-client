package mindustry.graphics;

import arc.math.Mathf;
import kotlin.Metadata;
import mindustry.Vars;

public final class Transparency {

    public static float convertTransparency(float input) {
        return Mathf.map(input, 0f, Vars.xray ? 0.2f : 1f);
    }
}
