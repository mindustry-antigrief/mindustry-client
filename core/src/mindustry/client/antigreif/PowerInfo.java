package mindustry.client.antigreif;

import arc.scene.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import mindustry.ui.*;
import mindustry.world.blocks.power.*;

import java.util.*;

public class PowerInfo {

    public static Seq<PowerGraph> graphs = new Seq<>();
    public static float powerFraction = 0f;
    public static float batteryAmount = 0f;
    public static float batteryCapacity = 0f;

    public static void initialize() {}

    public static void update() {
        graphs = graphs.filter(Objects::nonNull);
        PowerGraph graph = graphs.max(g -> g.all.size);
        if (graph != null) {
            powerFraction = graph.getPowerBalance();
            batteryAmount = graph.getBatteryStored();
            batteryCapacity = graph.getBatteryCapacity();
        } else {
            powerFraction = 0f;
            batteryAmount = 0f;
            batteryCapacity = 0f;
        }
    }

    public static Element getBars() {
        Table table = new Table();
        Bar powerBar = new Bar();
    }
}
