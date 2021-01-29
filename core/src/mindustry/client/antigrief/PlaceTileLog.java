package mindustry.client.antigrief;

import arc.scene.Element;
import arc.scene.ui.ImageButton;
import arc.scene.ui.layout.Table;
import mindustry.gen.*;
import mindustry.world.*;

import static mindustry.Vars.ui;
import static mindustry.Vars.world;

public class PlaceTileLog extends TileLogItem {
    public Block block;
    public Object config;
    /**
     * Creates a TileLogItem.  time is unix time.
     */
    public PlaceTileLog(Unitc player, Tile tile, long time, String additionalInfo, Block block, Object config){
        super(player, tile, time, additionalInfo);
        this.block = block;
        this.config = config;
    }

    @Override
    public Element toElement() {
        Table t = new Table();
        t.add(super.toElement());
        ImageButton button = new ImageButton(Icon.undo);
        button.clicked(() -> {
            try {
                world.tile(x, y).build.configure(config);
            } catch(Exception e) {
                ui.showErrorMessage("Failed to rollback configuration");
            }
        });
        t.add(button);
        return t;
    }

    @Override
    protected String formatDate(String date, long minutes) {
        return String.format("%s placed %s at %s UTC (%d minutes ago).  %s", player, block.localizedName, date, minutes, additionalInfo);
    }

    @Override
    protected String formatConcise(String date, String minutes) {
        return String.format("%s placed %s (%s)", player, block.localizedName, minutes);
    }
}
