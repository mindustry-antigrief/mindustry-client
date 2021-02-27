package mindustry.client.antigrief;

import arc.scene.*;
import arc.scene.ui.layout.*;
import mindustry.gen.*;
import mindustry.world.*;

import static mindustry.Vars.*;

public class ConfigTileLog extends TileLogItem {
    Object configuration;
    Object oldConfiguration;

    /**
     * Creates a TileLogItem.  time is unix time.
     */
    public ConfigTileLog(Unitc player, Tile tile, Object newConfig, Object previous, long time, String additionalInfo){
        super(player, tile, time, additionalInfo);
        configuration = newConfig;
        oldConfiguration = previous;
    }

    @Override
    public Element toElement() {
        Table t = new Table();
        t.add(super.toElement());
        t.button(Icon.refresh1, () -> {
            try {
                System.out.println(configuration);
                world.tile(x, y).build.configure(configuration);
            } catch(Exception e) {
                ui.showErrorMessage("Failed to rollback configuration");
                e.printStackTrace();
            }
        }).tooltip("Restore this configuration");
        return t;
    }
}
