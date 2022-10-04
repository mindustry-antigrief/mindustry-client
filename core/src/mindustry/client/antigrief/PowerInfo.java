package mindustry.client.antigrief;

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
import mindustry.world.blocks.power.*;

public class PowerInfo {
    private static PowerGraph found;
    public static PowerGraph hovered;
    public static final Seq<PowerGraph> graphs = new Seq<>();

    public static void update() {
        found = graphs.max(g -> g.team == Vars.player.team(), g -> g.all.size);
        var tile = Vars.control.input.cursorTile();
        hovered = Core.settings.getBool("graphdisplay") && tile != null && tile.build instanceof PowerNode.PowerNodeBuild node ? node.power.graph : null;
    }

    public static Element getBars(Table power) { // FINISHME: What in the world
        Bar powerBar = new MonospacedBar(
            () -> Core.bundle.format("bar.powerbalance", found != null ? (found.powerBalance.rawMean() >= 0 ? "+" : "") + UI.formatAmount((int)(found.getPowerBalance() * 60)) : "+0"),
            () -> Pal.powerBar,
            () -> found != null ? found.getSatisfaction() : 0
        );
        Bar batteryBar = new MonospacedBar(
            () -> Core.bundle.format("bar.powerstored", found != null ? UI.formatAmount((long)found.getLastPowerStored()) : 0, found != null ? UI.formatAmount((long)found.getLastCapacity()) : 0),
            () -> Pal.powerBar,
            () -> found != null && found.getLastCapacity() != 0f ? Mathf.clamp(found.getLastPowerStored() / found.getLastCapacity()) : 0
        );

        power.add(powerBar).height(18).growX().padBottom(6);
        power.row();
        power.add(batteryBar).height(18).growX().padBottom(6);

        return power;
    }
}
