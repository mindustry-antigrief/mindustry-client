package mindustry.client.antigrief;

import mindustry.gen.*;
import mindustry.world.*;

public class PayloadDropOffTileLog extends TileLogItem {
    private final Block block;
    /**
     * Creates a TileLogItem.  time is unix time.
     */
    public PayloadDropOffTileLog(Unitc player, Tile tile, Block block, long time, String additionalInfo){
        super(player, tile, time, additionalInfo);
        this.block = block;
    }

    @Override
    protected String formatDate(String date, long minutes) {
        return String.format("%s put down %s at %s UTC (%d minutes ago).  %s", player, block.localizedName, date, minutes, additionalInfo);
    }

    @Override
    protected String formatConcise(String date, long minutes) {
        return String.format("%s dropped %s (%dm)", player, block.localizedName, minutes);
    }
}
