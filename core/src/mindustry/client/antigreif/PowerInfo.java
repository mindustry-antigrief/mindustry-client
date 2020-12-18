package mindustry.client.antigreif;

import arc.math.*;
import arc.scene.*;
import arc.scene.event.Touchable;
import arc.scene.ui.Button;
import arc.struct.*;
import arc.util.Strings;
import mindustry.Vars;
import mindustry.client.ui.*;
import mindustry.core.*;
import mindustry.graphics.*;
import mindustry.ui.*;
import mindustry.world.blocks.power.*;
import java.util.*;
import mindustry.gen.*;

public class PowerInfo {

    private static PowerGraph found = null;

    public static void initialize() {}

    public static void update() {
        ObjectSet<PowerGraph> graphs = PowerGraph.activeGraphs.select(item -> item != null && item.team == Vars.player.team());
        found = graphs.asArray().max(g -> g.all.size);
        PowerGraph.activeGraphs.forEach(PowerGraph::updateActive);
    }

    public static Element getBars() {
        Button power = new Button(Styles.waveb);
        Bar powerBar = new MonospacedBar(() -> Strings.fixed(found != null ? found.displayPowerBalance.getAverage() * 60f : 0f, 1), () -> Pal.powerBar, () -> found != null? found.getSatisfaction() : 0f);

        Bar batteryBar = new MonospacedBar(() -> (found != null? UI.formatAmount((int)found.getLastPowerStored()) : 0) + " / " + (found != null? UI.formatAmount((int)found.getLastCapacity()) : 0), () -> Pal.powerBar, () -> found != null? Mathf.clamp(found.getLastPowerStored() / Math.max(found.getLastCapacity(), 0.0001f)) : 0f);

        power.touchable = Touchable.disabled; // Don't touch my button :)
        power.table(Tex.windowEmpty, t -> t.add(batteryBar).margin(0).grow()).grow();
        power.row();
        power.table(Tex.windowEmpty, t -> t.add(powerBar).margin(0).grow()).grow();

        return power;
    }
}
