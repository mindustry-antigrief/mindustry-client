package mindustry.client.antigrief;

import mindustry.gen.*;
import mindustry.world.*;

import static mindustry.Vars.*;

public class ConfigRequest {
    public final int x, y;
    public Object value;
    public boolean isRotate;

    public ConfigRequest(int x, int y, Object value, boolean isRotate) {
        this.x = x;
        this.y = y;
        this.value = value;
        this.isRotate = isRotate;
    }

    public ConfigRequest(int x, int y, Object value) {
        this(x, y, value, false);
    }

    public void run() {
        if (world != null) {
            Tile tile = world.tile(x, y);

            if (tile == null) return;

            if (isRotate) Call.rotateBlock(player, tile.build, (boolean) value);
            else Call.tileConfig(player, tile.build, value);
        }
    }
}
