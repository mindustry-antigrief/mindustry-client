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

        label(t, "Misc");
        label(t, "Zoom effectively uncapped");
        label(t, "Health bar");
        label(t, "Basic name/command autocompletion");
        label(t, "View enemy turret range when hovered");
        label(t, "Low item alert, any item with less than 50 in core is displayed on right");
        label(t, "Turret target indicator (sometimes it can be a little slow to update)");
        label(t, "Autopickup/dump (buttons on bottom right next to schematic menu)");

        label(t, "\nAdditional Game Settings");
        label(t, "Toggle Ambient Lighting (Graphics)");
        label(t, "Flying unit transparency/invisibility (Graphics)");
        
        label(t, "\nTab Menu Additions (use on self to stop)");
        label(t, "Build assist (copy button)");
        label(t, "Build blocking (shield button)");
        label(t, "Watch player (magnifier button)");
        label(t, "Mass undo (undo/share button)");

        label(t, "\nTop GUI (left to right)");
        label(t, "Create new path (records item pickups)");
        label(t, "Add waypoint to path");
        label(t, "Follow path (follows set waypoints)");
        label(t, "Stop placing/following path");
        label(t, "Delete path");
        label(t, "Fixpower (doesnt connect through plast walls, EMERGENCY ONLY)");
        label(t, "Start pathfinding path (no waypoints)");
        label(t, "End pathfinding path (AI will avoid enemy turrets if possible)");

        label(t, "\nHotkeys and Commands");
        label(t, "ARROW KEYS - Freecam ");
        label(t, "R - Stop freecam (locks camera to player)");
        label(t, "N - Move to camera (only works with freecam)");
        label(t, "Z - Path to camera (^ but uses AI to avoid turrets)");
        label(t, "B - Automatically go to unbuilt buildings");
        label(t, "; (SEMICOLON) - Automine/draug, mines and stores needed resources");
        label(t, "CTRL + F - Find block by ID, ENTER to select, !go to go");
        label(t, "CTRL + Z - Undo / Redo last block place/break.");
        label(t, "SHIFT + CLICK/RIGHTCLICK - Prioritize building/breaking specified block");
        label(t, "CTRL + CLICK - Block history (records places, breaks and configures)");
        label(t, "!go command: input coordinates or player name");
        label(t, "!here command: posts your coordinates in chat");
        
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
