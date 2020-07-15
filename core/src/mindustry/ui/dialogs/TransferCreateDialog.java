package mindustry.ui.dialogs;

import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.ui.*;
import mindustry.world.*;

public class TransferCreateDialog extends FloatingDialog{
    private TransferEndpoint start;
    private TransferEndpoint end;

    public TransferCreateDialog(){
        super("create_transfer");
        build();
    }

    public void build(){
        Table typeSelector = new Table();
        typeSelector.addButton("Single tile", () -> {
            cont.removeChild(typeSelector);
            TextField x = new TextField("0");
            x.setValidator((string) -> Strings.canParsePostiveInt(string) && Integer.parseInt(string) < Vars.world.width());
            TextField y = new TextField("0");
            y.setValidator((string) -> Strings.canParsePostiveInt(string) && Integer.parseInt(string) < Vars.world.height());
            Label comma = new Label(",");
            cont.add(x, comma, y);
            Image blockImage = new Image(Icon.block);
            blockImage.update(() -> {
                if(x.isValid() && y.isValid()){
                    Tile tile = Vars.world.tile(Integer.parseInt(x.getText()), Integer.parseInt(y.getText()));
                    if(tile.block() == Blocks.air){
                        blockImage.setDrawable(tile.floor().icon(Cicon.small));
                    }else{
                        blockImage.setDrawable(tile.block().icon(Cicon.small));
                    }
                }
            });
            cont.add(blockImage);
            cont.addButton("Next", () -> {
                cont.removeChild(x);
                cont.removeChild(y);
                cont.removeChild(blockImage);
                cont.removeChild(comma);
            });
        });
        typeSelector.row();

        typeSelector.addButton("Block type", () -> {
            cont.removeChild(typeSelector);
        });
        typeSelector.row();

        typeSelector.addButton("Player items", () -> {
            cont.removeChild(typeSelector);
        });
        cont.add(typeSelector);
        addCloseButton();
    }
}
