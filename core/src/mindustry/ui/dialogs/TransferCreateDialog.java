package mindustry.ui.dialogs;

import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.blocks.*;

import java.util.concurrent.atomic.*;

import static mindustry.Vars.defaultTilePos;

public class TransferCreateDialog extends FloatingDialog{
    private TransferEndpoint start;
    private TransferEndpoint end;

    public TransferCreateDialog(){
        super("create_transfer");
        build();
    }

    private Table buildSingleTilePickup(boolean isStart){
        Table table = new Table();
        TextField x = new TextField(Integer.toString(Pos.x(defaultTilePos)));
        TextField y = new TextField(Integer.toString(Pos.y(defaultTilePos)));
        x.setWidth(20);
        y.setWidth(20);
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
        table.row();
        table.add(blockImage);
         table.addImageButton(Icon.ok, () -> {
             if(x.isValid() && y.isValid()){
                 TransferEndpoint endpoint =  new TransferEndpoint(Vars.world.tile(Integer.parseInt(x.getText()), Integer.parseInt(y.getText())));
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

    private Table buildCorePickup(boolean isStart){
        Table table = new Table();
        table.addImageButton(Icon.ok, () -> {
            TransferEndpoint endpoint = new TransferEndpoint();
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

    private Table buildSelector(boolean isStart){
        Table table = new Table();
        Table item = new Table();
        TextButton button = new TextButton("Specific tile");
        button.clicked(() -> {
            item.clear();
            item.add(buildSingleTilePickup(isStart));
        });
        table.add(button).growX();
        table.row();

        button = new TextButton("Player items");
        button.clicked(() -> {
            item.clear();
            item.add(buildPlayerPickup(isStart));
        });
        table.add(button).growX();
        table.row();

        button = new TextButton("Core items");
        button.clicked(() -> {
            item.clear();
            item.add(buildCorePickup(isStart));
        });
        table.add(button).growX();
        table.row();

        button = new TextButton("Block type");
        button.clicked(() -> {
            item.clear();
            item.add(buildBlockTypePickup(isStart));
        });
        table.add(button).growX();
        table.add(item);
        return table;
    }

    public void build(){
        cont.add(buildSelector(true)).growX();
        cont.add(buildSelector(false)).growX();
        cont.row();
        Table table = new Table();
        AtomicReference<Item> selected = new AtomicReference<>();
        selected.set(null);
        ItemSelection.buildTable(table, Vars.content.items(), selected::get, selected::set);
        CheckBox checkbox = new CheckBox("Any item");
        table.add(checkbox);
        cont.add(table.center());
        buttons.addImageButton(Icon.ok, () -> {
            if(start != null && end != null && selected.get() != null){
                Vars.ui.transfer.transferRequests.add(new TransferItem(start, end, selected.get(), checkbox.isChecked()));
                hide();
            }
        });
        addCloseButton();
    }
}
