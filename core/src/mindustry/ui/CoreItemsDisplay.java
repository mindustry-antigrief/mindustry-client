package mindustry.ui;

import arc.math.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.client.*;
import mindustry.core.*;
import mindustry.type.*;
import mindustry.world.blocks.storage.CoreBlock.*;
import mindustry.world.modules.*;

import java.util.*;

import static mindustry.Vars.*;

public class CoreItemsDisplay extends Table{
    private final Bits usedItems = new Bits();
    private CoreBuild core;
    public CoreItemDisplayMode mode = CoreItemDisplayMode.disabled;
    private final CoreItemTracker inputItems = new CoreItemTracker(), totalItems = new CoreItemTracker();

    public CoreItemsDisplay(){
        rebuild();
        ClientVars.coreItemsDisplay = this;
    }

    public void resetUsed(){
        usedItems.clear();
        background(null);
        inputItems.reset();
        totalItems.reset();
    }

    void rebuild(){
        clear();
        if(!usedItems.isEmpty()){
            background(Styles.black6);
            margin(4);
        }

        clicked(() -> {
            mode = mode.next();
            rebuild();
        });
        update(() -> {
            core = Vars.player.team().core();
            if(core == null) return;
            inputItems.update();
            totalItems.update(core.items);

            if(content.items().contains(item -> core != null && core.items.get(item) > 0 && usedItems.getAndSet(item.id))){
                rebuild();
            }
        });

        int i = 0;

        for(Item item : content.items()){
            if(usedItems.get(item.id)){
                image(item.uiIcon).size(iconSmall).padRight(3).tooltip(t -> t.background(Styles.black6).margin(4f).add(item.localizedName).style(Styles.outlineLabel));
                //TODO leaks garbage
                if (mode == CoreItemDisplayMode.disabled) label(() -> core == null ? "0" : UI.formatAmount(core.items.get(item))).padRight(3).minWidth(52f).left();
                else if (mode == CoreItemDisplayMode.positiveOnly) label(() -> core == null ? "0" : formatAmount(inputItems.getAverage(6, item))).padRight(3).minWidth(52f).left();
                else if (mode == CoreItemDisplayMode.all) label(() -> core == null ? "0" : formatAmount(totalItems.getAverageChange(6, item))).padRight(3).minWidth(52f).left();

                if(++i % 4 == 0){
                    row();
                }
            }
        }

    }

    public void addItem(Item item, int amount){
        if (amount > 0) inputItems.add(item, amount);
    }

    public static String formatAmount(float rate){
        float mag = Math.abs(rate);
        if(mag == 0f) return "[lightgray]0[]";
        String out = rate > 0 ? "[green]" : "[red]";
        if(mag >= 100f){
            return out + UI.formatAmount(Math.round((double)mag)) + (mag >= 1000f ? "[]" : "");
        }
        // 3 significant figures
        int front = (int)Math.floor(Math.log10(mag) + 1); // round up even when the number ends on the integer
        int dp = Mathf.clamp(3 - front, 0, 3);
        return out + Strings.fixed(mag, dp) + "[]";
    }

    public enum CoreItemDisplayMode{
        disabled,
        positiveOnly,
        all;
        public CoreItemDisplayMode next(){
            return values()[(ordinal() + 1) % values().length];
        }
    }

    // Helper class to track the movement of items
    public static class CoreItemTracker{
        private final static int pollScl = 30; // every 30 frames or something
        private final static float rateMultiplier = 60f / pollScl;
        private float lastUpdate = Time.time;
        private final int numItems = content.items().size;
        private int[] itemRates = new int[numItems * 3600];
        private boolean hasInit = false;

        /* The index of the CURRENT group of items */
        private int idx = 0;

        private void checkCapacity(){
            if (idx + numItems >= itemRates.length){
                itemRates = Arrays.copyOf(itemRates, itemRates.length + numItems * 3600); // another half hour of tracking
            }
        }

        private int checkUpdate(){
            if (!hasInit) {
                hasInit = true;
                lastUpdate = Time.time;
                return 1;
            }
            float dt = Time.time - lastUpdate;
            int steps = (int)(dt / pollScl);
            lastUpdate += pollScl * steps;
            return steps;
        }

        public void reset(){
            int targetLength = numItems * 3600;
            if (itemRates.length != targetLength) itemRates = new int[targetLength];
            //https://stackoverflow.com/questions/9128737/fastest-way-to-set-all-values-of-an-array
            itemRates[0] = 0;
            for (int i = 1; i < targetLength; i += i) {
                System.arraycopy(itemRates, 0, itemRates, i, Math.min(targetLength - i, i));
            }
            idx = 0;
            hasInit = false;
        }

        public void update(ItemModule items){
            int steps = checkUpdate();
            if (steps <= 0) return;
            idx += numItems * steps;
            checkCapacity();
            int[] itemsitems = items.getAllItems();
            for(int i = 0; i < numItems; ++i){
                itemRates[idx + i] = itemsitems[i]; // vectorization when
            }
        }

        public void update(){
            int steps = checkUpdate();
            if (steps <= 0) return;
            idx += numItems * steps;
            checkCapacity();
        }

        public void add(Item item, int amount){
            add(item.id, amount);
        }

        public void add(int item, int amount){
            itemRates[idx + item] += amount;
        }

        public float getAverage(int steps, Item item){
            float avg = 0;
            int actualSteps = 0;
            // We take the <steps> steps before the current
            for (int i = idx + item.id - numItems; i >= 0 && actualSteps < steps; i -= numItems){
                avg += itemRates[i];
                ++actualSteps;
            }
            return avg / actualSteps * rateMultiplier;
        }

        public float getAverageChange(int steps, Item item){
            int actualSteps = Math.min(idx / numItems, steps);
            if (actualSteps <= 0) return 0;
            int id = item.id;
            return (float)(itemRates[idx + id] - itemRates[idx + id - actualSteps * numItems]) / actualSteps * rateMultiplier;
        }
    }
}
