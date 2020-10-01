package mindustry.client.antigreif;

import mindustry.gen.*;
import mindustry.world.*;

public class ConfigTileLog extends TileLogItem {

    /**
     * Creates a TileLogItem.  time is unix time.
     */
    public ConfigTileLog(Unitc player, Tile tile, long time, String additionalInfo){
        super(player, tile, time, additionalInfo);
    }
}
