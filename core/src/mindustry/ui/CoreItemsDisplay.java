package mindustry.ui;

import arc.scene.ui.layout.*;
import arc.struct.*;
import mindustry.*;
import mindustry.core.*;
import mindustry.type.*;
import mindustry.world.blocks.storage.CoreBlock.*;
import mindustry.world.modules.*;

import java.util.*;

import static mindustry.Vars.*;

public class CoreItemsDisplay extends Table{
    private final ObjectSet<Item> usedItems = new ObjectSet<>();
    private final int maxAverage = 10;
    private final HashMap<Item, Float> lastItemsAveraged = new HashMap<>();
    private final ItemModule lastItems = new ItemModule();
    private CoreBuild core;

    public CoreItemsDisplay(){
        rebuild();
        // add all items to the lists, else NullPointerException
        content.items().each((item) -> {
            lastItemsAveraged.put(item, (float)0);
            lastItems.set(item, 0);
        });
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

        update(() -> {
            core = Vars.player.team().core();

            if(content.items().contains(item -> core != null && core.items.get(item) > 0 && usedItems.add(item))){
                rebuild();
            }
        });

        int i = 0;

        for(Item item : content.items()){
            if(usedItems.contains(item)){
                image(item.uiIcon).size(iconSmall).padRight(3).tooltip(t -> t.background(Styles.black6).margin(4f).add(item.localizedName).style(Styles.outlineLabel));
                //TODO leaks garbage
                label(() -> {
                    String result;
                    if(core != null){
                        // calc new average
                        float newAvg = (lastItemsAveraged.get(item) * (maxAverage - 1) + core.items.get(item) - lastItems.get(item)) / maxAverage;
                        lastItemsAveraged.put(item, newAvg);
                        // set the text
                        result = (newAvg > 0 ? "[green]+" : "[red]-") + UI.formatAmount(core.items.get(item)) + "[]";
                        // set the last amounts for next update
                        if(lastItems.has(item)){
                            lastItems.set(item, core.items.get(item));
                        }else{
                            lastItems.add(item, core.items.get(item));
                        }
                    }else{
                        result = "0";
                    }
                    return result;
                }).padRight(3).minWidth(52f).left();

                if(++i % 4 == 0){
                    row();
                }
            }
        }
    }
}
