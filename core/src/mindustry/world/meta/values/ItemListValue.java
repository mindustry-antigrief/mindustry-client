package mindustry.world.meta.values;

import arc.scene.ui.layout.*;
import arc.util.Strings;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.meta.*;

public class ItemListValue implements StatValue{
    private final ItemStack[] stacks;
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

    /** Scuffed way of displaying item usage rates using a {@link Stat#productionTime} */
    public void display(Table table, float prodtime){
        for(ItemStack stack : stacks){
            float value = stack.amount / prodtime;
            int precision = Math.abs((int)value - value) <= 0.001f ? 0 : Math.abs((int)(value * 10) - value * 10) <= 0.001f ? 1 : 2;

            table.add(new ItemDisplay(stack.item, stack.amount, displayName, Strings.fixed(value, precision))).padRight(5);
        }
    }
}
