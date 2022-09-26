package mindustry.client.ui;

import arc.*;
import arc.input.*;
import arc.scene.ui.*;
import arc.struct.*;
import arc.util.*;
import mindustry.ai.types.*;
import mindustry.client.*;
import mindustry.client.utils.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.ui.dialogs.*;

import static mindustry.Vars.*;

// FINISHME: Refactor the event stuff, its horrible
public class UnitPicker extends BaseDialog {
    public UnitType type;
    Seq<UnitType> sorted = content.units().copy();
//    static boolean noDisable;

    public UnitPicker(){
        super("@client.unitpicker");

        onResize(this::build);
        shown(this::build);
        setup();
        keyDown(KeyCode.enter, () -> pickUnit(sorted.first()));
    }

    void build(){
        cont.clear();
        buttons.clear();
        addCloseButton();

        Seq<Image> imgs = new Seq<>();
        Seq<Label> labels = new Seq<>();
        for(int i = 0; i < 10; i++){
            imgs.add(new Image());
            labels.add(new Label(""));
        }
        TextField searchField = cont.field("", string -> {
            sorted = sorted.sort((b) -> BiasedLevenshtein.biasedLevenshteinInsensitive(string, b.name));
            for (int i = 0; i < imgs.size; i++) {
                Image region = new Image(sorted.get(i).uiIcon);
                imgs.get(i).setDrawable(region.getDrawable());
                labels.get(i).setText(sorted.get(i).name);
            }
        }).get();
        for(int i = 0; i < 10; i++){
            cont.row().add(imgs.get(i)).size(64);
            cont.add(labels.get(i));
        }

        Core.app.post(searchField::requestKeyboard);
    }

    /** Called whenever a new unit is added. */
    public void handle(Unit unit){
        if (type != unit.type || unit.team != player.team()) return;

        Call.unitControl(player, unit);
        type = null;

        Timer.schedule(() -> Core.app.post(() -> {
            if (unit.isPlayer()) {
                Toast t = new Toast(3);
                if (unit.isLocal()) {
                    t.add("New method worked.");
//                    t.add("@client.unitpicker.success");
                } else if (unit.getPlayer() != null && !unit.isLocal()) {
                    type = unit.type;
                    t.add("Failed to pick unit with new method, already in use by: " + unit.getPlayer().name);
//                    t.add(Core.bundle.format("client.unitpicker.alreadyinuse", unit.type, unit.getPlayer().name));
                }
            } else { // This happens sometimes, idk man FINISHME: Cleanup
                Log.err("???");
                type = unit.type;
            }
        }), net.client() ? netClient.getPing() / 1000f + .3f : 0);

    }

    public boolean pickUnit(UnitType type){
        return pickUnit(type, player.x, player.y, false);
    }
    public boolean pickUnit(UnitType type, float x, float y, boolean fast) {
        hide();
        if (type == null) return false;
        var found = findUnit(type, x, y, fast);

        Toast t = new Toast(3);
        if (found != null) {
            Call.unitControl(player, found); // Switch to unit
            t.add("@client.unitpicker.success");
            this.type = null;
        } else {
            t.add(Core.bundle.format("client.unitpicker.notfound", type));
            this.type = type;
        }
        return found != null;
    }
    
    public void unpickUnit() {
        this.type = null;
    }

    public Unit findUnit(UnitType type) {
        return findUnit(type, player.x, player.y);
    }
    public Unit findUnit(UnitType type, float x, float y) {
        Unit found = Units.closest(player.team(), player.x, player.y, u -> !u.isPlayer() && u.type == type && !u.dead && !(u.controller() instanceof LogicAI)); // Non logic units
        if (found == null) found = Units.closest(player.team(), player.x, player.y, u -> !u.isPlayer() && u.type == type && !u.dead); // All units

        return found;
    }
    public Unit findUnit(UnitType type, float x, float y, boolean fast) {
        if(!fast) return findUnit(type, x, y);
        return Units.closest(player.team(), x, y, u -> !u.isPlayer() && u.type == type && !u.dead);
    }

    private void setup(){

//        Events.on(EventType.UnitChangeEventClient.class, e -> {
//            Log.info("Player @ swapped from @ to @ | Nodisable: @", e.player.name, e.oldUnit.type, e.newUnit.type, noDisable);
//            if (e.player == player && !noDisable) {
//                type = null;
//                noDisable = false;
//            }
//        });

        Events.on(EventType.UnitChangeEventClient.class, event -> {
            if (type == null || event.oldUnit.dead || event.oldUnit.type != type || event.oldUnit.team != player.team() || event.player.isLocal()) return;
            type = null;
            Timer.schedule(() -> Core.app.post(() -> {
                Call.unitControl(player, event.oldUnit);
                Timer.schedule(() -> Core.app.post(() -> { // Delay by a frame + ping so the unit is actually unloaded in time.
                    if (event.oldUnit.isPlayer()) {
                        Toast t = new Toast(3);
                        if (event.oldUnit.isLocal()) {
                            type = null;
                            t.add("@client.unitpicker.success");
                        } else if (event.oldUnit.getPlayer() != null && !event.oldUnit.isLocal()) {
                            t.add(Core.bundle.format("client.unitpicker.alreadyinuse", event.oldUnit.type, event.oldUnit.getPlayer().name));
                            type = event.oldUnit.type;
                        } else t.add("[scarlet]This wasn't supposed to happen...");
                    }
                }), net.client() ? netClient.getPing()/1000f + .3f: 0);
            }), net.client() ? netClient.getPing()/1000f + .3f: 0);
        });

//        Events.on(EventType.UnitUnloadEvent.class, event -> {
//            if (type == null || event.unit.type != type || event.unit.team != player.team()) return;
//            var temp = type;
//            type = null;
//            Timer.schedule(() -> Core.app.post(() -> { // Delay by a frame + ping so the unit is actually unloaded in time.
//                var found = findUnit(temp);
//                if (found == null) return;
//                type = null;
//                Call.unitControl(player, found);
//                Timer.schedule(() -> Core.app.post(() -> {
//                    if (found.isPlayer()) {
//                        Toast t = new Toast(3);
//                        if (found.isLocal()) {
//                            t.add("@client.unitpicker.success");
//                        } else if (found.getPlayer() != null && !found.isLocal()) {
//                            type = found.type;
//                            t.add(Core.bundle.format("client.unitpicker.alreadyinuse", found.type, found.getPlayer().name));
//                        }
//                    } else { // This happens sometimes, idk man FINISHME: Cleanup
//                        type = event.unit.type;
//                    }
//                }), net.client() ? netClient.getPing() / 1000f + .3f : 0);
//            }), net.client() ? netClient.getPing()/1000f + .3f: 0);
//        });

        Events.on(EventType.WorldLoadEvent.class, event -> {
            if (!ClientVars.syncing) {
                type = null;
                Time.run(60, () -> pickUnit(Core.settings.getBool("automega") && state.isGame() && (player.unit().type == null || player.unit().type != UnitTypes.mega) ? UnitTypes.mega : null));
            }
        });
    }
}
