package mindustry.game;

import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import mindustry.type.*;

public class TransferItem{
    public Item item;
    public TransferEndpoint start;
    public TransferEndpoint end;

    public TransferItem(TransferEndpoint startPoint, TransferEndpoint endpoint, Item item){
        start = startPoint;
        end = endpoint;
        this.item = item;
    }

    public void run(){
        start.transferToPlayer(item);
        end.transferFromPlayer(item);
    }

    public Table show(){
        Table table = new Table();
        table.add(new Label(start.toString()));
        table.add(new Label(end.toString()));
        return table;
    }
}
