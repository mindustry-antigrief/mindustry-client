package mindustry.client.antigrief;

import arc.math.*;
import arc.scene.*;
import arc.scene.ui.layout.Table;
import arc.struct.*;
import arc.util.Strings;
import mindustry.Vars;
import mindustry.client.ui.*;
import mindustry.core.*;
import mindustry.graphics.*;
import mindustry.ui.*;
import mindustry.world.blocks.power.*;
import mindustry.gen.*;

public class PowerInfo {

    private static PowerGraph found = null;

    public static void initialize() {}

    public static void update() {
        if (PowerGraph.activeGraphs == null) return;
        PowerGraph.activeGraphs.forEach(item -> {
            if (item != null) {
                item.updateActive();
            }
        });
        ObjectSet<PowerGraph> graphs = PowerGraph.activeGraphs.select(item -> item != null && item.team == Vars.player.team());
        found = graphs.asArray().max(g -> g.all.size);
    }

    public static Element getBars() {
        Table power = new Table(Tex.wavepane);

        Bar powerBar = new MonospacedBar(() -> Strings.fixed(found != null ? found.powerBalance.rawMean() * 60f : 0f, 1), () -> Pal.powerBar, () -> found != null? found.getSatisfaction() : 0f);
        Bar batteryBar = new MonospacedBar(() -> (found != null? UI.formatAmount((int)found.getLastPowerStored()) : 0) + "/" + (found != null? UI.formatAmount((int)found.getLastCapacity()) : 0), () -> Pal.powerBar, () -> found != null? Mathf.clamp(found.getLastPowerStored() / Math.max(found.getLastCapacity(), 0.0001f)) : 0f);

        power.add(batteryBar).height(18).growX().padBottom(6);
        power.row();
        power.add(powerBar).height(18).growX();

        return power;
    }
}
