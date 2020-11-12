package mindustry.client.ui;

import arc.*;
import arc.input.*;
import arc.scene.ui.*;
import arc.scene.utils.*;
import arc.struct.*;
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
    private TextField findField = null;

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
            if(found == null){
                hide();
            }

            Unit find = Units.closest(player.team(), player.x, player.y, u -> !u.isPlayer() && u.type == found && !u.dead);
                if(find != null) {
                    Call.unitControl(player, find);
                    ui.chatfrag.addMessage("worked", "client");
                    this.hide();
                } else { ui.chatfrag.addMessage("no " + found + " was found, you will switch to it when it spawns", "client"); this.hide(); }

        });
    }

    public UnitPicker show(){
        build();
        super.show();
        Core.scene.setKeyboardFocus(findField);
        return this;
    }
}
