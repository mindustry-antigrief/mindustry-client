package mindustry.ui.dialogs;

import arc.scene.ui.layout.*;
import arc.struct.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;

public class TransferDialog extends FloatingDialog{
    public Array<TransferItem> transferRequests = new Array<>();
    private Array<TransferItem> previous = new Array<>();

    public TransferDialog(){
        super("transfer");
        this.addCloseButton();
        shown(this::rebuild);
    }

    public void build(){
        /*
        Plan:
        Have table of requests
        Each request has 3 sub items:
            - source (coords or unit items)
            - item type
            - dst (type of block, core, coords, or unit)
            - remove
         */
        cont.pane(full -> {
            Table requests = new Table();
            for(TransferItem transferItem : transferRequests){
                requests.add(transferItem.show());
                requests.row();
            }
            full.add(requests);
        });
        cont.addImageButton(Icon.add, () -> {
//            transferRequests.add(new TransferItem(new TransferEndpoint(Vars.player.getClosestCore().tile),
//                new TransferEndpoint(Blocks.thoriumReactor), Items.thorium));
//            rebuild();
            new TransferCreateDialog().show();
        });
        previous = transferRequests.copy();
    }

    public void rebuild(boolean force){
        if(force || previous != transferRequests){
            cont.clear();
            build();
        }
    }

    public void rebuild(){
        rebuild(false);
    }
}
