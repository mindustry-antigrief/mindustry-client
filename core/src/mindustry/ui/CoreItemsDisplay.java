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

import static mindustry.Vars.*;

public class CoreItemsDisplay extends Table{
    private final ObjectSet<Item> usedItems = new ObjectSet<>();
    private CoreBuild core;
    public static CoreItemDisplayMode mode = CoreItemDisplayMode.disabled; //FINISHME: make this not static

    public CoreItemsDisplay(){
        rebuild();
    }

    public void resetUsed(){
        usedItems.clear();
        background(null);
    }

    void rebuild(){
        clear();
        if(usedItems.size > 0){
            background(Styles.black6);
            margin(4);
        }

        clicked(() -> {
            mode = mode.next();
            ClientVars.coreItems.stopFlow();
            ClientVars.coreItems.clear();
            if(mode == CoreItemDisplayMode.negative){
                ClientVars.coreItems.set(player.core().items());
            }
            rebuild();
        });
        update(() -> {
            core = Vars.player.team().core();
            if(core == null) return;
            switch(mode){
                case disabled: break;
                case negative: {
                    for(Item item : content.items()){
                        addItemRate(item, core.items.get(item) - ClientVars.coreItems.get(item));
                    }
                    // no break
                }
                case positiveOnly: {
                    ClientVars.coreItems.updateFlow();
                    break;
                }
            }

            if(content.items().contains(item -> core != null && core.items.get(item) > 0 && usedItems.add(item))){
                rebuild();
            }
        });

        int i = 0;

        for(Item item : content.items()){
            if(usedItems.contains(item)){
                image(item.uiIcon).size(iconSmall).padRight(3).tooltip(t -> t.background(Styles.black6).margin(4f).add(item.localizedName).style(Styles.outlineLabel));
                //TODO leaks garbage
                if(mode != CoreItemDisplayMode.disabled)
                    label(() -> core == null || !ClientVars.coreItems.flowHasEnoughData() ? "..." : formatAmount(ClientVars.coreItems.getFlowRate(item))).padRight(3).minWidth(52f).left();
                else label(() -> core == null ? "0" : UI.formatAmount(core.items.get(item))).padRight(3).minWidth(52f).left();

                if(++i % 4 == 0){
                    row();
                }
            }
        }

    }

    public static void addItemRate(Item item, int amount){
        if(mode != CoreItemDisplayMode.negative && amount < 0) return;
        ClientVars.coreItems.add(item, amount);
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
        negative;
        public CoreItemDisplayMode next(){
            return values()[(ordinal() + 1) % values().length];
        }
    }
}
