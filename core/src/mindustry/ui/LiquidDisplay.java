package mindustry.ui;

import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import mindustry.type.*;

import static mindustry.Vars.*;

/** An ItemDisplay, but for liquids. */
public class LiquidDisplay extends Table{
    public final Liquid liquid;
    public final float amount;
    public final boolean perSecond;

    public LiquidDisplay(Liquid liquid, float amount, boolean perSecond){
        this.liquid = liquid;
        this.amount = amount;
        this.perSecond = perSecond;

        add(new Stack(){{
            add(new Image(liquid.uiIcon));

            if(amount != 0){
                Table t = new Table().left().bottom();
                t.add(Strings.autoFixed(amount, 2)).style(Styles.outlineLabel);
                if (perSecond) {
                    t.row();
                    t.add(Strings.autoFixed(amount, 2) + "/s", .5f).style(Styles.outlineLabel);
                }
                add(t);
            }
        }}).size(iconMed);

        add(liquid.localizedName);
    }
}
