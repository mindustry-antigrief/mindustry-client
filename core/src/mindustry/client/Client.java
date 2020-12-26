package mindustry.client;

import arc.*;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.struct.Queue;
import arc.struct.Seq;
import arc.util.*;
import mindustry.Vars;
import mindustry.client.antigreif.*;
import mindustry.client.navigation.*;
import mindustry.client.ui.Toast;
import mindustry.client.ui.UnitPicker;
import mindustry.client.utils.Autocomplete;
import mindustry.client.utils.Pair;
import mindustry.core.World;
import mindustry.entities.Units;
import mindustry.game.EventType;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.input.DesktopInput;
import mindustry.world.Tile;
import mindustry.world.blocks.defense.turrets.BaseTurret;
import mindustry.type.UnitType;

import static mindustry.Vars.*;
import static mindustry.Vars.player;

public class Client {
    private static TileLog[][] tileLogs;
    //todo: use this instead of Navigation.isFollowing and such
    public static ClientMode mode = ClientMode.normal;
    public static Queue<ConfigRequest> configs = new Queue<>();
    public static boolean showingTurrets = false;
    public static Seq<BaseTurret.BaseTurretBuild> turrets = new Seq<>();
    public static long lastSyncTime = 0L;
    public static final CommandHandler fooCommands = new CommandHandler("!");
    public static boolean hideTrails = true;
    public static Ratekeeper configRateLimit = new Ratekeeper();
    public static boolean hideUnits = false;

