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

        String color1 = "[lightgray]";
        String color2 = "[gray]";
        String titleCol = "[gold]";
        label(t, titleCol+"Misc");
        label(t, color1+"Zoom effectively uncapped");
        label(t, color2+"Health bar");
        label(t, color1+"Basic name/command autocompletion");
        label(t, color2+"View enemy turret range when hovered");
        label(t, color1+"Core item display, displays core item counts and net gain/loss");
        label(t, color2+"Turret target indicator (sometimes it can be a little slow to update)");
        label(t, color1+"Autopickup/dump (buttons on bottom right next to schematic menu)");

        label(t, titleCol+"\nAdditional Game Settings");
        label(t, color2+"Toggle Ambient Lighting (Graphics)");
        label(t, color1+"Flying unit transparency/invisibility (Graphics)");
        label(t, color2+"Toggle !here for other users, allows others to post their coordinates easily (Game)");
        
        label(t, titleCol+"\nTab Menu Additions (use on self to stop)");
        label(t, color1+"Build assist (copy button)");
        label(t, color2+"Build blocking (shield button)");
        label(t, color1+"Watch player (magnifier button)");
        label(t, color2+"Mass undo (undo/share button)");

        label(t, titleCol+"\nTop GUI (left to right)");
        label(t, color1+"Create new path (records item pickups)");
        label(t, color2+"Add waypoint to path");
        label(t, color1+"Follow path (follows set waypoints)");
        label(t, color2+"Stop placing/following path");
        label(t, color1+"Delete path");
        label(t, color2+"Fixpower (doesnt connect through plast walls, [#dd5656]EMERGENCY ONLY"+color2+")");
        label(t, color1+"Start pathfinding path (no waypoints)");
        label(t, color2+"End pathfinding path (AI will avoid enemy turrets if possible)");

        label(t, titleCol+"\nHotkeys and Commands");
        label(t, color1+"ARROW KEYS - Freecam ");
        label(t, color2+"R - Stop freecam (locks camera to player)");
        label(t, color1+"N - Move to camera (only works with freecam)");
        label(t, color2+"Z - Path to camera (^ but uses AI to avoid turrets)");
        label(t, color1+"B - Automatically go to unbuilt buildings");
        label(t, color2+"; (SEMICOLON) - Automine/draug, mines and stores needed resources");
        label(t, color1+"CTRL + F - Find block by ID, ENTER to select, !go to go");
        label(t, color2+"CTRL + Z - Undo / Redo last block place/break");
        label(t, color1+"SHIFT + CLICK/RIGHTCLICK - Prioritize building/breaking specified block");
        label(t, color2+"CTRL + CLICK - Block history (records places, breaks and configures)");
        label(t, color1+"!go command - input coordinates or player name");
        label(t, color2+"!here command - posts your coordinates in chat");
        
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
