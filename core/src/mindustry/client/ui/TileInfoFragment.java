package mindustry.client.ui;

import arc.Core;
import arc.graphics.g2d.TextureRegion;
import arc.scene.style.NinePatchDrawable;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.client.Client;
import mindustry.client.antigrief.TileLog;
import mindustry.client.antigrief.TileLogItem;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.ui.Cicon;
import mindustry.world.Tile;

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
            if (!visible) {
                return;
            }
            if (Vars.world == null) return;
            int x = Vars.control.input.rawTileX();
            int y = Vars.control.input.rawTileY();
            Tile hovered = Vars.world.tile(x, y);
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
            TileLog log = Client.getLog(x, y);
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
