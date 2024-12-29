package mindustry.ui.fragments;

import arc.*;
import arc.math.*;
import arc.scene.*;
import arc.scene.actions.*;
import arc.scene.ui.layout.*;
import arc.struct.ObjectSet;
import arc.util.*;
import mindustry.content.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.world.blocks.distribution.BufferedItemBridge.BufferedItemBridgeBuild;
import mindustry.world.blocks.distribution.ItemBridge.ItemBridgeBuild;
import mindustry.world.blocks.distribution.MassDriver.MassDriverBuild;
import mindustry.world.blocks.liquid.LiquidBridge.LiquidBridgeBuild;
import mindustry.world.blocks.payloads.PayloadMassDriver.PayloadDriverBuild;

import static mindustry.Vars.*;

public class BlockConfigFragment{
    static ObjectSet<Class<? extends Building>> validBuilds = ObjectSet.with(
            BufferedItemBridgeBuild.class, ItemBridgeBuild.class, LiquidBridgeBuild.class, MassDriverBuild.class, PayloadDriverBuild.class
    );
    Table table = new Table();
    Building selected;
    public boolean dragging = false;

    public void build(Group parent){
        table.visible = false;
        parent.addChild(table);

        Events.on(ResetEvent.class, e -> forceHide());
    }

    public void forceHide(){
        table.visible = false;
        selected = null;
        dragging = false;
    }

    public boolean isShown(){
        return table.visible && selected != null;
    }

    public Building getSelected(){
        return selected;
    }

    public void showConfig(Building tile){
        if(selected != null) selected.onConfigureClosed();
        if(tile.configTapped()){
            selected = tile;
            dragging = validBuilds.contains(selected.getClass());
            table.visible = true;
            table.clear();
            table.background(null); // clear the background as some blocks set custom ones
            tile.buildConfiguration(table);
            table.pack();
            table.setTransform(true);
            table.actions(Actions.scaleTo(0f, 1f), Actions.visible(true),
            Actions.scaleTo(1f, 1f, 0.07f, Interp.pow3Out));

            table.update(() -> {
                if(selected != null && selected.shouldHideConfigure(player)){
                    hideConfig();
                    return;
                }

                table.setOrigin(Align.center);
                if(selected == null || selected.block == Blocks.air || !selected.isValid()){
                    hideConfig();
                }else{
                    selected.updateTableAlign(table);
                }
            });
        }
    }

    public boolean hasConfigMouse(){
        Element e = Core.scene.getHoverElement();
        return e != null && (e == table || e.isDescendantOf(table));
    }

    public void hideConfig(){
        if(selected != null) selected.onConfigureClosed();
        selected = null;
        dragging = false;
        table.actions(Actions.scaleTo(0f, 1f, 0.06f, Interp.pow3Out), Actions.visible(false));
    }
}
