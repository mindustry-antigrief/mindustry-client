package mindustry.ui.dialogs;

import arc.scene.ui.*;
import arc.scene.ui.layout.*;

public class FeaturesDialog extends FloatingDialog{

    public FeaturesDialog(){
        super("Features");
        shown(this::setup);
        onResize(this::setup);
    }

    private void setup(){
        cont.clear();
        buttons.clear();

        Table t = new Table();

        label(t, "Zoom cap changed");
        label(t, "Lighting toggleable in settings");
        label(t, "Automatic block sequence placement");
        label(t, "Health bar");
        label(t, "Free camera movement (arrow keys)");
        label(t, "Build assist (in tab menu)");
        label(t, "Build denial (in tab menu)");
        label(t, "Undo (ctrl+z and in tab menu)");
        label(t, "Path following (top menu in-game)");
        label(t, "Flying unit transparency (settings)");
        label(t, "Auto move to building plans");
        label(t, "Auto mine");
        label(t, "Smart fixpower (knows about plast walls)");
        label(t, "Turret target indicator");
        label(t, "Ctrl+f to find block");
        label(t, "!here and !go");
        label(t, "Intelligent navigation");
        label(t, "Core item display");
        label(t, "Enemy turret ranges visible");
        label(t, "Limited command autocomplete");
        label(t, "Ctrl+shift+click to view tile logs");
        label(t, "Auto pickup/dump items (next to schematics button)");
        label(t, "Z to navigate player to center of viewport");

        cont.add(new ScrollPane(t)).growX();

        addCloseButton();
    }

    private void label(Table t, String text){
        t.add(new Label(text));
        t.row();
    }
    /**
     * # Feature list:
     *  * Zoom cap changed
     *  * Lighting toggleable in settings
     *  * Automatic block sequence placement
     *  * Health bar
     *  * Free camera movement with arrow keys
     *  * Build assist
     *  * Build denial
     *  * Undo (ctrl+z and in tab menu)
     *  * Path following
     *  * Flying unit transparency (settings)
     *  * Auto move to build plans
     *  * Auto mine
     *  * Smart fixpower equivalent (knows about plast walls)
     *  * Turret target indicator
     *  * Ctrl+f to find block type
     *  * !here and !go
     *  * Intelligent navigation
     *  * Core item display
     *  * Enemy turret ranges visible
     *  * Limited command autocomplete
     *  * Ctrl+shift+click to view tile logs
     *  * Auto pickup/dump items
     */
}
