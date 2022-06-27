package mindustry.client;

import arc.*;
import arc.func.*;
import arc.graphics.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import kotlin.*;
import mindustry.net.*;
import mindustry.world.blocks.defense.*;
import mindustry.world.modules.*;
import org.jetbrains.annotations.*;

import java.util.concurrent.*;

public class ClientVars {
    // Misc
    public static byte silentTrace; // How many traces to do silently (this is pretty 0head but shh)
    public static float spawnTime = 60f * Core.settings.getInt("spawntime");
    public static float travelTime = Core.settings.getInt("traveltime");
    public static float jpegQuality = Core.settings.getFloat("commpicquality", 0.5f);
    public static boolean benchmarkNav = false;
    public final static Rect cameraBounds = new Rect();

    // Core Item Display
    public static ItemModule coreItems;

    // Config Queue
    @NotNull public static LinkedBlockingQueue<Runnable> configs = new LinkedBlockingQueue<>(); // Thread safe just in case, contains mostly instances of ConfigRequest.
    public static int ratelimitRemaining = Administration.Config.interactRateLimit.num() - 1; // Number of configs that can be made safely before ratelimit reset

    // Hotkeys
    public static boolean showingTurrets, hidingUnits, hidingAirUnits, hidingBlocks, dispatchingBuildPlans, showingOverdrives;
    @NotNull public static Seq<OverdriveProjector.OverdriveBuild> overdrives = new Seq<>(); // For whatever reason the stupid allBuildings method hates me so im just not using it FINISHME: Replace this by just expanding block clipsize and drawing a circle in the draw method rather than using this

    // Commands
    @NotNull public static CommandHandler clientCommandHandler = new CommandHandler("!");
    @NotNull public static final ObjectMap<String, Seq<Pair<String, Prov<String>>>> containsCommandHandler = new ObjectMap<>(); // Currently a naive implementation which just replaces all occurrences
    @NotNull public static Vec2 lastSentPos = new Vec2(), lastCorePos = new Vec2();
    public static final String MESSAGE_BLOCK_PREFIX = "IN USE FOR CHAT AUTHENTICATION, do not use";
    public static long lastJoinTime; // Last time we connected to a server
    public static boolean syncing; // Whether we are in the process of reloading the world
    public static double lastServerStartTime;
    public static String lastServerName;

    // Networking
    public static final byte FOO_USER = (byte) 0b10101010, ASSISTING = (byte) 0b01010101;
    @NotNull public static Color encrypted = Color.valueOf("#243266"), verified = Color.valueOf("#2c9e52"), invalid = Color.valueOf("#890800"), user = Color.coral.cpy().mul(0.6f); // Encrypted = Blue, Verified = Green
    @NotNull public static String lastCertName = "";
    public static boolean isBuildingLock = false; // whether or not the building state is being controlled by networking
    public static int pluginVersion;
    public static boolean useNew = true;
}
