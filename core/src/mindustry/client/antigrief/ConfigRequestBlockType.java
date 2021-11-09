package mindustry.client.antigrief;

import mindustry.gen.*;
import mindustry.gen.Call;
import mindustry.world.Tile;

import static mindustry.Vars.player;
import static mindustry.Vars.world;

public class ConfigRequestBlockType extends ConfigRequest{
    public Building targetBuild;

    public ConfigRequestBlockType(int x, int y, Object value, boolean isRotate, Building building) {
        super(x, y, value, isRotate);
        targetBuild = building;
    }

    public ConfigRequestBlockType(int x, int y, Object value, Building building) {
        this(x, y, value, false, building);
    }

    @Override
    public void run() {
        if (world != null) {
            Tile tile = world.tile(x, y);

            if (tile == null || world.build(x, y) != targetBuild) return;

            if (isRotate) Call.rotateBlock(player, tile.build, (boolean) value);
            else Call.tileConfig(player, tile.build, value);
        }
    }
}
