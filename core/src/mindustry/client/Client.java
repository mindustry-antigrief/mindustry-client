package mindustry.client;

import arc.*;
import mindustry.*;
import mindustry.client.antigreif.*;
import mindustry.game.EventType.*;

public class Client {
    public static TileLog[][] tileLogs;

    public static void initialize() {
        Events.on(WorldLoadEvent.class, (event) -> {
            tileLogs = new TileLog[Vars.world.height()][Vars.world.width()];
        });
    }

    public static void update() {
    }
}
