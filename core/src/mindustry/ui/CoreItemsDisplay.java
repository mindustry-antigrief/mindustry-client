package mindustry.ui;

import arc.*;
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
    private static int trackSteps = 6;
    public static boolean trackItems = Core.settings != null && Core.settings.getBool("trackcoreitems") && net != null && !net.server();
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
            if(!trackItems && mode == CoreItemDisplayMode.inputOnly) inputItems.reset();
            if(!trackItems && mode == CoreItemDisplayMode.all) totalItems.reset();
            rebuild();
        });
        update(() -> {
            core = Vars.player.team().core();
            if(core == null) return;
            if(trackItems || mode == CoreItemDisplayMode.inputOnly) inputItems.update();
            if(trackItems || mode == CoreItemDisplayMode.all) totalItems.update(core.items);

            if(content.items().contains(item -> core != null && core.items.get(item) > 0 && !usedItems.getAndSet(item.id))){
                rebuild();
            }
        });

        int i = 0;

        for(Item item : content.items()){
            if(usedItems.get(item.id)){
                image(item.uiIcon).size(iconSmall).padRight(3).tooltip(t -> t.background(Styles.black6).margin(4f).add(item.localizedName).style(Styles.outlineLabel));
                //TODO leaks garbage
                if (mode == CoreItemDisplayMode.disabled) label(() -> core == null ? "0" : UI.formatAmount(core.items.get(item))).padRight(3).minWidth(52f).left();
                else if (mode == CoreItemDisplayMode.inputOnly) label(() -> core == null ? "0" : formatAmount(inputItems.getAverage(trackSteps, item))).padRight(3).minWidth(52f).left();
                else if (mode == CoreItemDisplayMode.all) label(() -> core == null ? "0" : formatAmount(totalItems.getAverageChange(trackSteps, item))).padRight(3).minWidth(52f).left();

                if(++i % 4 == 0){
                    row();
                }
            }
        }

    }

    public void addItem(Item item, int amount){
        if(amount > 0 && (trackItems || mode == CoreItemDisplayMode.inputOnly)) inputItems.add(item, amount);
    }

    public static String formatAmount(float rate){
        if(Float.isNaN(rate)) return "[lightgray]...";
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
        inputOnly,
        all;
        public CoreItemDisplayMode next(){
            return values()[(ordinal() + 1) % values().length];
        }
    }

    // Helper class to track the movement of items
    public static class CoreItemTracker{
        private final static int pollScl = 30; // every 30 time units or something
        private final static float rateMultiplier = 60f / pollScl;
        private float lastUpdate = 0;
        private final int numItems = content.items().size;
        private int[] itemRates = new int[numItems * (trackItems ? 1800 : trackSteps + 1)]; // +1 because we don't want average to loop around to itself
        private int stepsRecorded = 0;

        /* The index of the CURRENT group of items */
        private int idx = 0;

        /* Change the value of idx. Also clear the array accordingly if it wraps around, or lengthen the array if it doesn't. */
        private void changeIdx(int steps){
            if(steps <= 0) return;
            if(trackItems){
                idx += numItems * steps;
                while(idx >= itemRates.length){
                    itemRates = Arrays.copyOf(itemRates, itemRates.length + numItems * 1800); // another 15min of tracking
                }
            }
            else{
                int didx = numItems * steps;

                // If we somehow skip enough time to wrap around, reset the array
                if(didx >= itemRates.length){
                    Arrays.fill(itemRates, 0);
                    idx = 0;
                }
                else{
                    // Advance to correct position
                    int end = (idx + didx) % itemRates.length;
                    if(idx < end){
                        Arrays.fill(itemRates, idx + numItems, end + numItems, 0);
                    }else{
                        Arrays.fill(itemRates, 0, end + numItems, 0);
                        Arrays.fill(itemRates, idx + numItems, itemRates.length, 0);
                    }
                    idx = end;
                }
            }
        }

        private int checkUpdate(){
            if(stepsRecorded == 0){
                lastUpdate = Time.time;
                stepsRecorded = 1;
                return 1;
            }
            float dt = Time.time - lastUpdate;
            int steps = (int)(dt / pollScl);
            lastUpdate += pollScl * steps;
            stepsRecorded += steps;
            return steps;
        }

        public void reset(){
            int targetLength = numItems * (trackItems ? 1800 : trackSteps + 1);
            if (itemRates.length != targetLength) itemRates = new int[targetLength];
            itemRates[0] = 0;
            for (int i = 1; i < targetLength; i <<= 1) {
                System.arraycopy(itemRates, 0, itemRates, i, Math.min(targetLength - i, i));
            }
            idx = 0;
            stepsRecorded = 0;
        }

        public void update(ItemModule items){
            // TODO handle interpolation of data
            int steps = checkUpdate();
            if (steps <= 0) return;
            changeIdx(steps);
            if (numItems >= 0) System.arraycopy(items.getAllItems(), 0, itemRates, idx, numItems); // ok intellij, i trust that arraycopy will not be slow for <30 elements
        }

        public void update(){
            int steps = checkUpdate();
            if (steps <= 0) return;
            changeIdx(steps);
        }

        public void add(Item item, int amount){
            add(item.id, amount);
        }

        public void add(int item, int amount){
            itemRates[idx + item] += amount;
        }

        public float getAverage(int steps, Item item){
            if(stepsRecorded <= trackSteps) return Float.NaN;
            if(!trackItems){
                steps = Math.min(steps, trackSteps);
                float avg = 0;
                for (int i = idx + item.id, actualSteps = 0; actualSteps < steps; ++actualSteps){
                    i = i - numItems;
                    if(i < 0) i += itemRates.length; // Wrap around
                    avg += itemRates[i];
                }
                return avg / steps * rateMultiplier;
            }
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
            if(stepsRecorded <= trackSteps) return Float.NaN;
            int id = item.id;
            if(trackItems){
                int actualSteps = Math.min(idx / numItems, steps);
                if (actualSteps <= 0) return 0;
                return (float)(itemRates[idx + id] - itemRates[idx + id - actualSteps * numItems]) / actualSteps * rateMultiplier;
            }else{
                int from = idx - steps * numItems;
                from = (from + itemRates.length) % itemRates.length;
                return (float)(itemRates[idx + id] - itemRates[from + id]) / steps * rateMultiplier;
            }
        }
    }
}
