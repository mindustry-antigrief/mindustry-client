package mindustry.client;

import arc.*;
import arc.struct.Queue;
import arc.struct.Seq;
import mindustry.client.antigreif.*;
import mindustry.client.navigation.*;
import mindustry.game.EventType.*;
import mindustry.world.blocks.defense.turrets.BaseTurret;

import static mindustry.Vars.world;

public class Client {
    private static TileLog[][] tileLogs;
    //todo: use this instead of Navigation.isFollowing and such
    public static ClientMode mode = ClientMode.normal;
    public static Queue<ConfigRequest> configs = new Queue<>();
    public static boolean showingTurrets = false;
    public static Seq<BaseTurret.BaseTurretBuild> turrets = new Seq<>();

    public static void initialize() {
        Events.on(WorldLoadEvent.class, event -> {
            tileLogs = new TileLog[world.height()][world.width()];
            PowerInfo.initialize();
            Navigation.stopFollowing();
            configs.clear();
            turrets.clear();
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
