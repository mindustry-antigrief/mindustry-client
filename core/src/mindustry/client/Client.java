package mindustry.client;

import arc.*;
import arc.graphics.Color;
import arc.struct.Queue;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.Timer;
import mindustry.client.antigreif.*;
import mindustry.client.navigation.*;
import mindustry.client.ui.Toast;
import mindustry.client.ui.UnitPicker;
import mindustry.game.EventType;
import mindustry.game.EventType.*;
import mindustry.gen.Player;
import mindustry.world.blocks.defense.turrets.BaseTurret;
import mindustry.gen.Call;
import mindustry.type.UnitType;

import static arc.Core.camera;
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
        Events.on(EventType.UnitChangeEvent.class, event -> { // TODO: Instead of this code, call a thing in UnitPicker.java to find and switch to new unit if possible.
            UnitType unit = UnitPicker.found;
            if (!event.unit.dead && event.unit.type == unit && event.unit.team == player.team() && !event.unit.isPlayer()) {
                Call.unitControl(player, event.unit);
                Timer.schedule(() -> {
                    if (event.unit.isPlayer()) {
                    if (player.unit() == event.unit) { UnitPicker.found = null; new Toast(3f).label(() -> "Successfully swapped to " + player.unit().type() + ".");}  // After we switch units successfully, stop listening for this unit
                    else { new Toast(3f).label(() -> "Failed to swap units, " + event.unit.getPlayer().name + " is already controlling this unit (likely using unit sniper).");}}
                }, .5f);
            }
        });
        Events.on(EventType.UnitCreateEvent.class, event -> { // TODO: Instead of this code, call a thing in UnitPicker.java to find and switch to new unit if possible.
            UnitType unit = UnitPicker.found;
            if (!event.unit.dead && event.unit.type == unit && event.unit.team == player.team() && !event.unit.isPlayer()) {
                Call.unitControl(player, event.unit);
                Timer.schedule(() -> {
                    if (event.unit.isPlayer()) {
                    if (player.unit() == event.unit) { UnitPicker.found = null; new Toast(3f).label(() -> "Successfully swapped to " + player.unit().type() + ".");}  // After we switch units successfully, stop listening for this unit
                    else { new Toast(3f).label(() -> "Failed to swap units, " + event.unit.getPlayer().name + " is already controlling this unit (likely using unit sniper).");}}
                }, .5f);
            }
        });
    }

    public static void update() {
        Navigation.update();
        PowerInfo.update();
        Spectate.update();

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
