package mindustry.client.ui;

import arc.*;
import arc.input.*;
import arc.scene.ui.*;
import arc.scene.utils.*;
import arc.struct.*;
import mindustry.ai.types.FormationAI;
import mindustry.client.utils.BiasedLevenshtein;
import mindustry.entities.Units;
import mindustry.gen.Call;
import mindustry.gen.Unit;
import mindustry.type.UnitType;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;

import static mindustry.Vars.*;


public class UnitPicker extends BaseDialog {
    public static UnitType found;
    private TextField findField;

    public UnitPicker(){
        super("Unit Picker");
    }

    public void build(){

        cont.clear();
        buttons.clear();
        clearListeners();
        addCloseButton();
        Seq<Image> imgs = new Seq<>();
        for(int i = 0; i < 10; i += 1){
            imgs.add(new Image());
        }
        findField = Elem.newField("", (string) -> {
            Seq<UnitType> sorted = content.units().copy();
            sorted = sorted.sort((b) -> BiasedLevenshtein.biasedLevenshtein(string, b.name));
            found = sorted.first();
            for(int i = 0; i < imgs.size - 1; i += 1){
                Image region = new Image(sorted.get(i).icon(Cicon.large));
                region.setSize(32);
                imgs.get(i).setDrawable(region.getDrawable());
            }

        });
        cont.add(findField);
        for(Image img : imgs){
            cont.row().add(img);
        }

        keyDown(KeyCode.enter, () -> findUnit(found));
    }

    public void findUnit(UnitType found) {
        if (found != null) {
            Unit find = Units.closest(player.team(), player.x, player.y, u -> !u.isPlayer() && u.type == found && !u.dead && !(u.controller() instanceof FormationAI));
            if (find == null) find = Units.closest(player.team(), player.x, player.y, u -> !u.isPlayer() && u.type == found && !u.dead); // Either no unit or unit is commanded, search for commanded units
            if (find != null) {
                Call.unitControl(player, find); // Switch to unit
                UnitPicker.found = null; // No need to check if the player has managed to take control as it is very unlikely that 2 players attempt this on the same unit at once.
            } else {
                new Toast(5f).label(() ->"No " + found + " was found, automatically switching to that unit when it spawns (set picked unit to alpha to cancel).");
            }
        }
        hide();
    }

    public UnitPicker show(){
        build();
        super.show();
        Core.scene.setKeyboardFocus(findField);
        return this;
    }
}
