package mindustry.client.antigrief;

import mindustry.gen.Nulls;
import mindustry.gen.Unitc;
import mindustry.world.Block;
import mindustry.world.Tile;

public class DestroyTileLog extends TileLogItem {
    public final Block block;
    /**
     * Creates a TileLogItem.  time is unix time.
     */
    public DestroyTileLog(Tile tile, long time, String additionalInfo, Block block) {
        super(Nulls.unit, tile, time, additionalInfo);
        this.block = block;
    }

    @Override
    protected String formatDate(String date, long minutes) {
        return String.format("%s destroyed at %s UTC (%d minutes ago).  %s", block.localizedName, date, minutes, additionalInfo);
    }

    @Override
    protected String formatConcise(String date, long minutes) {
        return String.format("%s destroyed (%dm)", block.localizedName, minutes);
    }
}
