package mindustry.client.ui;

import arc.*;
import arc.graphics.g2d.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import mindustry.*;
import mindustry.client.antigrief.*;
import mindustry.gen.*;
import mindustry.ui.*;
import mindustry.world.*;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

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
        AtomicInteger lastPos = new AtomicInteger();
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
            } else if (hovered.pos() == lastPos.get()) {
                return;
            }
            lastPos.set(hovered.pos());

            TextureRegion icon = hovered.block().icon(Cicon.xlarge);
            img.setDrawable(icon.found()? icon : hovered.floor().icon(Cicon.xlarge));
            var record = TileRecords.INSTANCE.get(hovered);
            if (record == null) return;
            var logs = record.lastLogs(3);

            var builder = new StringBuilder();
            for (var item : logs) {
                builder.append(item.toShortString()).append("\n");
            }
            label.setText(builder.toString());
        });
    }
}
