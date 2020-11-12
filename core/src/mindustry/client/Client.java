package mindustry.client;

import arc.*;
import arc.struct.Queue;
import mindustry.client.antigreif.*;
import mindustry.client.navigation.*;
import mindustry.client.ui.UnitPicker;
import mindustry.game.EventType;
import mindustry.game.EventType.*;
import mindustry.gen.Call;
import mindustry.type.UnitType;

import static mindustry.Vars.*;

public class Client {
    private static TileLog[][] tileLogs;
    //todo: use this instead of Navigation.isFollowing and such
    public static ClientMode mode = ClientMode.normal;
    public static Queue<ConfigRequest> configs = new Queue<>();

    public static void initialize() {
        Events.on(WorldLoadEvent.class, event -> {
            tileLogs = new TileLog[world.height()][world.width()];
            PowerInfo.initialize();
            Navigation.stopFollowing();
            configs.clear();
        });
        Events.on(EventType.UnitChangeEvent.class, event -> {
            UnitType unit = UnitPicker.found;
            if (!event.unit.dead && event.unit.type == unit && event.unit.team == player.team() && !event.unit.isPlayer()) {
                Call.unitControl(player, event.unit);
                UnitPicker.found = null; // After we switch units, don't attempt to switch again
            }
        });
        Events.on(EventType.UnitCreateEvent.class, event -> { // TODO: Make it check team and remove debug stuff, make sure its not player controlled
            UnitType unit = UnitPicker.found;
            if (!event.unit.dead && event.unit.type == unit && event.unit.team == player.team() && !event.unit.isPlayer()) {
                Call.unitControl(player, event.unit);
                UnitPicker.found = null; // After we switch units, don't attempt to switch again
            }
        });
    }

    public static void update() {
        Navigation.update();
        PowerInfo.update();
        if (!configs.isEmpty()) {
            try {
                configs.removeFirst().run();
            } catch (Exception ignored) {}
        }
    }

    public static TileLog getLog(int x, int y) {
        if (tileLogs[y][x] == null) {
            tileLogs[y][x] = new TileLog(world.tile(x, y));
        }
        return tileLogs[y][x];
    }
}
