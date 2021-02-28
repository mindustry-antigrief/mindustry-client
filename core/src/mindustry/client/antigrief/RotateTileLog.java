package mindustry.client.antigrief;

import mindustry.gen.Unitc;
import mindustry.world.Tile;

public class RotateTileLog extends TileLogItem {
    int newRotation;
    int oldRotation;

    /**
     * Creates a TileLogItem.  time is unix time.
     */
    public RotateTileLog(Unitc player, Tile tile, int oldRotation, int newRotation, long time, String additionalInfo){
        super(player, tile, time, additionalInfo, "rotated", tile.block());
        this.newRotation = newRotation;
        this.oldRotation = oldRotation;
    }

    @Override
    protected String formatDate(String date, long minutes) {
        return String.format("%s rotated tile from %s to %s at %s UTC (%d minutes ago).  %s", player, toCardinalDirection(oldRotation), toCardinalDirection(newRotation), date, minutes, additionalInfo);
    }

    @Override
    protected String formatConcise(String date, long minutes) {
        return String.format("%s rotated tile (%dm)", player, minutes);
    }
}
