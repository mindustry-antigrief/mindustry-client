package mindustry.ui;

import arc.graphics.g2d.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.util.Scaling;
import arc.util.Strings;
import mindustry.core.*;
import mindustry.type.*;

public class ItemImage extends Stack{

    public ItemImage(TextureRegion region, int amount){

        add(new Table(o -> {
            o.left();
            o.add(new Image(region)).size(32f);
        }));

        add(new Table(t -> {
            t.left().bottom();
            t.add(amount > 1000 ? UI.formatAmount(amount) : amount + "");
            t.pack();
        }));
    }

    public ItemImage(TextureRegion region){
        Table t = new Table().left().bottom();

        add(new Image(region));
        add(t);
    }

    public ItemImage(ItemStack stack){

        add(new Table(o -> {
            o.left();
            o.add(new Image(stack.item.icon(Cicon.medium))).size(32f);
        }));

        if(stack.amount != 0){
            add(new Table(t -> {
                t.left().bottom();
                t.add(stack.amount > 1000 ? UI.formatAmount(stack.amount) : stack.amount + "").style(Styles.outlineLabel);
                t.pack();
            }));
        }
    }

    public ItemImage(ItemStack stack, String rate){

        add(new Table(o -> {
            o.left();
            o.add(new Image(stack.item.icon(Cicon.medium))).size(32f);
        }));

        if(stack.amount != 0){
            add(new Table(t -> {
                t.left().bottom().defaults().left();

                t.add(stack.amount > 1000 ? UI.formatAmount(stack.amount) : stack.amount + "").style(Styles.outlineLabel).row();
                t.add( rate + "/s", .5f).style(Styles.outlineLabel);
                t.pack();
            }));
        }
    }
}
