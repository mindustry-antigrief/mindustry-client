package mindustry.client.antigreif;

import arc.scene.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import mindustry.gen.*;
import mindustry.world.*;

import static mindustry.Vars.*;

public class ConfigTileLog extends TileLogItem {
    Object configuration = null;

    /**
     * Creates a TileLogItem.  time is unix time.
     */
    public ConfigTileLog(Unitc player, Tile tile, Object value, long time, String additionalInfo){
        super(player, tile, time, additionalInfo);
        configuration = value;
    }

    @Override
    public Element toElement() {
        Table t = new Table();
        t.add(super.toElement());
        TextButton button = new TextButton("Rollback config to here");
        button.clicked(() -> {
            try {
                world.tile(x, y).build.configure(configuration);
            } catch(Exception e) {
                ui.showErrorMessage("Failed to rollback configuration");
            }
        });
        t.add(button).width(400f);
        return t;
    }
}
