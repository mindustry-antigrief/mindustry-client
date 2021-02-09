package mindustry.client;

import arc.math.geom.Vec2;
import mindustry.gen.Player;

/**
 * This is an interface implemented by the client module in kotlin.
 * It serves as the mapping between the java and kotlin modules.
 * If something from core needs to get something from client, add it to the interface and implement it in the client module.
 */
public interface ClientInterface {

    void showFindDialog();

    void showChangelogDialog();

    void showFeaturesDialog();

    void setAssistPath(Player player);

    Vec2 floatEmbed();

    void generateKey();

    void shareKey();
}
