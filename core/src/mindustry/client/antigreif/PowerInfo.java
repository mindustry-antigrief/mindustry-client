package mindustry.client.antigreif;

import arc.*;
import arc.math.*;
import arc.scene.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import mindustry.*;
import mindustry.client.ui.*;
import mindustry.core.*;
import mindustry.graphics.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.blocks.power.*;
import java.util.*;

public class PowerInfo {

    public static ObjectSet<PowerGraph> graphs = new ObjectSet<>();
    public static float powerBalance = 0f;
    public static float powerFraction = 0f;
    public static float batteryAmount = 0f;
    public static float batteryCapacity = 0f;
    private static int framesWithoutUpdate = 0;

    public static void initialize() {}

    public static void update() {
        graphs = graphs.select(Objects::nonNull);
        PowerGraph graph = graphs.asArray().max(g -> g.all.size);
        if (graph != null) {
            powerBalance = graph.displayPowerBalance.getAverage() * 60;
            powerFraction = Mathf.clamp(graph.getLastPowerProduced() / graph.getLastPowerNeeded());
            batteryAmount = graph.getBatteryStored();
            batteryCapacity = graph.getTotalBatteryCapacity();
            framesWithoutUpdate = 0;
            if (Core.graphics.getFrameId() % 120 == 0) {
                // Every 2 seconds or so rescan
                scan();
            }
        } else {
            powerBalance = 0f;
            batteryAmount = 0f;
            batteryCapacity = 0f;
            framesWithoutUpdate += 1;
            if (framesWithoutUpdate > 30) {
                // Scan twice a second
                framesWithoutUpdate = 2;
                scan();
            }
            if (framesWithoutUpdate == 1) {
                // Scan once immediately
                scan();
            }
        }
    }

    /** Scans the entire world for power nodes and gets the power graphs */
    private static void scan() {
        graphs.clear();
        if (Vars.world == null) {
            return;
        } else if (Vars.world.tiles == null) {
            return;
        }
        for (Tile tile : Vars.world.tiles) {
            if (tile != null) {
                if (tile.block() instanceof PowerNode) {
                    if (tile.build != null){
                        if (tile.build.power != null) {
                            if (tile.build.power.graph != null) {
                                if (tile.getTeamID() == Vars.player.team().id){
                                    graphs.add(tile.build.power.graph);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public static Element getBars() {
        Table table = new Table();
        Bar powerBar = new MonospacedBar(() -> Float.toString(powerBalance), () -> Pal.powerBar, () -> powerFraction);
        table.add(powerBar).width(200f).height(30f);
        table.row();

        Bar batteryBar = new MonospacedBar(() -> UI.formatAmount((int)batteryAmount) + " / " + UI.formatAmount((int)batteryCapacity), () -> Pal.powerBar, () -> Mathf.clamp(batteryAmount / batteryCapacity));
        table.add(batteryBar).width(200f).height(30f);

        return table;
    }
}
