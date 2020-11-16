package mindustry.client;

import arc.*;
import arc.graphics.Color;
import arc.struct.Queue;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.client.antigreif.*;
import mindustry.client.navigation.*;
import mindustry.client.ui.UnitPicker;
import mindustry.game.EventType;
import mindustry.game.EventType.*;
import mindustry.world.blocks.defense.turrets.BaseTurret;
import mindustry.gen.Call;
import mindustry.type.UnitType;

import static mindustry.Vars.*;

public class Client {
    private static TileLog[][] tileLogs;
    //todo: use this instead of Navigation.isFollowing and such
    public static ClientMode mode = ClientMode.normal;
    public static Queue<ConfigRequest> configs = new Queue<>();
    public static boolean showingTurrets = false;
    public static Seq<BaseTurret.BaseTurretBuild> turrets = new Seq<>();
    public static long lastSyncTime = 0L;

    public static void initialize() {
        Events.on(WorldLoadEvent.class, event -> {
            if (Time.timeSinceMillis(lastSyncTime) > 5000) {
                tileLogs = new TileLog[world.height()][world.width()];
            }
            PowerInfo.initialize();
            Navigation.stopFollowing();
            configs.clear();
            turrets.clear();
        });
        Events.on(EventType.UnitChangeEvent.class, event -> { // TODO: Instead of this code, call a class in UnitPicker.java to find and switch to new unit if possible.
            UnitType unit = UnitPicker.found;
            if (!event.unit.dead && event.unit.type == unit && event.unit.team == player.team() && !event.unit.isPlayer()) {
                Call.unitControl(player, event.unit);
                if (event.unit.isPlayer()) {
                if (player.unit() == event.unit) { UnitPicker.found = null; ui.chatfrag.addMessage("Success", "Unit Picker", Color.yellow);} // After we switch units successfully, stop listening for this unit
                else { ui.chatfrag.addMessage("Failed to become " + unit + ", " + event.unit.getPlayer() + " is already controlling it (likely using unit sniper).", "Unit Picker", Color.yellow);}
            }}
        });
        Events.on(EventType.UnitCreateEvent.class, event -> { // TODO: Instead of this code, call a class in UnitPicker.java to find and switch to new unit if possible.
            UnitType unit = UnitPicker.found;
            if (!event.unit.dead && event.unit.type == unit && event.unit.team == player.team() && !event.unit.isPlayer()) {
                Call.unitControl(player, event.unit);
                if (event.unit.isPlayer()) {
                if (player.unit() == event.unit) { UnitPicker.found = null; ui.chatfrag.addMessage("Success", "Unit Picker", Color.yellow);}  // After we switch units successfully, stop listening for this unit
                else { ui.chatfrag.addMessage("Failed to become " + unit + ", " + event.unit.getPlayer() + " is already controlling it (likely using unit sniper).", "Unit Picker", Color.yellow);}
            }}
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
