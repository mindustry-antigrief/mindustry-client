package mindustry.client.antigreif;

import mindustry.gen.*;
import mindustry.world.*;

public class PlaceTileLog extends TileLogItem {
    public Block block;
    /**
     * Creates a TileLogItem.  time is unix time.
     */
    public PlaceTileLog(Unitc player, Tile tile, long time, String additionalInfo, Block block){
        super(player, tile, time, additionalInfo);
        this.block = block;
    }

    @Override
    protected String formatDate(String date, long minutes) {
        return String.format("%s placed %s at %s UTC (%d minutes ago).  %s", player, block.name, date, minutes, additionalInfo);
    }
}
