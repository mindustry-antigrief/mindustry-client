package mindustry.net;

import arc.*;
import arc.files.*;
import arc.func.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.*;
import mindustry.core.*;
import mindustry.mod.Mods.*;
import mindustry.ui.dialogs.*;

import java.io.*;
import java.text.*;
import java.util.*;

import static arc.Core.*;
import static mindustry.Vars.*;

public class CrashHandler{

    public static String createReport(Throwable exception){
        String error = writeException(exception);
        LoadedMod cause = getModCause(exception);
        var lastHost = ui.join != null ? ui.join.lastHost : null;
        var lastIp = ui.join != null ? Reflect.<String>get(JoinDialog.class, ui.join, "lastIp") : null;
        var group = lastHost != null ? lastHost.group != null ? lastHost.group : ui.join.communityHosts.contains(h -> h.equals(lastHost)) ? ui.join.communityHosts.find(h -> h.equals(lastHost)).group : null : null;

        String report = "Ohno, the game has crashed. Report this at: " + clientDiscord + "\n";
        if(cause != null) report += "The mod '" +  cause.meta.displayName + "' (" + cause.name + ") has caused foo's client to crash\n.";
        report += "\nCopy paste the report below when reporting:\n```java\n";

        return Strings.stripColors(report
        + "Version: " + Version.combined() + (Vars.headless ? " (Server)" : "") + "\n"
        + "Last Server: " + (lastHost != null ? lastHost.name + (group != null ? " (" + group + ") " : "(nogroup)") + " (" + lastHost.address + ":" + lastHost.port + ")" : lastIp != null && lastIp.startsWith("steam:") ? "steam" : "unknown/none") + "\n"
        + "Source: " + settings.getString("updateurl") + "\n"
        + "OS: " + OS.osName + " x" + (OS.osArchBits) + " (" + OS.osArch + ")\n"
        + ((OS.isAndroid || OS.isIos) && app != null ? "Android API level: " + Core.app.getVersion() + "\n" : "")
        + "Java Version: " + OS.javaVersion + "\n"
        + "Runtime Available Memory: " + (Runtime.getRuntime().maxMemory() / 1024 / 1024) + "mb\n"
        + "Cores: " + Runtime.getRuntime().availableProcessors() + "\n"
        + (cause == null ? "" : "Likely Cause: " + cause.meta.displayName + " (" + cause.name + " v" + cause.meta.version + ")\n")
        + (mods == null ? "<no mod init>" : "Mods: " + (!mods.list().contains(LoadedMod::enabled) ? "none (vanilla)" : mods.list().select(LoadedMod::shouldBeEnabled).toString(", ", mod -> mod.name + ":" + mod.meta.version)))
        + "\n\n") + error + "```";
    }

    public static void log(Throwable exception){
        try{
            Core.settings.getDataDirectory().child("crashes").child("crash_" + System.currentTimeMillis() + ".txt")
            .writeString(createReport(exception));
        }catch(Throwable ignored){
        }
    }

