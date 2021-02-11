package mindustry.client.ui;

import arc.*;
import arc.input.*;
import arc.scene.ui.*;
import arc.struct.*;
import arc.util.Log;
import arc.util.Timer;
import mindustry.ai.types.FormationAI;
import mindustry.client.utils.BiasedLevenshtein;
import mindustry.entities.Units;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.gen.Unit;
import mindustry.type.UnitType;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;

import static mindustry.Vars.*;


public class UnitPicker extends BaseDialog {
    public UnitType found;
    Seq<UnitType> sorted = content.units().copy();

    public UnitPicker(){
        super("Unit Picker");

        onResize(this::build);
        shown(this::build);
        setup();
        keyDown(KeyCode.enter, () -> findUnit(sorted.first()));
    }

    void build(){
        cont.clear();
        buttons.clear();
        addCloseButton();

        Seq<Image> imgs = new Seq<>();
        for(int i = 0; i < 10; i++){
            imgs.add(new Image());
        }
        TextField searchField = cont.field("", string -> {
            sorted = sorted.sort((b) -> BiasedLevenshtein.biasedLevenshtein(string, b.name));
            for (int i = 0; i < imgs.size; i++) {
                Image region = new Image(sorted.get(i).icon(Cicon.large));
                region.setSize(32);
                imgs.get(i).setDrawable(region.getDrawable());
            }
        }).get();
        for(Image img : imgs){
            cont.row().add(img);
        }

        Core.app.post(searchField::requestKeyboard);
    }

    public void findUnit(UnitType found) {
        if (found != null) {
            Unit find = Units.closest(player.team(), player.x, player.y, u -> !u.isPlayer() && u.type == found && !u.dead && !(u.controller() instanceof FormationAI));
            if (find == null) find = Units.closest(player.team(), player.x, player.y, u -> !u.isPlayer() && u.type == found && !u.dead); // Either no unit or unit is commanded, search for commanded units
            if (find != null) {
                Call.unitControl(player, find); // Switch to unit
                this.found = null; // No need to check if the player has managed to take control as it is very unlikely that 2 players attempt this on the same unit at once.
            } else {
                new Toast(5f).label(() ->"No " + found + " was found, automatically switching to that unit when it spawns (set picked unit to alpha to cancel).");
                this.found = found;
            }
        }
        hide();
    }

    private void setup(){
        Events.on(EventType.UnitChangeEvent.class, event -> { //TODO: Test Player.lastReadUnit
            if (event.unit.team == player.team() && event.player != player) {
                Unit find = Units.closest(player.team(), player.x, player.y, u -> !u.isPlayer() && u.type == found && !u.dead);
                if (find != null) {
                    Call.unitControl(player, find);
                    Timer.schedule(() -> {
                        if (find.isPlayer()) {
                            Toast t = new Toast(2);
                            if (player.unit() == find) { found = null; t.add("Successfully switched units.");} // After we switch units successfully, stop listening for this unit
                            else if (find.getPlayer() != null) { t.add("Failed to become " + found + ", " + find.getPlayer().name + " is already controlling it (likely using unit sniper).");} // TODO: make these responses a method in UnitPicker
                        }
                    }, net.client() ? netClient.getPing()/1000f+.3f : .025f);
                }
            }
        });

        Events.on(EventType.UnitCreateEvent.class, event -> {
            Log.info(event.unit.dead + " " + event.unit.type + " " + event.unit.team + " " + event.unit.isPlayer());
            if (!event.unit.dead && event.unit.type == found && event.unit.team == player.team() && !event.unit.isPlayer()) {
                Call.unitControl(player, event.unit);
                Timer.schedule(() -> {
                    if (event.unit.isPlayer()) {
                        Toast t = new Toast(2);
                        if (player.unit() == event.unit) { found = null; t.add("Successfully switched units.");}  // After we switch units successfully, stop listening for this unit
                        else if (event.unit.getPlayer() != null) { t.add("Failed to become " + found + ", " + event.unit.getPlayer().name + " is already controlling it (likely using unit sniper).");}
                    }
                }, net.client() ? netClient.getPing()/1000f+.3f : .025f);
            }
        });
    }
}
