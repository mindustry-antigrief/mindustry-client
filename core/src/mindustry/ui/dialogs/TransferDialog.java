package mindustry.ui.dialogs;

import arc.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;

public class TransferDialog extends FloatingDialog{
    public Array<TransferItem> transferRequests = new Array<>();
    private Array<TransferItem> previous = new Array<>();

    public TransferDialog(){
        super("transfer");
        this.addCloseButton();
        shown(this::rebuild);
        Events.on(WorldLoadEvent.class, transferRequests::clear);
    }

    public void build(){
        /*
        There's a table of requests
        Each request has 3 sub items:
            - source (coords or player items)
            - item type
            - dst (type of block, core, coords, or player)
            - remove
         */
        cont.pane(full -> {
            Table requests = new Table();
            for(TransferItem transferItem : transferRequests){
                requests.add(transferItem.show());
                requests.add(new Label(" "));
                requests.addImageButton(Icon.cancel, () -> {
                    transferRequests.remove(transferItem);
                    rebuild();
                });
                ImageButton button = new ImageButton();
                if(transferItem.paused){
                    button.replaceImage(new Image(Icon.play));
                }else{
                    button.replaceImage(new Image(Icon.pause));
                }
                button.clicked(() -> {
                    transferItem.paused = !transferItem.paused;

                    if(transferItem.paused){
                        button.replaceImage(new Image(Icon.play));
                    }else{
                        button.replaceImage(new Image(Icon.pause));
                    }
                });
                requests.add(button);
                requests.row();
            }
            full.add(requests);
        });
        cont.addImageButton(Icon.add, () -> {
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