    public static void handle(Throwable exception, Cons<File> writeListener){
        try{
            try{
                //log to file
                Log.err(exception);
            }catch(Throwable no){
                exception.printStackTrace();
            }

            //try saving game data
            try{
                settings.manualSave();
            }catch(Throwable ignored){}

            //don't create crash logs for custom builds, as it's expected
            if(OS.username.equals("anuke") && !"steam".equals(Version.modifier)){
                System.exit(1);
            }

            //attempt to load version regardless
            if(Version.number == 0){
                try{
                    ObjectMap<String, String> map = new ObjectMap<>();
                    PropertiesUtils.load(map, new InputStreamReader(CrashHandler.class.getResourceAsStream("/version.properties")));

                    Version.type = map.get("type");
                    Version.number = Integer.parseInt(map.get("number"));
                    Version.modifier = map.get("modifier");
                    if(map.get("build").contains(".")){
                        String[] split = map.get("build").split("\\.");
                        Version.build = Integer.parseInt(split[0]);
                        Version.revision = Integer.parseInt(split[1]);
                    }else{
                        Version.build = Strings.canParseInt(map.get("build")) ? Integer.parseInt(map.get("build")) : -1;
                    }
                }catch(Throwable e){
                    e.printStackTrace();
                    Log.err("Failed to parse version.");
                }
            }

            try{
                File file = new File(OS.getAppDataDirectoryString(Vars.appName), "crashes/crash-report-" + new SimpleDateFormat("MM_dd_yyyy_HH_mm_ss").format(new Date()) + ".txt");
                new Fi(OS.getAppDataDirectoryString(Vars.appName)).child("crashes").mkdirs();
                new Fi(file).writeString(createReport(exception));
                writeListener.get(file);
            }catch(Throwable e){
                Log.err("Failed to save local crash report.", e);
            }

            if(true) return; // FOO'S RETURN


            try{
                //check crash report setting
                if(!Core.settings.getBool("crashreport", true)){
                    return;
                }
            }catch(Throwable ignored){
                //if there's no settings init we don't know what the user wants but chances are it's an important crash, so send it anyway
            }

            try{
                //check any mods - if there are any, don't send reports
                if(Vars.mods != null && !Vars.mods.list().isEmpty()){
                    return;
                }
            }catch(Throwable ignored){
            }

            //do not send exceptions that occur for versions that can't be parsed
            if(Version.number == 0){
                return;
            }

            boolean netActive = false, netServer = false;

            //attempt to close connections, if applicable
            try{
                netActive = net.active();
                netServer = net.server();
                net.dispose();
            }catch(Throwable ignored){
            }

            //disabled until further notice.
            /*

            JsonValue value = new JsonValue(ValueType.object);

            boolean fn = netActive, fs = netServer;

            //add all relevant info, ignoring exceptions
            ex(() -> value.addChild("versionType", new JsonValue(Version.type)));
            ex(() -> value.addChild("versionNumber", new JsonValue(Version.number)));
            ex(() -> value.addChild("versionModifier", new JsonValue(Version.modifier)));
            ex(() -> value.addChild("build", new JsonValue(Version.build)));
            ex(() -> value.addChild("revision", new JsonValue(Version.revision)));
            ex(() -> value.addChild("net", new JsonValue(fn)));
            ex(() -> value.addChild("server", new JsonValue(fs)));
            ex(() -> value.addChild("players", new JsonValue(Groups.player.size())));
            ex(() -> value.addChild("state", new JsonValue(Vars.state.getState().name())));
            ex(() -> value.addChild("os", new JsonValue(OS.osName + " x" + OS.osArchBits + " " + OS.osVersion)));
            ex(() -> value.addChild("trace", new JsonValue(parseException(exception))));
            ex(() -> value.addChild("javaVersion", new JsonValue(OS.javaVersion)));
            ex(() -> value.addChild("javaArch", new JsonValue(OS.osArchBits)));

            Log.info("Sending crash report.");

            //post to crash report URL, exit code indicates send success
            // Just kidding, dont do that because the client is at fault most of the time
//            Http.post(Vars.crashReportURL, value.toJson(OutputType.json)).error(t -> {
//                Log.info("Crash report not sent.");
//                System.exit(-1);
//            }).block(r -> {
//                Log.info("Crash sent successfully.");
//                System.exit(1);
//            });*/

            return;
        }catch(Throwable death){
            death.printStackTrace();
        }

        System.exit(1);
    }

    /** @return the mod that is likely to have caused the supplied crash */
    public static @Nullable LoadedMod getModCause(Throwable e){
        if(Vars.mods == null) return null;
        try{
            for(var element : e.getStackTrace()){
                String name = element.getClassName();
                if(!name.matches("(mindustry|arc|java|javax|sun|jdk)\\..*")){
                    for(var mod : mods.list()){
                        if(mod.meta.main != null && getMatches(mod.meta.main, name) > 0){
                            return mod;
                        }else if(element.getFileName() != null && element.getFileName().endsWith(".js") && element.getFileName().startsWith(mod.name + "/")){
                            return mod;
                        }
                    }
                }
            }
        }catch(Throwable ignored){}
        return null;
    }

    private static int getMatches(String name1, String name2){
        String[] arr1 = name1.split("\\."), arr2 = name2.split("\\.");
        int matches = 0;
        for(int i = 0; i < Math.min(arr1.length, arr2.length); i++){

            if(!arr1[i].equals(arr2[i])){
                return i;
            }else if(!arr1[i].matches("net|org|com|io")){ //ignore common domain prefixes, as that's usually not enough to call something a "match"
                matches ++;
            }
        }
        return matches;
    }

    private static String writeException(Throwable e){
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
}
