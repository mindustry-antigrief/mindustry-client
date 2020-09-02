package mindustry.client;

import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import mindustry.type.*;

public class TransferItem{
    public Item item;
    public TransferEndpoint start;
    public TransferEndpoint end;
    public boolean paused = false;
    public boolean anyItem;

    public TransferItem(TransferEndpoint startPoint, TransferEndpoint endpoint, Item item, boolean anyItem){
        start = startPoint;
        end = endpoint;
        this.item = item;
        this.anyItem = anyItem;
    }

    public void run(){
        if(paused){
            return;
        }
        try{
            start.transferToPlayer(item, anyItem);
            end.transferFromPlayer(item);
            if(!end.type.equals("player")){
                start.transferFromPlayer(item);
            }
        }catch(NullPointerException ignored){} //SHH EVERYTHING'S FINE
    }

    public void update(){
        start.rebuild();
        end.rebuild();
    }

    public Table show(){
        Table table = new Table();
        table.add(start.toElement());
        table.add(new Label(" "));
        table.add(end.toElement());
        return table;
    }
}
