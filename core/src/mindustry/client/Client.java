package mindustry.client;

import arc.*;
import mindustry.client.antigreif.*;
import mindustry.client.navigation.*;
import mindustry.game.EventType.*;

import static mindustry.Vars.world;

public class Client {
    private static TileLog[][] tileLogs;

    public static void initialize() {
        Events.on(WorldLoadEvent.class, event -> {
            tileLogs = new TileLog[world.height()][world.width()];
            PowerInfo.initialize();
        });
    }

    public static void update() {
        Navigation.update();
        PowerInfo.update();
    }

    public static TileLog getLog(int x, int y) {
        if (tileLogs[y][x] == null) {
            tileLogs[y][x] = new TileLog(world.tile(x, y));
        }
        return tileLogs[y][x];
    }
}
