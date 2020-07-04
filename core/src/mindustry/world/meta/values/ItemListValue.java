package mindustry.world.meta.values;

import arc.math.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.Table;
import mindustry.type.ItemStack;
import mindustry.ui.ItemDisplay;
import mindustry.world.meta.StatValue;

import java.text.*;

public class ItemListValue implements StatValue{
    public final ItemStack[] stacks;
    private final boolean displayName;

    public ItemListValue(ItemStack... stacks){
        this(true, stacks);
    }

    public ItemListValue(boolean displayName, ItemStack... stacks){
        this.stacks = stacks;
        this.displayName = displayName;
    }

    @Override
    public void display(Table table){
        for(ItemStack stack : stacks){
            table.add(new ItemDisplay(stack.item, stack.amount, displayName)).padRight(5);
        }
    }

    public void display(Table table, float time){
        for(ItemStack stack : stacks){
            Table row = new Table();
            row.add(new ItemDisplay(stack.item, stack.amount, displayName));
            DecimalFormat format = new DecimalFormat("#.##");
            row.add(new Label(" (" + format.format(stack.amount / (time / 60)) + " / second)"));
            table.add(row).padRight(5);
        }
    }
}
