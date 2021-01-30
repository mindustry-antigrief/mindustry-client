package mindustry.client.antigrief;

import arc.scene.Element;
import arc.scene.ui.layout.Table;
import mindustry.gen.Unitc;
import mindustry.world.Tile;

public class RotateTileLog extends TileLogItem {
    int newRotation;
    int oldRotation;

    /**
     * Creates a TileLogItem.  time is unix time.
     */
    public RotateTileLog(Unitc player, Tile tile, int oldRotation, int newRotation, long time, String additionalInfo){
        super(player, tile, time, additionalInfo);
        this.newRotation = newRotation;
        this.oldRotation = oldRotation;
    }

    @Override
    public Element toElement() {
        Table t = new Table();
        t.add(super.toElement());
        return t;
    }

    private String toCardinalDirection(int rotation) {
        return switch (rotation) {
            case (0) -> "east";
            case (1) -> "north";
            case (2) -> "west";
            case (3) -> "south";
            default -> String.valueOf(rotation);
        };
    }

    @Override
    protected String formatDate(String date, long minutes) {
        return String.format("%s rotated tile from %s to %s at %s UTC (%d minutes ago).  %s", player, toCardinalDirection(oldRotation), toCardinalDirection(newRotation), date, minutes, additionalInfo);
    }

    @Override
    protected String formatConcise(String date, String minutes) {
        return String.format("%s rotated tile (%s)", player, minutes);
    }
}
