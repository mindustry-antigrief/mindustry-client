package mindustry.world.blocks;

import arc.func.*;
import arc.math.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.ctype.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.*;

import static mindustry.Vars.*;

public class ItemSelection{
    private static TextField search;
    private static int rowCount;

    public static <T extends UnlockableContent> void buildTable(Table table, Seq<T> items, Prov<T> holder, Cons<T> consumer){
        buildTable(table, items, holder, consumer, true);
    }

    public static <T extends UnlockableContent> void buildTable(Table table, Seq<T> items, Prov<T> holder, Cons<T> consumer, boolean closeSelect){
        buildTable(null, table, items, holder, consumer, closeSelect, 5, 4);
    }

    public static <T extends UnlockableContent> void buildTable(Table table, Seq<T> items, Prov<T> holder, Cons<T> consumer, int columns){
        buildTable(null, table, items, holder, consumer, true, 5, columns);
    }

    public static <T extends UnlockableContent> void buildTable(Block block, Table table, Seq<T> items, Prov<T> holder, Cons<T> consumer){
        buildTable(block, table, items, holder, consumer, true, 5, 4);
    }

    public static <T extends UnlockableContent> void buildTable(Block block, Table table, Seq<T> items, Prov<T> holder, Cons<T> consumer, boolean closeSelect){
        buildTable(block, table, items, holder, consumer, closeSelect, 5 ,4);
    }

    public static <T extends UnlockableContent> void buildTable(Block block, Table table, Seq<T> items, Prov<T> holder, Cons<T> consumer, int rows, int columns){
        buildTable(block, table, items, holder, consumer, true, rows, columns);
    }

    public static <T extends UnlockableContent> void buildTable(Table table, Seq<T> items, Prov<T> holder, Cons<T> consumer, int rows, int columns){
        buildTable(null, table, items, holder, consumer, true, rows, columns);
    }

    public static <T extends UnlockableContent> void buildTable(@Nullable Block block, Table table, Seq<T> items, Prov<T> holder, Cons<T> consumer, boolean closeSelect, int rows, int columns){
        ButtonGroup<ImageButton> group = new ButtonGroup<>();
        group.setMinCheckCount(0);
        Table cont = new Table();
        cont.defaults().size(40);

        if(search != null) search.clearText();

        Runnable rebuild = () -> {
            group.clear();
            cont.clearChildren();

            var text = search != null ? search.getText().toLowerCase() : null;
            var blank = text == null || text.trim().isEmpty();
            int i = 0;
            rowCount = 0;

            for(T item : items){
                if(!blank && !item.localizedName.toLowerCase().contains(text)) continue;
                if(!item.unlockedNow() || (item instanceof Item checkVisible && state.rules.hiddenBuildItems.contains(checkVisible)) || item.isHidden()) continue;

                ImageButton button = cont.button(Tex.whiteui, Styles.clearTogglei, Mathf.clamp(item.selectionSize, 0f, 40f), () -> {
                    if(closeSelect) control.input.config.hideConfig();
                }).group(group).tooltip(item.localizedName).get();
                button.changed(() -> consumer.get(button.isChecked() ? item : null));
                button.getStyle().imageUp = new TextureRegionDrawable(item.uiIcon);
                button.update(() -> button.setChecked(holder.get() == item));

                if(++i % columns + 1 == 0){
                    cont.row();
                    rowCount++;
                }
            }

            //add extra blank spaces so it looks nice
            if(i % columns != 0){
                int remaining = columns - (i % columns);
                for(int j = 0; j < remaining; j++){
                    cont.image(Styles.none);
                }
            }
        };

        rebuild.run();

        Table main = new Table().background(Styles.black6);
        if(rowCount > rows * 1.5f){
            search = main.field(null, text -> rebuild.run()).width(40 * columns).padBottom(4).left().growX().get();
            search.setMessageText("@players.search");
            main.row();
        }

        ScrollPane pane = new ScrollPane(cont, Styles.smallPane);
        pane.setScrollingDisabled(true, false);

        if(block != null){
            pane.setScrollYForce(block.selectScroll);
            pane.update(() -> {
                block.selectScroll = pane.getScrollY();
            });
        }

        pane.setOverscroll(false, false);
        main.add(pane).maxHeight(40 * rows);
        table.add(main);
    }
}