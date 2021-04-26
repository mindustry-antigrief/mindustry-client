package mindustry.client;

import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.client.antigrief.*;
import org.jetbrains.annotations.*;

public class ClientVars {
    // Misc
    @NotNull public static ClientMode mode = ClientMode.normal;

    // Config Queue
    @NotNull public static Queue<ConfigRequest> configs = new Queue<>();
    @NotNull public static Ratekeeper configRateLimit = new Ratekeeper();

    // Hotkeys
    public static boolean showingTurrets, hidingUnits, hidingBlocks, dispatchingBuildPlans;

    // Commands
    @NotNull public static CommandHandler clientCommandHandler = new CommandHandler("!");
    @NotNull public static Vec2 lastSentPos = new Vec2();
    public static final String MESSAGE_BLOCK_PREFIX = "IN USE FOR CHAT AUTHENTICATION, do not use";
    public static long lastJoinTime;
    public static boolean syncing; // Whether we are in the process of reloading the world
    public static boolean signMessages = true; // Whether or not to sign outbound messages (toggle green highlight)

    // Cursor Position
    public static final byte FOO_USER = (byte) 0b10101010, ASSISTING = (byte) 0b01010101;
}
