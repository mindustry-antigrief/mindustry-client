package mindustry.client.ui;

import arc.*;
import arc.graphics.g2d.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import mindustry.*;
import mindustry.client.antigrief.*;
import mindustry.core.*;
import mindustry.gen.*;
import mindustry.world.*;

import java.util.concurrent.atomic.*;

public class TileInfoFragment extends Table {

    public TileInfoFragment() {

        setBackground(Tex.wavepane);
        marginRight(6);
        Image img = new Image();
        add(img).size(63).padRight(6);
        Label label = new Label("");
        add(label).height(126);
        visible(() -> Core.settings.getBool("tilehud"));
        AtomicInteger lastPos = new AtomicInteger();
        var builder = new StringBuilder();
        update(() -> {
            Tile hovered = Vars.control.input.cursorTile();
            if (hovered == null) {
                img.setDrawable(Icon.none);
                label.setText("");
                return;
            } else if (hovered.block() == null) {
                img.setDrawable(hovered.floor().fullIcon);
                label.setText("");
                return;
            } else if (hovered.pos() == lastPos.get()) {
                return;
            }
            lastPos.set(hovered.pos());

            TextureRegion icon = hovered.block().fullIcon;
            img.setDrawable(icon.found() ? icon : hovered.floor().fullIcon);
            var record = TileRecords.INSTANCE.get(hovered);
            if (record == null) return;
            var logs = record.lastLogs(7);

            builder.setLength(0);
            for (var item : logs) {
                builder.append(item.toShortString()).append(" (").append(UI.formatMinutesFromMillis(Time.timeSinceMillis(item.getTime().toEpochMilli()))).append(")\n");
            }
            label.setText(builder.length() == 0 ? "" : builder.substring(0, builder.length() - 1));
        });
    }
}
