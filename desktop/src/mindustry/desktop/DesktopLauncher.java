package mindustry.desktop;

import arc.Core;
import arc.*;
import arc.Files.*;
import arc.backend.sdl.*;
import arc.backend.sdl.jni.*;
import arc.files.*;
import arc.func.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import arc.util.serialization.*;
import com.codedisaster.steamworks.*;
import de.jcm.discordgamesdk.*;
import de.jcm.discordgamesdk.activity.*;
import de.jcm.discordgamesdk.user.*;
import mindustry.*;
import mindustry.client.*;
import mindustry.client.utils.*;
import mindustry.core.*;
import mindustry.desktop.steam.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.mod.*;
import mindustry.net.*;
import mindustry.net.Net.*;
import mindustry.service.*;
import mindustry.type.*;

import java.io.*;
import java.time.*;
import java.util.*;

import static mindustry.Vars.*;

public class DesktopLauncher extends ClientLauncher{
    public final static long discordID = 514551367759822855L;
    boolean useDiscord = !OS.hasProp("nodiscord"), loadError = false;
    de.jcm.discordgamesdk.Core discordCore;
    Throwable steamError;

    public static void main(String[] arg){
        System.out.println("Launching Mindustry! Arguments: " + Arrays.toString(arg));
        try{
            int[] aaSamples = new int[1];

            String env = OS.hasProp("aaSamples") ? OS.prop("aaSamples") : OS.hasEnv("aaSamples") ? OS.env("aaSamples") : "";
            if (Strings.canParsePositiveInt(env)) aaSamples[0] = Math.min(Integer.parseInt(env), 32);

            Vars.loadLogger();
            Version.init();

            Events.on(EventType.ClientLoadEvent.class, e -> Core.graphics.setTitle(getWindowTitle()));

            new UnpackJars().unpack();
            Log.infoTag("AA Samples", "" + aaSamples[0]);

            new SdlApplication(new DesktopLauncher(arg), new SdlConfig() {{
                title = getWindowTitle();
                maximized = true;
                width = 900;
                height = 700;
                samples = aaSamples[0];
                //enable gl3 with command-line argument (slower performance, apparently)
                for(int i = 0; i < arg.length; i++){
                    if(arg[i].charAt(0) == '-'){
                        String name = arg[i].substring(1);
                        try{
                            //noinspection EnhancedSwitchMigration
                            switch(name){
                                case "width": width = Integer.parseInt(arg[i + 1]); break;
                                case "height": height = Integer.parseInt(arg[i + 1]); break;
                                case "gl3": gl30 = true; break;
                                case "antialias": samples = 16; break;
                                case "debug": Log.level = Log.LogLevel.debug; break;
                                case "maximized": maximized = Boolean.parseBoolean(arg[i + 1]); break;
                            }
                        }catch(NumberFormatException number){
                            Log.warn("Invalid parameter number value.");
                        }
                    }
                }

                setWindowIcon(FileType.internal, "icons/foo_64.png");
            }});

        }catch(Throwable e){
            handleCrash(e);
        }
    }

    private static String getWindowTitle(){
        int enabled = 0;
        if(mods != null){
            for(Mods.LoadedMod mod : mods.mods){
                if(mod.enabled()) enabled++;
            }
        }
        return Strings.format("Mindustry (v@) | Foo's Client (@) | @/@ Mods Enabled", Version.buildString(), Version.clientVersion.equals("v0.0.0") ? "Dev" : Version.clientVersion, enabled, mods == null ? 0 : mods.mods.size);
    }

    @Override
    public void stopDiscord(){
        if (discordCore != null) {
            discordCore.activityManager().clearActivity();
            discordCore.close();
        }
    }

