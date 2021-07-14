package mindustry.ui;

import arc.graphics.g2d.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import mindustry.core.*;
import mindustry.type.*;

import static mindustry.Vars.iconMed;

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
        this(stack, 0);
    }

    public ItemImage(ItemStack stack, float timePeriod){

        add(new Table(o -> {
            o.left();
            o.add(new Image(stack.item.uiIcon)).size(iconMed);
        }));

        if(stack.amount != 0){
            add(new Table(t -> {
                t.left().bottom().defaults().left();

                t.add(stack.amount > 1000 ? UI.formatAmount(stack.amount) : stack.amount + "").style(Styles.outlineLabel);
                if (timePeriod != 0) {
                    t.row();
                    t.add(Strings.autoFixed(stack.amount / timePeriod, 2) + "/s", .5f).style(Styles.outlineLabel);
                }
                t.pack();
            }));
        }
    }
}
