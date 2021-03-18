package mindustry.client;

import arc.math.geom.Vec2;
import arc.struct.Queue;
import arc.util.*;
import mindustry.client.antigrief.ConfigRequest;
import org.jetbrains.annotations.NotNull;

public interface ClientVars {

    @NotNull ClientMode getMode();
    void setMode(@NotNull ClientMode mode);

    Queue<ConfigRequest> getConfigs();

    boolean getShowingTurrets();
    void setShowingTurrets(boolean showingTurrets);

    boolean getHideUnits();
    void setHideUnits(boolean hideUnits);

    boolean getHidingBlocks();
    void setHidingBlocks(boolean hidingBlocks);

    boolean getDispatchingBuildPlans();
    void setDispatchingBuildPlans(boolean dispatchingBuildPlans);

    long getLastSyncTime();
    void setLastSyncTime(long lastSyncTime);

    CommandHandler getFooCommands();

    Ratekeeper getConfigRateLimit();

    Vec2 getLastSentPos();

    String getMessageBlockCommunicationPrefix();

    ClientInterface getMapping();

    byte getFooUser();

    byte getAssisting();
}