    @Override
    public void startDiscord(){
        if (!useDiscord) return;
        stopDiscord(); // Stop any still running discord instance

        if(discordCore == null){ // Run exactly once
            Core.app.addListener(new ApplicationListener(){
                @Override
                public void update(){
                    if(discordCore != null){
                        discordCore.runCallbacks();
                    }
                }
            });
        }

//        File discordLib = dataDirectory.child("discord_game_sdk.dll").file();
        try{
            de.jcm.discordgamesdk.Core.initDownload(); // FINISHME: This downloads a new dll every time, rewrite this so that it caches
        }catch(IOException e){
            Log.err("Error loading discord", e);
            useDiscord = false;
            return;
        }

        DiscordEventAdapter handler = new DiscordEventAdapter(){
            /** We're joining someone */
            @Override
            public void onActivityJoin(String secret) {
                Log.info("On activity join | Secret: @", secret);
                SVars.net.onGameRichPresenceJoinRequested(null, secret.split(" ")[1]);
            }

            /** Someone requested to join us */
            @Override
            public void onActivityJoinRequest(DiscordUser user) { // FINISHME: Add a dialog for this
                Log.info("On activity join request | User: @", user);
            }
        };

        CreateParams params = new CreateParams();
        params.setClientID(discordID);
        params.setFlags(CreateParams.Flags.NO_REQUIRE_DISCORD);
        params.registerEventHandler(handler);
        discordCore = new de.jcm.discordgamesdk.Core(params);
        discordCore.activityManager().registerSteam(1127400);
//        if(useDiscord){
//            try{
//                DiscordRPC.connect(discordID);
//                updateRPC();
//                Log.info("Initialized Discord rich presence.");
//            }catch(NoDiscordClientException none){
//                useDiscord = false;
//                Log.debug("Not initializing Discord RPC - no discord instance open.");
//            }catch(Throwable t){
//                useDiscord = false;
//                Log.warn("Failed to initialize Discord RPC - you are likely using a JVM <16.");
//            }
//        }
    }

//    public static File downloadDiscord() throws IOException
//    {
//        // Find out which name Discord's library has (.dll for Windows, .so for Linux)
//        String name = "discord_game_sdk";
//        String suffix = OS.isWindows ? ".dll" : OS.isLinux ? ".so" : OS.isMac ? ".dylib" : null;
//        if (suffix == null) throw new ArcRuntimeException("Unknown OS: " + OS.osName);
//        String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
//    /*
//    Some systems report "amd64" (e.g. Windows and Linux), some "x86_64" (e.g. Mac OS).
//    At this point we need the "x86_64" version, as this one is used in the ZIP.
//     */
//        if(arch.equals("amd64"))
//            arch = "x86_64";
//        // Path of Discord's library inside the ZIP
//        String zipPath = "lib/"+arch+"/"+name+suffix;
//        // Open the URL as a ZipInputStream
//        URL downloadUrl = new URL("https://dl-game-sdk.discordapp.net/3.2.1/discord_game_sdk.zip");
//        HttpURLConnection connection = (HttpURLConnection) downloadUrl.openConnection();
//        connection.setRequestProperty("User-Agent", "discord-game-sdk4j (https://github.com/JnCrMx/discord-game-sdk4j)");
//        ZipInputStream zin = new ZipInputStream(connection.getInputStream());
//        // Search for the right file inside the ZIP
//        ZipEntry entry;
//        while((entry = zin.getNextEntry())!=null)
//        {
//            if(entry.getName().equals(zipPath))
//            {
//                // Create a new temporary directory
//                // We need to do this, because we may not change the filename on Windows
//                File tempDir = new File(System.getProperty("java.io.tmpdir"), "java-"+name+System.nanoTime());
//                if(!tempDir.mkdir())
//                    throw new IOException("Cannot create temporary directory");
//                tempDir.deleteOnExit();
//                // Create a temporary file inside our directory (with a "normal" name)
//                File temp = new File(tempDir, name+suffix);
//                temp.deleteOnExit();
//                // Copy the file in the ZIP to our temporary file
//                Files.copy(zin, temp.toPath());
//                // We are done, so close the input stream
//                zin.close();
//                // Return our temporary file
//                return temp;
//            }
//            // next entry
//            zin.closeEntry();
//        }
//        zin.close();
//        // We couldn't find the library inside the ZIP
//        return null;
//    }

    public DesktopLauncher(String[] args){
        boolean useSteam = (Version.modifier.contains("steam") || new Fi("./steam_appid.txt").exists()) && !OS.hasProp("nosteam");
        testMobile = Seq.with(args).contains("-testMobile");

        add(Main.INSTANCE);

        if(useSteam){
            Events.on(ClientLoadEvent.class, event -> {
                if(steamError != null){
                    Core.app.post(() -> Core.app.post(() -> Core.app.post(() -> {
                        ui.showErrorMessage(Core.bundle.format("steam.error", (steamError.getMessage() == null) ? steamError.getClass().getSimpleName() : steamError.getClass().getSimpleName() + ": " + steamError.getMessage()));
                    })));
                }
            });

            try{
                SteamAPI.loadLibraries();

                if(!SteamAPI.init()){
                    loadError = true;
                    Log.err("Steam client not running.");
                }else{
                    initSteam(args);
                    Vars.steam = true;
                }

                if(SteamAPI.restartAppIfNecessary(SVars.steamID)){
                    System.exit(0);
                }
            }catch(Throwable e){
                steam = false;
                Log.err("Failed to load Steam native libraries.");
                logSteamError(e);
            }
        }

        Events.on(DisposeEvent.class, e -> stopDiscord());
    }

    void logSteamError(Throwable e){
        steamError = e;
        loadError = true;
        Log.err(e);
        try(OutputStream s = new FileOutputStream("steam-error-log-" + System.nanoTime() + ".txt")){
            String log = Strings.neatError(e);
            s.write(log.getBytes());
        }catch(Exception e2){
            Log.err(e2);
        }
    }

