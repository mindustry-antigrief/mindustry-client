package mindustry.client;

import arc.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.client.antigrief.*;
import mindustry.client.navigation.*;
import mindustry.client.utils.*;
import mindustry.core.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.input.*;
import mindustry.world.*;

import static arc.Core.*;
import static mindustry.Vars.*;

public class Client {
    // TODO: Organize section below at least somewhat.
    private static TileLog[][] tileLogs;
    //todo: use this instead of Navigation.isFollowing and such
    public static ClientMode mode = ClientMode.normal;
    public static Queue<ConfigRequest> configs = new Queue<>();
    public static boolean showingTurrets, hideUnits, hidingBlocks;
    public static long lastSyncTime = 0L;
    public static final CommandHandler fooCommands = new CommandHandler("!");
    public static boolean hideTrails;
    public static Ratekeeper configRateLimit = new Ratekeeper();
    /** The last position in TILE COORDS someone sent in chat or was otherwise put into the buffer. */
    public static final Vec2 lastSentPos = new Vec2();
    public static IntSet messageBlockPositions = new IntSet();
    public static final String messageCommunicationPrefix = "IN USE FOR CHAT AUTHENTICATION, do not use";
    public static ClientInterface mapping;
    public static boolean dispatchingBuildPlans = false;
    public static final byte FOO_USER = (byte) 0b10101010, ASSISTING = (byte) 0b01010101;

    public static void initialize() {
        registerCommands();

        Events.on(WorldLoadEvent.class, event -> {
            if (Time.timeSinceMillis(lastSyncTime) > 5000) {
                tileLogs = new TileLog[world.height()][world.width()];
            }
            PowerInfo.initialize();
            Navigation.stopFollowing();
            Navigation.obstacles.clear();
            configs.clear();
            ui.unitPicker.found = null;
            if (state.rules.pvp) ui.announce("[scarlet]Don't use a client in pvp, it's uncool!", 5);
            messageBlockPositions.clear();
        });

        Events.on(EventType.ClientLoadEvent.class, event -> {
            int changeHash = Core.files.internal("changelog").readString().hashCode(); // Display changelog if the file contents have changed & on first run. (this is really scuffed lol).
            if (settings.getInt("changeHash") != changeHash) Client.mapping.showChangelogDialog();
            settings.put("changeHash", changeHash);

            settings.remove("updatevalues"); // TODO: Remove this line at some point in the future, removes an unused setting value. (added feb 10)

            hideTrails = Core.settings.getBool("hidetrails");
            Autocomplete.autocompleters.add(new BlockEmotes());
            Autocomplete.autocompleters.add(new PlayerCompletion());
            Autocomplete.autocompleters.add(new CommandCompletion());
            Autocomplete.initialize();
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

    private static void registerCommands(){
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
            ui.unitPicker.findUnit(content.units().copy().sort(b -> BiasedLevenshtein.biasedLevenshtein(args[0], b.name)).first());
        });

        fooCommands.<Player>register("go","[x] [y]", "Navigates to (x, y) or the last coordinates posted to chat", (args, player) -> {
            try {
                if (args.length == 2) lastSentPos.set(Float.parseFloat(args[0]), Float.parseFloat(args[1]));
                Navigation.navigateTo(lastSentPos.cpy().scl(tilesize));
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
                Navigation.follow(new BuildPath(args.length  == 0 ? "" : args[0]))
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

        fooCommands.<Player>register("login", "[name] [pw]", "Used for CN. [scarlet]Don't use this if you care at all about security.", (args, player) -> {
            if (args.length == 2) settings.put("cnpw", args[0] + " "  + args[1]);
            else Call.sendChatMessage("/login " + settings.getString("cnpw", ""));
        });
    }
}