    public static void initialize() {
        fooCommands.<Player>register("help", "[page]", "Lists all client commands.", (args, player) -> {
            if(args.length > 0 && !Strings.canParseInt(args[0])){
                player.sendMessage("[scarlet]'page' must be a number.");
                return;
            }
            int commandsPerPage = 6;
            int page = args.length > 0 ? Strings.parseInt(args[0]) : 1;
            int pages = Mathf.ceil((float)fooCommands.getCommandList().size / commandsPerPage);

            page --;

            if(page >= pages || page < 0){
                player.sendMessage("[scarlet]'page' must be a number between[orange] 1[] and[orange] " + pages + "[scarlet].");
                return;
            }

            StringBuilder result = new StringBuilder();
            result.append(Strings.format("[orange]-- Client Commands Page[lightgray] @[gray]/[lightgray]@[orange] --\n\n", (page+1), pages));

            for(int i = commandsPerPage * page; i < Math.min(commandsPerPage * (page + 1), fooCommands.getCommandList().size); i++){
                CommandHandler.Command command = fooCommands.getCommandList().get(i);
                result.append("[orange] !").append(command.text).append("[white] ").append(command.paramText).append("[lightgray] - ").append(command.description).append("\n");
            }
            player.sendMessage(result.toString());
        });

        fooCommands.<Player>register("unit", "<unit-name>", "Swap to specified unit", (args, player) -> {
            Seq<UnitType> sorted = content.units().copy();
            sorted = sorted.sort((b) -> Strings.levenshtein(args[0], b.name));
            UnitType found = sorted.first();
            new UnitPicker().findUnit(found);
        });

        fooCommands.<Player>register("goto","<x> <y>", "Navigates to (x, y)", (args, player) -> {
            try {
                Navigation.navigateTo(Float.parseFloat(args[0])*8, Float.parseFloat(args[1])*8);
            }
            catch(Exception e){
                player.sendMessage("[scarlet]Invalid coordinates, format is <x> <y> Eg: !goto 10 300");
            }
        });

        fooCommands.<Player>register("lookat","<x> <y>", "Moves camera to (x, y)", (args, player) -> {
            try {
                DesktopInput.panning = true;
                Spectate.pos = new Vec2(Float.parseFloat(args[0])*8, Float.parseFloat(args[1])*8);
            }
            catch(Exception e){
                player.sendMessage("[scarlet]Invalid coordinates, format is <x> <y> Eg: !lookat 10 300");
            }
        });

        fooCommands.<Player>register("here", "[message...]", "Prints your location to chat with an optional message", (args, player) ->
                Call.sendChatMessage(String.format("%s(%s, %s)",args.length == 0 ? "" : args[0] + " ", player.tileX(), player.tileY()))
        );

        fooCommands.<Player>register("cursor", "[message...]", "Prints cursor location to chat with an optional message", (args, player) ->
                Call.sendChatMessage(String.format("%s(%s, %s)",args.length == 0 ? "" : args[0] + " ", World.toTile(player.mouseX), World.toTile(player.mouseY)))
        );

        fooCommands.<Player>register("builder", "[options...]", "Starts auto build with optional arguments, prioritized from first to last.", (args, player) ->
                Navigation.follow(new BuildPath(args))
        );

        fooCommands.<Player>register("tp", "<x> <y>", "Moves to (x, y) at insane speeds, only works on servers without strict mode enabled.", (args, player) -> {
            try {
                Timer.schedule(() -> player.unit().moveAt(new Vec2().set(World.unconv(Float.parseFloat(args[0])), World.unconv(Float.parseFloat(args[1]))).sub(player.unit()), player.dst(World.unconv(Float.parseFloat(args[0])), World.unconv(Float.parseFloat(args[1])))), 0, .01f, 15);
            }
            catch(Exception e){
                player.sendMessage("[scarlet]Invalid coordinates, format is <x> <y> Eg: !tp 10 300");
            }
        });

        fooCommands.<Player>register("", "[message...]", "Does nothing", (args, player) ->
            Call.sendChatMessage("!" + (args.length == 1 ? args[0] : ""))
        );


        Events.on(WorldLoadEvent.class, event -> {
            if (Time.timeSinceMillis(lastSyncTime) > 5000) {
                tileLogs = new TileLog[world.height()][world.width()];
            }
            PowerInfo.initialize();
            Navigation.stopFollowing();
            configs.clear();
            turrets.clear();
            UnitPicker.found = null;
            if (state.rules.pvp) ui.chatfrag.addMessage("[scarlet]Don't use a client in pvp, it's uncool!", "Your Conscience", Color.crimson);
        });

        Events.on(EventType.UnitChangeEvent.class, event -> {
            UnitType unit = UnitPicker.found;
            if (event.unit.team == player.team() && !(event.player == player)) {
                Unit find = Units.closest(player.team(), player.x, player.y, u -> !u.isPlayer() && u.type == unit && !u.dead);
                if (find != null) {
                    Call.unitControl(player, find);
                    Timer.schedule(() -> {
                        if (event.unit.isPlayer()) {
                            Toast t = new Toast(2);
                            if (player.unit() == find) { UnitPicker.found = null; t.add("Successfully switched units.");} // After we switch units successfully, stop listening for this unit
                            else if (find.getPlayer() != null) { t.add("Failed to become " + unit + ", " + find.getPlayer().name + " is already controlling it (likely using unit sniper).");} // TODO: make these responses a method in UnitPicker
                        }
                        }, net.client() ? netClient.getPing()/1000f+.3f : .025f);
                }
            }
        });

        Events.on(EventType.UnitCreateEvent.class, event -> {
            UnitType unit = UnitPicker.found;
            if (!event.unit.dead && event.unit.type == unit && event.unit.team == player.team() && !event.unit.isPlayer()) {
                Call.unitControl(player, event.unit);
                Timer.schedule(() -> {
                    if (event.unit.isPlayer()) {
                        Toast t = new Toast(2);
                        if (player.unit() == event.unit) { UnitPicker.found = null; t.add("Successfully switched units.");}  // After we switch units successfully, stop listening for this unit
                        else if (event.unit.getPlayer() != null) { t.add("Failed to become " + unit + ", " + event.unit.getPlayer().name + " is already controlling it (likely using unit sniper).");}
                    }
                    }, net.client() ? netClient.getPing()/1000f+.3f : .025f);
            }
        });
        Events.on(EventType.ClientLoadEvent.class, event -> {
            Autocomplete.initialize();
        });
    }


    public static void update() {
        Navigation.update();
        PowerInfo.update();
        Spectate.update();

        hideTrails = Core.settings.getBool("hidetrails");

        if (!configs.isEmpty()) {
                try {
                    if (configRateLimit.allow(6 * 1000, 25)) {
                        ConfigRequest req = configs.last();
                        Tile tile = world.tile(req.x, req.y);
                        if (tile != null) {
//                            Object initial = tile.build.config();
                            req.run();
                            configs.remove(req);
                            Timer.schedule(() -> {
                                // if(tile.build != null && tile.build.config() == initial) configs.addLast(req); TODO: This can also cause loops
                                // if(tile.build != null && req.value != tile.build.config()) configs.addLast(req); TODO: This infinite loops if u config something twice, find a better way to do this
                            }, net.client() ? netClient.getPing()/1000f+.05f : .025f);
                        }
                    }
                } catch (Exception e) {Log.info(e.getMessage());}
        }
    }

    public static TileLog getLog(int x, int y) {
        if (tileLogs == null) tileLogs = new TileLog[world.height()][world.width()];
        if (tileLogs[y][x] == null) {
            tileLogs[y][x] = new TileLog(world.tile(x, y));
        }
        return tileLogs[y][x];
    }
}