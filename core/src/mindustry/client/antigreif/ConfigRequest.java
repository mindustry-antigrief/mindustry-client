package mindustry.client.antigreif;

import mindustry.world.Tile;

import static mindustry.Vars.world;

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
            if (tile == null) return;
            tile.build.configure(value);
        }
    }
}
