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
import mindustry.world.blocks.*;

import java.util.concurrent.atomic.*;

public class TransferCreateDialog extends FloatingDialog{
    private TransferEndpoint start;
    private TransferEndpoint end;

    public TransferCreateDialog(){
        super("create_transfer");
        build();
    }

    private Table buildSingleTilePickup(boolean isStart){
        Table table = new Table();
        TextField x = new TextField("0");
        TextField y = new TextField("0");
        x.setValidator((string) -> Strings.canParsePostiveInt(string) && Integer.parseInt(string) <= Vars.world.width());
        y.setValidator((string) -> Strings.canParsePostiveInt(string) && Integer.parseInt(string) <= Vars.world.height());
        table.add(x, new Label(","), y);
        Image blockImage = new Image();
        blockImage.update(() -> {
            if(x.isValid() && y.isValid()){
                Tile tile = Vars.world.tile(Integer.parseInt(x.getText()), Integer.parseInt(y.getText()));
                blockImage.setDrawable(tile.block() == Blocks.air? tile.floor().icon(Cicon.small) : tile.block().icon(Cicon.small));
            }
        });
         table.addImageButton(Icon.ok, () -> {
             if(x.isValid() && y.isValid()){
                 TransferEndpoint endpoint =  new TransferEndpoint(Vars.world.tile(Integer.parseInt(x.getText()), Integer.parseInt(x.getText())));
                 if(isStart){
                     start = endpoint;
                 }else{
                     end = endpoint;
                 }
             }
         });
        return table;
    }

    private Table buildPlayerPickup(boolean isStart){
        Table table = new Table();
        table.addImageButton(Icon.ok, () -> {
            TransferEndpoint endpoint = new TransferEndpoint(Vars.player);
            if(isStart){
                start = endpoint;
            }else{
                end = endpoint;
            }
        });
        return table;
    }

    private Table buildBlockTypePickup(boolean isStart){
        Table table = new Table();
        Table selector = new Table();
        AtomicReference<Block> selected = new AtomicReference<>();
        selected.set(null);
        ItemSelection.buildTable(selector, Vars.content.blocks(), selected::get, selected::set);
        table.add(selector);
        table.addImageButton(Icon.ok, () -> {
            if(selected.get() != null){
                TransferEndpoint endpoint = new TransferEndpoint(selected.get());
                if(isStart){
                    start = endpoint;
                }else{
                    end = endpoint;
                }
            }
        });
        return table;
    }

    public void build(){
        addCloseButton();
    }
}
