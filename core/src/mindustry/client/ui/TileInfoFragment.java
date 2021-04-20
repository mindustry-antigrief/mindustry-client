package mindustry.client.ui;

import arc.*;
import arc.graphics.g2d.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import mindustry.*;
import mindustry.client.antigrief.*;
import mindustry.gen.*;
import mindustry.ui.*;
import mindustry.world.*;

public class TileInfoFragment extends Table {

    public TileInfoFragment() {
        NinePatchDrawable background = new NinePatchDrawable(Tex.buttonTransTop);

        setBackground(background);
        Image img = new Image();
        add(new Padding(5f, 1f));
        add(img);
        Table table = new Table();
        Label label = new Label("");
        table.add(label);
        add(new Padding(5f, 1f));
        add(table);
        visible(() -> Core.settings.getBool("tilehud"));
        update(() -> {
            Tile hovered = Vars.control.input.cursorTile();
            if (hovered == null) {
                img.setDrawable(Icon.none);
                label.setText("");
                return;
            } else if (hovered.block() == null) {
                img.setDrawable(hovered.floor().icon(Cicon.xlarge));
                label.setText("");
                return;
            }

            TextureRegion icon = hovered.block().icon(Cicon.xlarge);
            img.setDrawable(icon.found()? icon : hovered.floor().icon(Cicon.xlarge));
            TileLog log = hovered.getLog();
            Seq<TileLogItem> logItems = new Seq<>(log.log);
            label.setText("");
            logItems.reverse();
            logItems.truncate(Math.min(3, logItems.size));
            logItems.reverse();
            for (TileLogItem item : logItems) {
                label.setText(label.getText() + item.formatShort() + "\n");
            }
        });
    }
}
