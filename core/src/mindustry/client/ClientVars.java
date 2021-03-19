package mindustry.client;

import arc.math.geom.Vec2;
import arc.struct.Queue;
import arc.util.*;
import mindustry.client.antigrief.ConfigRequest;
import org.jetbrains.annotations.NotNull;

public class ClientVars {

    @NotNull public static ClientMode mode = ClientMode.normal;
    @NotNull public static Queue<ConfigRequest> configs = new Queue<>();
    public static boolean showingTurrets, hidingUnits, hidingBlocks, dispatchingBuildPlans;
    public static long lastSyncTime;
    @NotNull public static CommandHandler clientCommandHandler = new CommandHandler("!");
    @NotNull public static Ratekeeper configRateLimit = new Ratekeeper();
    @NotNull public static Vec2 lastSentPos = new Vec2();
    @NotNull public static final String MESSAGE_BLOCK_PREFIX = "IN USE FOR CHAT AUTHENTICATION, do not use";
    public static final byte FOO_USER = (byte) 0b10101010;
    public static final byte ASSISTING = (byte) 0b01010101;
}
