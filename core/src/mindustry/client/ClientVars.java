package mindustry.client;

import arc.*;
import arc.graphics.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.client.antigrief.*;
import mindustry.client.utils.*;
import mindustry.game.EventType.*;
import mindustry.world.blocks.defense.*;

import java.util.Locale;

import org.jetbrains.annotations.*;

public class ClientVars {
    // Misc
    public static short silentTrace; // How many traces to do silently (this is pretty 0head but shh)
    public static IntMap<Object> processorConfigs = new IntMap<>();
    public static float spawnTime = 60f * Core.settings.getInt("spawntime");
    public static float travelTime = Core.settings.getInt("traveltime");

    // Config Queue
    @NotNull public static Queue<ConfigRequest> configs = new Queue<>();
    @NotNull public static Ratekeeper configRateLimit = new Ratekeeper();

    // Hotkeys
    public static boolean showingTurrets, hidingUnits, hidingAirUnits, hidingBlocks, dispatchingBuildPlans, showingOverdrives;
    @NotNull public static Seq<OverdriveProjector.OverdriveBuild> overdrives = new Seq<>(); // For whatever reason the stupid allBuildings method hates me so im just not using it

    // Commands
    @NotNull public static CommandHandler clientCommandHandler = new CommandHandler("!");
    @NotNull public static Vec2 lastSentPos = new Vec2(), coreWarnPos = new Vec2();
    public static final String MESSAGE_BLOCK_PREFIX = "IN USE FOR CHAT AUTHENTICATION, do not use";
    public static long lastJoinTime; // Last time we connected to a server
    public static boolean syncing; // Whether we are in the process of reloading the world

    // Cursor Position
    public static final byte FOO_USER = (byte) 0b10101010, ASSISTING = (byte) 0b01010101;

    // Networking
    @NotNull public static Color encrypted = Color.valueOf("#243266"), verified = Color.valueOf("#2c9e52"), invalid = Color.valueOf("#890800"), user = Color.coral.cpy().mul(0.6f); // Encrypted = Blue, Verified = Green
    @NotNull public static String lastCertName = "";

    // Translating
    public static String targetLang = Locale.getDefault().getLanguage(); // Language to translate messages to
    public static Seq<String> supportedLangs = new Seq<>(); // Languages supported by LibreTranslate
    public static boolean enableTranslation = Core.settings.getBool("enabletranslation", true);

    static {
        Events.on(ClientLoadEvent.class, e -> Translating.languages(langs -> {
            supportedLangs = langs;
            targetLang = langs.contains(targetLang) ? targetLang : "en";
        }));
    }
}
