package mindustry.client.ui;

import arc.input.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.ui.*;
import arc.scene.utils.*;
import arc.struct.*;
import mindustry.client.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;
import mindustry.world.*;
import static mindustry.Vars.*;
import static mindustry.client.Client.*;
import static mindustry.client.utils.Levenshtein.distanceCompletion;

public class FindDialog extends FloatingDialog{
    public TextField findField = null;

    public FindDialog(){
        super("find");
    }

    public void build(){
        addCloseButton();
        Array<Image> imgs = new Array<>();
        for(int i = 0; i < 10; i += 1){
            imgs.add(new Image());
        }
        findField = Elements.newField("", (string) -> {
            Array<Block> sorted = content.blocks().copy();
            sorted = sorted.sort((b) -> distanceCompletion(string, b.name));
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

        keyDown(KeyCode.ENTER, () -> {
            if(found == null){
                hide();
            }
            Array<Tile> tiles = new Array<>();
            for(Tile[] t : world.getTiles()){
                for(Tile tile2 : t){
                    if(tile2.block() != null){
                        if(tile2.block().name.equals(found.name) && tile2.getTeam() == player.getTeam()){
                            tiles.add(tile2);
                        }
                    }
                }
            }
            if(tiles.size > 0){
                float dist = Float.POSITIVE_INFINITY;
                Tile closest = null;

                for(Tile t : tiles){
                    float d = Mathf.dst(player.x, player.y, t.x, t.y);
                    if(d < dist){
                        closest = t;
                        dist = d;

                    }
                }
                if(closest != null){
                    Client.targetPosition = new Vec2(closest.x, closest.y);
                    ui.chatfrag.addMessage(String.format("%d, %d (/go to travel there)", (int)closest.x, (int)closest.y), "client");
                    hide();
                }
            }
        });
    }

    public FindDialog show(){
        return (FindDialog)super.show();
    }
}
