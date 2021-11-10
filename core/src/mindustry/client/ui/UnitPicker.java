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

    public UnitPicker(){
        super("@client.unitpicker");

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
        Seq<Label> labels = new Seq<>();
        for(int i = 0; i < 10; i++){
            imgs.add(new Image());
            labels.add(new Label(""));
        }
        TextField searchField = cont.field("", string -> {
            sorted = sorted.sort((b) -> BiasedLevenshtein.biasedLevenshteinInsensitive(string, b.localizedName));
            for (int i = 0; i < imgs.size; i++) {
                Image region = new Image(sorted.get(i).uiIcon);
                imgs.get(i).setDrawable(region.getDrawable());
                labels.get(i).setText(sorted.get(i).localizedName);
            }
        }).get();
        for(int i = 0; i < 10; i++){
            cont.row().add(imgs.get(i)).size(64);
            cont.add(labels.get(i));
        }

        Core.app.post(searchField::requestKeyboard);
    }

    public boolean findUnit(UnitType type) {
        return findUnit(type, false);
    }

    public boolean findUnit(UnitType type, boolean silent) {
        hide();
        if (type == null) return false;

        Unit found = Units.closest(player.team(), player.x, player.y, u -> !u.isPlayer() && u.type == type && !u.dead && !(u.controller() instanceof FormationAI || u.controller() instanceof LogicAI));
        if (found == null) found = Units.closest(player.team(), player.x, player.y, u -> !u.isPlayer() && u.type == type && !u.dead && !(u.controller() instanceof FormationAI)); // Include logic units
        if (found == null) found = Units.closest(player.team(), player.x, player.y, u -> !u.isPlayer() && u.type == type && !u.dead); // Include formation units

        Toast t = silent ? null : new Toast(3);
        if (found != null) {
            Call.unitControl(player, found); // Switch to unit
            if (!silent) t.add("@client.unitpicker.success");
            this.type = null;
        } else {
            if (!silent) t.add(Core.bundle.format("client.unitpicker.notfound", type));
            this.type = type;
        }
        return found != null;
    }

    private void setup(){
        Events.on(EventType.UnitChangeEvent.class, event -> { // FINISHME: Test Player.lastReadUnit also get rid of this dumb ping prediction stuff
            if (type == null) return;
            if (!event.player.isLocal() && event.unit.team == player.team()) {
                Unit f = Units.closest(player.team(), player.x, player.y, u -> !u.isPlayer() && u.type == type && !u.dead && !(u.controller() instanceof FormationAI || u.controller() instanceof LogicAI));
                if (f == null) f = Units.closest(player.team(), player.x, player.y, u -> !u.isPlayer() && u.type == type && !u.dead && !(u.controller() instanceof FormationAI)); // Include logic units
                if (f == null) f = Units.closest(player.team(), player.x, player.y, u -> !u.isPlayer() && u.type == type && !u.dead); // Include formation units
                Unit find = f;
                if (find != null) {
                    type = null;
                    Call.unitControl(player, find);
                    Timer.schedule(() -> Core.app.post(() -> {
                        if (find.isPlayer()) {
                            Toast t = new Toast(3);
                            if (find.isLocal()) {
                                type = null;
                                t.add("@client.unitpicker.success");
                            } else if (find.getPlayer() != null && !find.isLocal()) {
                                t.add(Core.bundle.format("client.unitpicker.alreadyinuse", find.type, find.getPlayer().name));
                                type = find.type;
                            } else t.add("[scarlet]This wasn't supposed to happen...");
                        }
                    }), net.client() ? netClient.getPing()/1000f + .3f: 0);
                }
            }
        });

        Events.on(EventType.UnitUnloadEvent.class, event -> { // FINISHME: Run on all unit creations, test that it actually works
            if (type == null) return;
            if (!event.unit.dead && event.unit.type == type && event.unit.team == player.team() && !event.unit.isPlayer()) {
                type = null;
                Timer.schedule(() -> {
                    Call.unitControl(player, event.unit);
                    Timer.schedule(() -> Core.app.post(() -> {
                        if (event.unit.isPlayer()) {
                            Toast t = new Toast(3);
                            if (event.unit.isLocal()) {
                                t.add("@client.unitpicker.success");
                            } else if (event.unit.getPlayer() != null && !event.unit.isLocal()) {
                                type = event.unit.type;
                                t.add(Core.bundle.format("client.unitpicker.alreadyinuse", event.unit.type, event.unit.getPlayer().name));
                            }
                        } else Time.run(60, () -> findUnit(event.unit.type, true));
                    }), net.client() ? netClient.getPing()/1000f + .3f : 0);
                }, net.client() ? netClient.getPing()/1000f + .3f : 0);
            }
        });

        Events.on(EventType.WorldLoadEvent.class, event -> {
            if (!ClientVars.syncing) {
                type = null;
                Time.run(60, () -> findUnit(Core.settings.getBool("automega") && state.isGame() && (player.unit().type == null || player.unit().type != UnitTypes.mega) ? UnitTypes.mega : null));
            }
        });
    }
}
