package mindustry.client;

import arc.*;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.util.*;
import mindustry.client.antigrief.*;
import mindustry.client.navigation.*;
import mindustry.client.utils.*;
import mindustry.core.*;
import mindustry.game.EventType;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.gen.*;
import mindustry.input.DesktopInput;
import mindustry.net.Administration;
import mindustry.world.Tile;

import static arc.Core.settings;
import static mindustry.Vars.*;

public class Client {
    // TODO: Organize section below at least somewhat.
    private static TileLog[][] tileLogs;
    /** The last position in TILE COORDS someone sent in chat or was otherwise put into the buffer. */
    public static ClientInterface mapping;

    public static ClientVars vars;

    public static void initialize() {
        registerCommands();

        Events.on(WorldLoadEvent.class, event -> {
            vars.getMapping().setPluginNetworking(false);
            if (Time.timeSinceMillis(vars.getLastSyncTime()) > 5000) {
                tileLogs = new TileLog[world.height()][world.width()];
            }
            PowerInfo.initialize();
            Navigation.stopFollowing();
            Navigation.obstacles.clear();
            vars.getConfigs().clear();
            ui.unitPicker.found = null;

            control.input.lastVirusWarning = null;

            vars.setShowingTurrets(false);
            vars.setHideUnits(false);
            vars.setDispatchingBuildPlans(false);

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

        if (!vars.getConfigs().isEmpty()) {
                try {
                    if (vars.getConfigRateLimit().allow(Administration.Config.interactRateWindow.num() * 1000L, Administration.Config.interactRateLimit.num())) {
                        ConfigRequest req = vars.getConfigs().last();
                        Tile tile = world.tile(req.x, req.y);
                        if (tile != null) {
//                            Object initial = tile.build.config();
                            req.run();
                            vars.getConfigs().remove(req);
//                            Timer.schedule(() -> {
//                                // if(tile.build != null && tile.build.config() == initial) configs.addLast(req); TODO: This can also cause loops
//                                // if(tile.build != null && req.value != tile.build.config()) configs.addLast(req); TODO: This infinite loops if u config something twice, find a better way to do this
//                            }, net.client() ? netClient.getPing()/1000f+.05f : .025f);
                        }
                    }
                } catch (Exception e) { Log.info(e.getMessage()); }
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
        vars.getFooCommands().<Player>register("help", "[page]", "Lists all client commands.", (args, player) -> {
            if(args.length > 0 && !Strings.canParseInt(args[0])){
                player.sendMessage("[scarlet]'page' must be a number.");
                return;
            }
            int commandsPerPage = 6;
            int page = args.length > 0 ? Strings.parseInt(args[0]) : 1;
            int pages = Mathf.ceil((float)vars.getFooCommands().getCommandList().size / commandsPerPage);

            page --;

            if(page >= pages || page < 0){
                player.sendMessage("[scarlet]'page' must be a number between[orange] 1[] and[orange] " + pages + "[scarlet].");
                return;
            }

            StringBuilder result = new StringBuilder();
            result.append(Strings.format("[orange]-- Client Commands Page[lightgray] @[gray]/[lightgray]@[orange] --\n\n", (page+1), pages));

            for(int i = commandsPerPage * page; i < Math.min(commandsPerPage * (page + 1), vars.getFooCommands().getCommandList().size); i++){
                CommandHandler.Command command = vars.getFooCommands().getCommandList().get(i);
                result.append("[orange] !").append(command.text).append("[white] ").append(command.paramText).append("[lightgray] - ").append(command.description).append("\n");
            }
            player.sendMessage(result.toString());
        });

        vars.getFooCommands().<Player>register("unit", "<unit-name>", "Swap to specified unit", (args, player) -> {
            ui.unitPicker.findUnit(content.units().copy().sort(b -> BiasedLevenshtein.biasedLevenshtein(args[0], b.name)).first());
        });

        vars.getFooCommands().<Player>register("go","[x] [y]", "Navigates to (x, y) or the last coordinates posted to chat", (args, player) -> {
            try {
                if (args.length == 2) vars.getLastSentPos().set(Float.parseFloat(args[0]), Float.parseFloat(args[1]));
                Navigation.navigateTo(vars.getLastSentPos().cpy().scl(tilesize));
            } catch(NumberFormatException | IndexOutOfBoundsException e){
                player.sendMessage("[scarlet]Invalid coordinates, format is [x] [y] Eg: !go 10 300 or !go");
            }
        });

        vars.getFooCommands().<Player>register("lookat","[x] [y]", "Moves camera to (x, y) or the last coordinates posted to chat", (args, player) -> {
            try {
                DesktopInput.panning = true;
                if (args.length == 2) vars.getLastSentPos().set(Float.parseFloat(args[0]), Float.parseFloat(args[1]));
                Spectate.spectate(vars.getLastSentPos().cpy().scl(tilesize));
            } catch(NumberFormatException | IndexOutOfBoundsException e){
                player.sendMessage("[scarlet]Invalid coordinates, format is [x] [y] Eg: !lookat 10 300 or !lookat");
            }
        });

        vars.getFooCommands().<Player>register("here", "[message...]", "Prints your location to chat with an optional message", (args, player) ->
                Call.sendChatMessage(String.format("%s(%s, %s)", args.length == 0 ? "" : args[0] + " ", player.tileX(), player.tileY()))
        );

        vars.getFooCommands().<Player>register("cursor", "[message...]", "Prints cursor location to chat with an optional message", (args, player) ->
                Call.sendChatMessage(String.format("%s(%s, %s)", args.length == 0 ? "" : args[0] + " ", World.toTile(Core.input.mouseWorldX()), World.toTile(Core.input.mouseWorldY())))
        );

        vars.getFooCommands().<Player>register("builder", "[options...]", "Starts auto build with optional arguments, prioritized from first to last.", (args, player) ->
                Navigation.follow(new BuildPath(args.length  == 0 ? "" : args[0]))
        );

        vars.getFooCommands().<Player>register("tp", "<x> <y>", "Moves to (x, y) at insane speeds, only works on servers without strict mode enabled.", (args, player) -> {
            try {
                NetClient.setPosition(World.unconv(Float.parseFloat(args[0])), World.unconv(Float.parseFloat(args[1])));
            } catch(Exception e) {
                player.sendMessage("[scarlet]Invalid coordinates, format is <x> <y> Eg: !tp 10 300");
            }
        });

        vars.getFooCommands().<Player>register("", "[message...]", "Lets you start messages with an !", (args, player) ->
                Call.sendChatMessage("!" + (args.length == 1 ? args[0] : ""))
        );

        vars.getFooCommands().<Player>register("shrug", "[message...]", "Sends the shrug unicode emoji with an optional message", (args, player) ->
                Call.sendChatMessage("¯\\_(ツ)_/¯ " + (args.length == 1 ? args[0] : ""))
        );

        vars.getFooCommands().<Player>register("login", "[name] [pw]", "Used for CN. [scarlet]Don't use this if you care at all about security.", (args, player) -> {
            if (args.length == 2) settings.put("cnpw", args[0] + " "  + args[1]);
            else Call.sendChatMessage("/login " + settings.getString("cnpw", ""));
        });

        vars.getFooCommands().<Player>register("js", "<code...>", "Runs JS on the client.", (arg, player) ->
                ui.chatfrag.addMessage(mods.getScripts().runConsole(arg[0]), "client", Color.coral.cpy().mul(0.75f)));
    }
}