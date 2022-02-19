package mindustry.ui.fragments;

import arc.*;
import arc.math.*;
import arc.scene.*;
import arc.scene.actions.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import mindustry.client.*;
import mindustry.client.antigrief.*;
import mindustry.content.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.ui.*;
import mindustry.world.blocks.logic.*;

import static mindustry.Vars.*;

public class BlockConfigFragment extends Fragment{
    Table table = new Table();
    Building configTile;

    @Override
    public void build(Group parent){
        table.visible = false;
        parent.addChild(table);

        //hacky way to hide block config when in menu
        //TODO remove?
        Core.scene.add(new Element(){
            @Override
            public void act(float delta){
                super.act(delta);
                if(state.isMenu()){
                    table.visible = false;
                    configTile = null;
                }
            }
        });

        Events.on(ResetEvent.class, e -> {
            table.visible = false;
            configTile = null;
        });
    }

    public boolean isShown(){
        return table.visible && configTile != null;
    }

    public Building getSelectedTile(){
        return configTile;
    }

    public void showConfig(Building tile){
        if(tile.configTapped()){
            configTile = tile;

            table.visible = true;
            table.clear();
            tile.buildConfiguration(table);
            if (tile instanceof LogicBlock.LogicBuild lb) { // Cant put this directly in the logicBuild class as LDB overrides and breaks it then...
                table.button(Icon.refresh, Styles.clearTransi, () -> {
                    var original = lb.code;
                    ClientVars.configs.add(() -> { // Cursed, enqueues a config now, when that one is run it enqueues a second config.
                        new ConfigRequest(lb, LogicBlock.compress("end\n" + lb.code, lb.relativeConnections())).run();
                        Timer.schedule(() -> ClientVars.configs.add(new ConfigRequest(lb, LogicBlock.compress(original, lb.relativeConnections()))), net.client() ? netClient.getPing()/1000f : 0);
                    });
                }).size(40).tooltip("Restart code execution");
            }
            table.pack();
            table.setTransform(true);
            table.actions(Actions.scaleTo(0f, 1f), Actions.visible(true),
            Actions.scaleTo(1f, 1f, 0.07f, Interp.pow3Out));

            table.update(() -> {
                if(configTile != null && configTile.shouldHideConfigure(player)){
                    hideConfig();
                    return;
                }

                table.setOrigin(Align.center);
                if(configTile == null || configTile.block == Blocks.air || !configTile.isValid()){
                    hideConfig();
                }else{
                    configTile.updateTableAlign(table);
                }
            });
        }
    }

    public boolean hasConfigMouse(){
        Element e = Core.scene.hit(Core.input.mouseX(), Core.graphics.getHeight() - Core.input.mouseY(), true);
        return e != null && (e == table || e.isDescendantOf(table));
    }

    public void hideConfig(){
        configTile = null;
        table.actions(Actions.scaleTo(0f, 1f, 0.06f, Interp.pow3Out), Actions.visible(false));
    }
}
