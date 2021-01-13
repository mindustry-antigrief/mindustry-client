package mindustry.client.antigrief;

import mindustry.gen.Call;
import mindustry.world.Tile;

import static mindustry.Vars.*;

public class ConfigRequest {
    public final int x, y;
    public final Object value;

    public ConfigRequest(int x, int y, Object value) {
        this.x = x;
        this.y = y;
        this.value = value;
    }

    public void run() {
        if (world != null) {
            Tile tile = world.tile(x, y);
            if (tile == null || tile.build.config() == value) return;
            Call.tileConfig(player, tile.build, value);
        }
    }
}
