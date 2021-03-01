package mindustry.client.antigrief;

import arc.scene.Element;
import arc.scene.ui.layout.Table;
import mindustry.gen.*;
import mindustry.world.*;

import static mindustry.Vars.*;

public class PlaceTileLog extends TileLogItem {
    Block block;
    Object config;

    /**
     * Creates a TileLogItem.  time is unix time.
     */
    public PlaceTileLog(Unitc player, Tile tile, long time, String additionalInfo, Block block, Object config){
        super(player, tile, time, additionalInfo, "placed", block);
        this.block = block;
        this.config = config;
    }

    @Override
    public Element toElement() {
        Table t = new Table();
        t.add(super.toElement());
        t.button(Icon.refresh, () -> {
            try {
                world.tile(x, y).build.configure(config);
            } catch(Exception e) {
                ui.showErrorMessage("Failed to rollback configuration");
            }
        }).tooltip("Restore this configuration");
        return t;
    }

    @Override
    protected String formatDate(String date, long minutes) {
        return String.format("%s placed %s at %s UTC (%d minutes ago).  %s", player, block.localizedName, date, minutes, additionalInfo);
    }

    @Override
    protected String formatConcise(String date, long minutes) {
        return String.format("%s placed %s (%dm)", player, block.localizedName, minutes);
    }
}
