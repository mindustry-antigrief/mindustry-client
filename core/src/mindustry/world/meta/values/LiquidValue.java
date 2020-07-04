package mindustry.world.meta.values;

import arc.scene.ui.*;
import arc.scene.ui.layout.Table;
import mindustry.type.Liquid;
import mindustry.ui.*;
import mindustry.world.meta.StatValue;

import java.text.*;

public class LiquidValue implements StatValue{
    private final Liquid liquid;
    private final float amount;
    private final boolean perSecond;

    public LiquidValue(Liquid liquid, float amount, boolean perSecond){
        this.liquid = liquid;
        this.amount = amount;
        this.perSecond = perSecond;
    }

    @Override
    public void display(Table table){
        table.add(new LiquidDisplay(liquid, amount, perSecond));
    }

    public void display(Table table, float time){
        Table row = new Table();
        row.add(new LiquidDisplay(liquid, amount, perSecond));
        DecimalFormat format = new DecimalFormat("#.##");
        row.add(new Label(" (" + format.format(amount / (time / 60)) + " / second)"));
        table.add(row).padRight(5);

//        table.add(new LiquidDisplay(liquid, amount, perSecond));
    }
}
