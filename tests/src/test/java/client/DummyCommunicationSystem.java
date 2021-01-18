package client;

import com.github.blahblahbloopster.crypto.CommunicationSystem;
import kotlin.Unit;
import kotlin.jvm.functions.Function2;
import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** A dummy {@link CommunicationSystem} for tests. */
public class DummyCommunicationSystem implements CommunicationSystem {
    private final List<Function2<byte[], Integer, Unit>> listeners = new ArrayList<>();
    private final int id = new Random().nextInt();
    private static final ArrayList<DummyCommunicationSystem> systems = new ArrayList<>();

    {
        systems.add(this);
    }

    @NotNull
    @Override
    public List<Function2<byte[], Integer, Unit>> getListeners() {
        return listeners;
    }

    @Override
    public int getId() {
        return id;
    }

    private void received(byte[] bytes, int sender) {
        listeners.forEach(item -> item.invoke(bytes, sender));
    }

    @Override
    public void send(@NotNull byte[] bytes) {
        systems.forEach(item -> {
            if (item != this) {
                item.received(bytes, id);
            }
        });
    }

    @Override
    public void init() {}
}
