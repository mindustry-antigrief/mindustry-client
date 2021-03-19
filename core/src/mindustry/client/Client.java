package mindustry.client;

import arc.*;
import arc.math.*;
import arc.util.*;
import mindustry.client.antigrief.*;
import mindustry.client.navigation.*;
import mindustry.client.utils.*;
import mindustry.core.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.input.*;
import mindustry.net.*;
import mindustry.world.*;

import static arc.Core.*;
import static mindustry.Vars.*;

public class Client {
    private static TileLog[][] tileLogs;
    public static ClientInterface mapping;

    private static int fuelTimer;
    /** Last time (millis) that the !fuel command was run */
    public static long lastFuelTime;
    private static final Interval timer = new Interval();

    public static void initialize() {
        registerCommands();

        Events.on(WorldLoadEvent.class, event -> {
            mapping.setPluginNetworking(false);
            if (Time.timeSinceMillis(ClientVars.lastSyncTime) > 5000) {
                tileLogs = new TileLog[world.height()][world.width()];
                fuelTimer = 0;
            }
            PowerInfo.initialize();
            Navigation.stopFollowing();
            Navigation.obstacles.clear();
            ClientVars.configs.clear();
            ui.unitPicker.found = null;
            control.input.lastVirusWarning = null;

            ClientVars.showingTurrets = false;
            ClientVars.hidingUnits = false;
            ClientVars.hidingBlocks = false;
            ClientVars.dispatchingBuildPlans = false;

            if (state.rules.pvp) ui.announce("[scarlet]Don't use a client in pvp, it's uncool!", 5);
        });

        Events.on(EventType.ClientLoadEvent.class, event -> {
            int changeHash = Core.files.internal("changelog").readString().hashCode(); // Display changelog if the file contents have changed & on first run. (this is really scuffed lol)
            if (settings.getInt("changeHash") != changeHash) Client.mapping.showChangelogDialog();
            settings.put("changeHash", changeHash);

            if (settings.getBool("debug")) Log.level = Log.LogLevel.debug; // Set log level to debug if the setting is checked
            if (Core.settings.getBool("discordrpc")) platform.startDiscord();

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

        if (!ClientVars.configs.isEmpty()) {
                try {
                    if (ClientVars.configRateLimit.allow(Administration.Config.interactRateWindow.num() * 1000L, Administration.Config.interactRateLimit.num())) {
                        ConfigRequest req = ClientVars.configs.removeLast();
                        Tile tile = world.tile(req.x, req.y);
                        if (tile != null) {
//                            Object initial = tile.build.config();
                            req.run();
//                            Timer.schedule(() -> {
//                                 if(tile.build != null && tile.build.config() == initial) configs.addLast(req); TODO: This can also cause loops
//                                 if(tile.build != null && req.value != tile.build.config()) configs.addLast(req); TODO: This infinite loops if u config something twice, find a better way to do this
//                            }, net.client() ? netClient.getPing()/1000f+.05f : .025f);
                        }
                    }
                } catch (Exception e) { Log.err(e); }
        }

        if (timer.get(Integer.MAX_VALUE) || fuelTimer > 0 && timer.get(fuelTimer * 60)) { // Auto fuel for cn
            lastFuelTime = Time.millis();
            Call.sendChatMessage("/fuel");
            Time.run(10f, () -> Call.tileTap(player, world.tile(0,0)));
            Time.run(20f, () -> Call.tileTap(player, world.tile(world.width() - 1, world.height() - 1)));
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
        ClientVars.clientCommandHandler.<Player>register("help", "[page]", "Lists all client commands.", (args, player) -> {
            if(args.length > 0 && !Strings.canParseInt(args[0])){
                player.sendMessage("[scarlet]'page' must be a number.");
                return;
            }
            int commandsPerPage = 6;
            int page = args.length > 0 ? Strings.parseInt(args[0]) : 1;
            int pages = Mathf.ceil((float)ClientVars.clientCommandHandler.getCommandList().size / commandsPerPage);

            page --;

            if(page >= pages || page < 0){
                player.sendMessage("[scarlet]'page' must be a number between[orange] 1[] and[orange] " + pages + "[scarlet].");
                return;
            }

            StringBuilder result = new StringBuilder();
            result.append(Strings.format("[orange]-- Client Commands Page[lightgray] @[gray]/[lightgray]@[orange] --\n\n", (page+1), pages));

            for(int i = commandsPerPage * page; i < Math.min(commandsPerPage * (page + 1), ClientVars.clientCommandHandler.getCommandList().size); i++){
                CommandHandler.Command command = ClientVars.clientCommandHandler.getCommandList().get(i);
                result.append("[orange] !").append(command.text).append("[white] ").append(command.paramText).append("[lightgray] - ").append(command.description).append("\n");
            }
            player.sendMessage(result.toString());
        });

        ClientVars.clientCommandHandler.<Player>register("unit", "<unit-name>", "Swap to specified unit", (args, player) ->
            ui.unitPicker.findUnit(content.units().copy().sort(b -> BiasedLevenshtein.biasedLevenshtein(args[0], b.name)).first())
        );

        ClientVars.clientCommandHandler.<Player>register("go","[x] [y]", "Navigates to (x, y) or the last coordinates posted to chat", (args, player) -> {
            try {
                if (args.length == 2) ClientVars.lastSentPos.set(Float.parseFloat(args[0]), Float.parseFloat(args[1]));
                Navigation.navigateTo(ClientVars.lastSentPos.cpy().scl(tilesize));
            } catch(NumberFormatException | IndexOutOfBoundsException e) {
                player.sendMessage("[scarlet]Invalid coordinates, format is [x] [y] Eg: !go 10 300 or !go");
            }
        });

        ClientVars.clientCommandHandler.<Player>register("lookat","[x] [y]", "Moves camera to (x, y) or the last coordinates posted to chat", (args, player) -> {
            try {
                DesktopInput.panning = true;
                if (args.length == 2) ClientVars.lastSentPos.set(Float.parseFloat(args[0]), Float.parseFloat(args[1]));
                Spectate.spectate(ClientVars.lastSentPos.cpy().scl(tilesize));
            } catch(NumberFormatException | IndexOutOfBoundsException e) {
                player.sendMessage("[scarlet]Invalid coordinates, format is [x] [y] Eg: !lookat 10 300 or !lookat");
            }
        });

        ClientVars.clientCommandHandler.<Player>register("here", "[message...]", "Prints your location to chat with an optional message", (args, player) ->
            Call.sendChatMessage(String.format("%s(%s, %s)", args.length == 0 ? "" : args[0] + " ", player.tileX(), player.tileY()))
        );

        ClientVars.clientCommandHandler.<Player>register("cursor", "[message...]", "Prints cursor location to chat with an optional message", (args, player) ->
            Call.sendChatMessage(String.format("%s(%s, %s)", args.length == 0 ? "" : args[0] + " ", World.toTile(Core.input.mouseWorldX()), World.toTile(Core.input.mouseWorldY())))
        );

        ClientVars.clientCommandHandler.<Player>register("builder", "[options...]", "Starts auto build with optional arguments, prioritized from first to last.", (args, player) ->
            Navigation.follow(new BuildPath(args.length  == 0 ? "" : args[0]))
        );

        ClientVars.clientCommandHandler.<Player>register("tp", "<x> <y>", "Moves to (x, y) at insane speeds, only works on servers without strict mode enabled.", (args, player) -> {
            try {
                NetClient.setPosition(World.unconv(Float.parseFloat(args[0])), World.unconv(Float.parseFloat(args[1])));
            } catch(Exception e) {
                player.sendMessage("[scarlet]Invalid coordinates, format is <x> <y> Eg: !tp 10 300");
            }
        });

        ClientVars.clientCommandHandler.<Player>register("", "[message...]", "Lets you start messages with an !", (args, player) ->
            Call.sendChatMessage("!" + (args.length == 1 ? args[0] : ""))
        );

        ClientVars.clientCommandHandler.<Player>register("shrug", "[message...]", "Sends the shrug unicode emoji with an optional message", (args, player) ->
            Call.sendChatMessage("¯\\_(ツ)_/¯ " + (args.length == 1 ? args[0] : ""))
        );

        ClientVars.clientCommandHandler.<Player>register("login", "[name] [pw]", "Used for CN. [scarlet]Don't use this if you care at all about security.", (args, player) -> {
            if (args.length == 2) settings.put("cnpw", args[0] + " "  + args[1]);
            else Call.sendChatMessage("/login " + settings.getString("cnpw", ""));
        });

        ClientVars.clientCommandHandler.<Player>register("js", "<code...>", "Runs JS on the client.", (args, player) ->
            player.sendMessage("[accent]" + mods.getScripts().runConsole(args[0]))
        );

        ClientVars.clientCommandHandler.<Player>register("fuel", "[interval]", "Runs the fuel command on cn, selects the entire map, optional interval in seconds (min 30)", (args, player) -> {
            if (args.length == 0) {
                timer.reset(0, -Integer.MAX_VALUE); // Jank way to fuel once right now
                player.sendMessage("[accent]Fueled successfully.");
            }   else {
                try {
                    fuelTimer = Short.parseShort(args[0]);
                    if (fuelTimer > 0){
                        fuelTimer = Math.max(fuelTimer, 30); // Min of 30s
                        player.sendMessage("[accent]Successfully set auto-fuel to run every " + fuelTimer + " seconds. (use !fuel 0 to disable)");
                    } else {
                        player.sendMessage("[accent]Successfully disabled auto-fuel.");
                    }
                } catch (Exception e) {
                    fuelTimer = 0;
                    player.sendMessage("[scarlet]That number was invalid, disabling auto-fuel.");
                }
            }
        });
    }
}