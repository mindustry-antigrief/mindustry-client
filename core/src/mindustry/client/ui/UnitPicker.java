package mindustry.client.ui;

import arc.*;
import arc.graphics.Color;
import arc.input.*;
import arc.scene.ui.*;
import arc.scene.utils.*;
import arc.struct.*;
import arc.util.Log;
import arc.util.Strings;
import mindustry.entities.Units;
import mindustry.gen.Call;
import mindustry.gen.Unit;
import mindustry.type.UnitType;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;

import static mindustry.Vars.*;


public class UnitPicker extends BaseDialog{
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
            sorted = sorted.sort((b) -> Strings.levenshtein(string, b.name));
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

        keyDown(KeyCode.enter, () -> {
            Log.info(found);
            if (found != null) {
                Unit find = Units.closest(player.team(), player.x, player.y, u -> !u.isPlayer() && u.type == found && !u.dead && u.formation == null);
                if ( find == null) { Log.info("all controlled or none exists"); find = Units.closest(player.team(), player.x, player.y, u -> !u.isPlayer() && u.type == found && !u.dead); }
                if (find != null) {
                    Call.unitControl(player, find);
                    found = null; // No need to check if the player has managed to take control as it is very unlikely that 2 players attempt this on the same unit at once.
                } else {
                    ui.chatfrag.addMessage("No " + found + " was found, automatically switching to that unit when it spawns (set picked unit to alpha).", "Unit Picker", Color.gold);
                }
            }
            hide();
        });
    }

    public UnitPicker show(){
        build();
        super.show();
        Core.scene.setKeyboardFocus(findField);
        return this;
    }
}
