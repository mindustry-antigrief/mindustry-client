package mindustry.client;

import arc.*;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.struct.IntSet;
import arc.struct.Queue;
import arc.struct.Seq;
import arc.util.*;
import mindustry.client.antigrief.*;
import mindustry.client.navigation.*;
import mindustry.client.ui.Toast;
import mindustry.client.ui.UnitPicker;
import mindustry.client.utils.*;
import mindustry.client.utils.Autocomplete;
import mindustry.content.Blocks;
import mindustry.core.NetClient;
import mindustry.core.World;
import mindustry.entities.Units;
import mindustry.game.EventType;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.input.DesktopInput;
import mindustry.world.Tile;
import mindustry.world.blocks.defense.turrets.BaseTurret;
import mindustry.type.UnitType;
import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static arc.Core.settings;
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
    public static boolean hideTrails;
    public static Ratekeeper configRateLimit = new Ratekeeper();
    public static boolean hideUnits = false;
    /** The last position someone sent in chat or was otherwise put into the buffer. */
    public static final Vec2 lastSentPos = new Vec2();
    public static IntSet messageBlockPositions = new IntSet();
    public static final String messageCommunicationPrefix = "IN USE FOR CHAT AUTHENTICATION, do not use";
    public static ClientInterface mapping;
    public static final byte FOO_USER = (byte) 0b10101010;
    public static final byte ASSISTING = (byte) 0b01010101;

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

        fooCommands.<Player>register("go","[x] [y]", "Navigates to (x, y) or the last stored coordinates posted to chat", (args, player) -> {
            try {
                float x, y;
                if (args.length == 2) {
                    x = Float.parseFloat(args[0]);
                    y = Float.parseFloat(args[1]);
                } else {
                    x = World.conv(lastSentPos.x);
                    y = World.conv(lastSentPos.y);
                }
                Navigation.navigateTo(World.unconv(x), World.unconv(y));
            } catch(NumberFormatException | IndexOutOfBoundsException e){
                player.sendMessage("[scarlet]Invalid coordinates, format is [x] [y] Eg: !go 10 300 or !go");
            }
        });

        fooCommands.<Player>register("lookat","[x] [y]", "Moves camera to (x, y) or the last coordinates posted to chat", (args, player) -> {
            try {
                DesktopInput.panning = true;
                if (args.length == 2) lastSentPos.set(Float.parseFloat(args[0]), Float.parseFloat(args[1]));
                Spectate.spectate(lastSentPos.cpy().scl(tilesize));
            } catch(NumberFormatException | IndexOutOfBoundsException e){
                player.sendMessage("[scarlet]Invalid coordinates, format is [x] [y] Eg: !lookat 10 300 or !lookat");
            }
        });

        fooCommands.<Player>register("here", "[message...]", "Prints your location to chat with an optional message", (args, player) ->
                Call.sendChatMessage(String.format("%s(%s, %s)", args.length == 0 ? "" : args[0] + " ", player.tileX(), player.tileY()))
        );

        fooCommands.<Player>register("cursor", "[message...]", "Prints cursor location to chat with an optional message", (args, player) ->
                Call.sendChatMessage(String.format("%s(%s, %s)", args.length == 0 ? "" : args[0] + " ", World.toTile(Core.input.mouseWorldX()), World.toTile(Core.input.mouseWorldY())))
        );

        fooCommands.<Player>register("builder", "[options...]", "Starts auto build with optional arguments, prioritized from first to last.", (args, player) ->
                Navigation.follow(new BuildPath(args))
        );

        fooCommands.<Player>register("tp", "<x> <y>", "Moves to (x, y) at insane speeds, only works on servers without strict mode enabled.", (args, player) -> {
            try {
                NetClient.setPosition(World.unconv(Float.parseFloat(args[0])), World.unconv(Float.parseFloat(args[1])));
            }
            catch(Exception e){
                player.sendMessage("[scarlet]Invalid coordinates, format is <x> <y> Eg: !tp 10 300");
            }
        });

        fooCommands.<Player>register("", "[message...]", "Lets you start messages with an !", (args, player) ->
            Call.sendChatMessage("!" + (args.length == 1 ? args[0] : ""))
        );

        fooCommands.<Player>register("shrug", "[message...]", "Sends the shrug unicode emoji with an optional message", (args, player) ->
            Call.sendChatMessage("¯\\_(ツ)_/¯ " + (args.length == 1 ? args[0] : ""))
        );

        Events.on(WorldLoadEvent.class, event -> {
            if (Time.timeSinceMillis(lastSyncTime) > 5000) {
                tileLogs = new TileLog[world.height()][world.width()];
            }
            PowerInfo.initialize();
            Navigation.stopFollowing();
            Navigation.obstacles.clear();
            configs.clear();
            turrets.clear();
            UnitPicker.found = null;
            if (state.rules.pvp) ui.announce("[scarlet]Don't use a client in pvp, it's uncool!", 5);
            messageBlockPositions.clear();
        });

        Pattern coordPattern = Pattern.compile("\\d+(\\s|,)\\d+");
        Events.on(EventType.PlayerChatEvent.class, event -> {
            if (event.message == null) return;
            Matcher matcher = coordPattern.matcher(event.message);
            if (!matcher.matches()) return;
            String coords = matcher.toMatchResult().group(0);
            try {
                int x = Integer.parseInt(coords.split(",")[0]);
                int y = Integer.parseInt(coords.split(",")[1]);
                lastSentPos.set(World.unconv(x), World.unconv(y));
                Timer.schedule(() -> ui.chatfrag.addMessage("!go to navigate to (%d,%d)", "client", Color.coral.cpy().mul(0.75f)), 0.05f);
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        });

        Events.on(EventType.UnitChangeEvent.class, event -> {
            UnitType unit = UnitPicker.found;
            if (event.unit.team == player.team() && !(event.player == player)) {
                Unit find = Units.closest(player.team(), player.x, player.y, u -> !u.isPlayer() && u.type == unit && !u.dead);
                if (find != null) {
                    Call.unitControl(player, find);
                    Timer.schedule(() -> {
                        if (find.isPlayer()) {
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
            settings.getBoolOnce("updatevalues", () -> { // TODO: Remove this code and the updatevalues bool at some point in the future (this converts old settings to new format)
                settings.put("reactorwarningdistance", settings.getBool("reactorwarnings", true) ? settings.getInt("reactorwarningdistance") == 0 ? 101 : settings.getInt("reactorwarningdistance") : -1);
                settings.put("reactorsounddistance", settings.getBool("reactorwarningsounds", true) ? settings.getInt("reactorsounddistance") == 0 ? 101 : settings.getInt("reactorsounddistance") : -1);
                settings.remove("reactorwarnings");
                settings.remove("reactorwarningsounds");

                try { // Somehow this works?
                    Method ohno = ui.settings.client.getClass().getDeclaredMethod("rebuild");
                    ohno.setAccessible(true);
                    ohno.invoke(ui.settings.client);
                } catch (Exception e) {
                    Log.err(e);
                }
            });

            Autocomplete.autocompleters.add(new BlockEmotes());
            Autocomplete.autocompleters.add(new PlayerCompletion());
            Autocomplete.autocompleters.add(new CommandCompletion());
            Autocomplete.initialize();
            hideTrails = Core.settings.getBool("hidetrails");
            Blocks.sand.asFloor().playerUnmineable = !settings.getBool("doubleclicktomine");
            Blocks.darksand.asFloor().playerUnmineable = !settings.getBool("doubleclicktomine");
            Navigation.navigator.init();
        });
    }


    public static void update() {
        Navigation.update();
        PowerInfo.update();
        Spectate.update();

        if (!configs.isEmpty()) {
                try {
                    if (configRateLimit.allow(6 * 1000, 25)) {
                        ConfigRequest req = configs.last();
                        Tile tile = world.tile(req.x, req.y);
                        if (tile != null) {
//                            Object initial = tile.build.config();
                            req.run();
                            configs.remove(req);
//                            Timer.schedule(() -> {
//                                // if(tile.build != null && tile.build.config() == initial) configs.addLast(req); TODO: This can also cause loops
//                                // if(tile.build != null && req.value != tile.build.config()) configs.addLast(req); TODO: This infinite loops if u config something twice, find a better way to do this
//                            }, net.client() ? netClient.getPing()/1000f+.05f : .025f);
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