package mindustry.ui.dialogs;

import arc.*;
import arc.graphics.g2d.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import mindustry.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.maps.*;
import mindustry.ui.*;

public class CustomGameDialog extends BaseDialog{
    private MapPlayDialog dialog = new MapPlayDialog();
    private TextField searchField;
    private Table maps = new Table().marginBottom(55f);

    public CustomGameDialog(){
        super("@customgame");
        addCloseButton();
        shown(this::setup);
        onResize(this::setup);
    }

    void setup(){
        clearChildren();
        cont.clearChildren();
        add(titleTable).growX().row();
        stack(cont, buttons).grow();
        buttons.bottom();

        cont.table(s -> {
            s.left();
            s.image(Icon.zoom);
            searchField = s.field(null, text -> build()).growX().get();
        }).fillX().padBottom(4).row();
        Time.runTask(2f, () -> {
            Core.scene.setKeyboardFocus(searchField);
            build();
        });

        ScrollPane pane = new ScrollPane(maps);
        pane.setFadeScrollBars(false);
        pane.setScrollingDisabled(true, false);

        cont.add(pane);
    }

    void build() {
        maps.defaults().width(170).fillY().top().pad(4f);
        int maxwidth = Math.max((int)(Core.graphics.getWidth() / Scl.scl(210)), 1);
        float images = 146f;

        int i = 0;

        maps.clearChildren();
        for(Map map : Vars.maps.all()){
            if(searchField.getText().length() > 0 && !map.name().toLowerCase().contains(searchField.getText().toLowerCase())) continue;

            if(i % maxwidth == 0){
                maps.row();
            }

            ImageButton image = new ImageButton(new TextureRegion(map.safeTexture()), Styles.cleari);
            image.margin(5);
            image.top();

            Image img = image.getImage();
            img.remove();

            image.row();
            image.table(t -> {
                t.left();
                for(Gamemode mode : Gamemode.all){
                    TextureRegionDrawable icon = Vars.ui.getIcon("mode" + Strings.capitalize(mode.name()) + "Small");
                    if(mode.valid(map) && Core.atlas.isFound(icon.getRegion())){
                        t.image(icon).size(16f).pad(4f);
                    }
                }
            }).left();
            image.row();
            image.add(map.name()).pad(1f).growX().wrap().left().get().setEllipsis(true);
            image.row();
            image.image(Tex.whiteui, Pal.gray).growX().pad(3).height(4f);
            image.row();
            image.add(img).size(images);

            BorderImage border = new BorderImage(map.safeTexture(), 3f);
            border.setScaling(Scaling.fit);
            image.replaceImage(border);

            image.clicked(() -> dialog.show(map));

            maps.add(image);

            i++;
        }

        if(Vars.maps.all().size == 0){
            maps.add("@maps.none").pad(50);
        }
    }
}