    void initSteam(String[] args){
        SVars.net = new SNet(new ArcNetProvider());
        SVars.stats = new SStats();
        SVars.workshop = new SWorkshop();
        SVars.user = new SUser();
        boolean[] isShutdown = {false};

        service = new GameService(){

            @Override
            public boolean enabled(){
                return true;
            }

            @Override
            public void completeAchievement(String name){
                SVars.stats.stats.setAchievement(name);
                SVars.stats.stats.storeStats();
            }

            @Override
            public void clearAchievement(String name){
                SVars.stats.stats.clearAchievement(name);
                SVars.stats.stats.storeStats();
            }

            @Override
            public boolean isAchieved(String name){
                return SVars.stats.stats.isAchieved(name, false);
            }

            @Override
            public int getStat(String name, int def){
                return SVars.stats.stats.getStatI(name, def);
            }

            @Override
            public void setStat(String name, int amount){
                SVars.stats.stats.setStatI(name, amount);
            }

            @Override
            public void storeStats(){
                SVars.stats.onUpdate();
            }
        };

        Events.on(ClientLoadEvent.class, event -> {
            Core.settings.defaults("name", SVars.net.friends.getPersonaName());
            if(player.name.isEmpty()){
                player.name = SVars.net.friends.getPersonaName();
                Core.settings.put("name", player.name);
            }
            steamPlayerName = SVars.net.friends.getPersonaName();
            //update callbacks
            Core.app.addListener(new ApplicationListener(){
                @Override
                public void update(){
                    if(SteamAPI.isSteamRunning()){
                        SteamAPI.runCallbacks();
                    }
                }
            });

            Core.app.post(() -> {
                if(args.length >= 2 && args[0].equals("+connect_lobby")){
                    try{
                        long id = Long.parseLong(args[1]);
                        ui.join.connect("steam:" + id, port);
                    }catch(Exception e){
                        Log.err("Failed to parse steam lobby ID: @", e.getMessage());
                        e.printStackTrace();
                    }
                }else if(args.length >= 2 && args[0].equals("+connect_server")){
                    SVars.net.onGameRichPresenceJoinRequested(null, args[1]);
                }
            });
        });

        Events.on(DisposeEvent.class, event -> {
            SteamAPI.shutdown();
            isShutdown[0] = true;
        });

        //steam shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if(!isShutdown[0]){
                SteamAPI.shutdown();
            }
        }));
    }

    static void handleCrash(Throwable e){
        Cons<Runnable> dialog = Runnable::run;
        boolean badGPU = false;
        String finalMessage = Strings.getFinalMessage(e);
        String total = Strings.getCauses(e).toString();

        if(total.contains("Couldn't create window") || total.contains("OpenGL 2.0 or higher") || total.toLowerCase().contains("pixel format") || total.contains("GLEW")|| total.contains("unsupported combination of formats")){

            dialog.get(() -> message(
                total.contains("Couldn't create window") ? "A graphics initialization error has occured! Try to update your graphics drivers:\n" + finalMessage :
                    "Your graphics card does not support the right OpenGL features.\n" +
                    "Try to update your graphics drivers. If this doesn't work, your computer may not support Mindustry.\n\n" +
                    "Full message: " + finalMessage));
            badGPU = true;
        }

        boolean fbgp = badGPU;

        CrashSender.send(e, file -> {
            Throwable fc = Strings.getFinalCause(e);
            if(!fbgp){
                dialog.get(() -> message("A crash has occurred. It has been saved in:\n" + file.getAbsolutePath() + "\n" + fc.getClass().getSimpleName().replace("Exception", "") + (fc.getMessage() == null ? "" : ":\n" + fc.getMessage())));
            }
        });
    }

    @Override
    public Seq<Fi> getWorkshopContent(Class<? extends Publishable> type){
        return !steam ? super.getWorkshopContent(type) : SVars.workshop.getWorkshopFiles(type);
    }

    @Override
    public void viewListing(Publishable pub){
        SVars.workshop.viewListing(pub);
    }

    @Override
    public void viewListingID(String id){
        SVars.net.friends.activateGameOverlayToWebPage("steam://url/CommunityFilePage/" + id);
    }

    @Override
    public NetProvider getNet(){
        return steam ? SVars.net : new ArcNetProvider();
    }

    @Override
    public void openWorkshop(){
        SVars.net.friends.activateGameOverlayToWebPage("https://steamcommunity.com/app/1127400/workshop/");
    }

    @Override
    public void publish(Publishable pub){
        SVars.workshop.publish(pub);
    }

    @Override
    public void inviteFriends(){
        SVars.net.showFriendInvites();
    }

    @Override
    public void updateLobby(){
        if(SVars.net != null){
            SVars.net.updateLobby();
        }
    }

    @Override
    public void updateRPC(){
        //if we're using neither discord nor steam, do no work
        if((!useDiscord || !Core.settings.getBool("discordrpc")) && !steam) return;

        //common elements they each share
        boolean inGame = state.isGame();
        String gameMapWithWave = "Unknown Map";
        String gameMode = "";
        String gamePlayersSuffix = "";
        String uiState = "";

        if(inGame){
            gameMapWithWave = Strings.capitalize(Strings.stripColors(state.map.name()));

            if(state.rules.waves){
                gameMapWithWave += " | Wave " + state.wave;
            }
            gameMode = state.rules.pvp ? "PvP" : state.rules.attackMode ? "Attack" : state.rules.infiniteResources ? "Sandbox" : "Survival";
            if(net.active() && Groups.player.size() > 1){
                gamePlayersSuffix = " | " + Groups.player.size() + " Players";
            }
        }else{
            if(ui.editor != null && ui.editor.isShown()){
                uiState = "In Editor";
            }else if(ui.planet != null && ui.planet.isShown()){
                uiState = "In Launch Selection";
            }else{
                uiState = "In Menu";
            }
        }

        String currentLobby = SVars.net.currentLobby == null ? null : "" + SVars.net.currentLobby.handle();
        String partyID = net.server() ? currentLobby :
            net.client() && currentLobby != null ? currentLobby :
            ui.join.lastHost != null ? ui.join.lastHost.addrPort() :
            null;

        if(useDiscord && Core.settings.getBool("discordrpc")){
            try(Activity presence = new Activity()){
                if(inGame){
                    presence.setState(gameMode + gamePlayersSuffix);
                    presence.setDetails(gameMapWithWave);
                    if(state.rules.waves){
                        presence.assets().setLargeText("Wave " + state.wave);
                    }
                }else{
                    presence.setState(uiState);
                }

                presence.assets().setLargeImage("logo");
                presence.assets().setSmallImage("foo");
                presence.assets().setSmallText(Strings.format("Foo's Client (@)", Version.clientVersion.equals("v0.0.0") ? "Dev" : Version.clientVersion));
                presence.timestamps().setStart(Instant.ofEpochSecond(state.tick == 0 ? beginTime/1000 : Time.timeSinceMillis((long)(state.tick * 16.666))));
//                presence.label1 = "Client Github"; FINISHME: This isn't supported in this library for some reason
//                presence.url1 = "https://github.com/mindustry-antigrief/mindustry-client";
                if(net.active() && !Groups.player.isEmpty()){ // Player group is empty during sync and causes a crash
//                    DiscordRPC.onActivityJoinRequest = (secret, user) -> Log.info("Received discord request for @ by @", secret, user);
                    presence.party().setID(partyID);
                    presence.party().size().setCurrentSize(Groups.player.size());
                    presence.party().size().setMaxSize(100); // This shouldn't be fixed to 100
                    // FINISHME: Dynamic number above, handle steam lobbies below.
                    presence.secrets().setJoinSecret(net.client() && currentLobby == null && ui.join.lastHost != null ? Strings.format("+connect_server @", ui.join.lastHost.addrPort()) : null);
                }

                discordCore.activityManager().updateActivity(presence);
            }
        }

        if(steam){
            //Steam mostly just expects us to give it a nice string, but it apparently expects "steam_display" to always be a loc token, so I've uploaded this one which just passes through 'steam_status' raw.
            SVars.net.friends.setRichPresence("steam_display", "#steam_status_raw");

            String status = Strings.format("Foo's Client (@) | @", Version.clientVersion.equals("v0.0.0") ? "Dev" : Version.clientVersion, inGame ? gameMapWithWave : uiState);
            SVars.net.friends.setRichPresence("steam_status", status);
            SVars.net.friends.setRichPresence("status", inGame ? status : null); // This shows in the view game info menu. We should add more stuff to it, using the steam_status value is just a placeholder as it's required for joining.
            SVars.net.friends.setRichPresence("steam_player_group", partyID);
            SVars.net.friends.setRichPresence("steam_player_group_size", net.active() ? "" + Groups.player.size() : null);
            SVars.net.friends.setRichPresence("connect", net.client() && currentLobby == null && ui.join.lastHost != null ? Strings.format("+connect_server @", ui.join.lastHost.addrPort()) : null);
        }
    }

    @Override
    public String getUUID(){
        if(steam){
            try{
                byte[] result = new byte[8];
                new Rand(SVars.user.user.getSteamID().getAccountID()).nextBytes(result);
                return new String(Base64Coder.encode(result));
            }catch(Exception e){
                e.printStackTrace();
            }
        }

        return super.getUUID();
    }

    private static void message(String message){
        SDL.SDL_ShowSimpleMessageBox(SDL.SDL_MESSAGEBOX_ERROR, "oh no", message);
    }
}
