package mindustry.client.antigrief;

import arc.scene.*;
import arc.scene.ui.*;
import arc.util.*;
import mindustry.ai.types.*;
import mindustry.content.*;
import mindustry.gen.*;
import mindustry.world.*;

import java.text.*;
import java.time.*;
import java.time.temporal.*;
import java.util.*;

public class TileLogItem {
    public String additionalInfo;
    String action;
    Block block;
    public String player;
    public long time;
    public int x, y;

    /**
     * Creates a TileLogItem.  time is unix time.
     */
    public TileLogItem(Unitc player, Tile tile, long time, String additionalInfo, String action, Block block) {
        this.block = block;
        this.action = action;
        this.additionalInfo = additionalInfo;
        if (player instanceof LogicAI ai) this.additionalInfo += " " + Blocks.microProcessor.emoji() + " (" + (ai.controller != null ? ai.controller.tileX() + ", " + ai.controller.tileY() : "?, ?") + ")";
        this.player = Strings.stripColors(player.isPlayer() ? player.getPlayer().name : player instanceof FormationAI ai && ai.leader.isPlayer() ? ai.leader.getPlayer().name : player.type() == null ? "" : player.type().name);
        this.time = time;
        x = tile.x;
        y = tile.y;
    }

    protected String formatDate(String date, long minutes) {
        return Strings.format("@@ @ at @ UTC (@ minutes ago). @[white]", player.equals("") ? "" : player + " ", action, block.localizedName, date, minutes, additionalInfo);
    }

    protected String formatConcise(String date, long minutes) {
        return Strings.format("@@ @ (@m)[white]", player.equals("") ? "" : player + " ", action, block.localizedName, minutes);
    }

    public String format() {
        Instant instant = Instant.ofEpochSecond(time);
        TimeZone timezone = TimeZone.getTimeZone("UTC");
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd  HH:mm:ss");
        format.setTimeZone(timezone);
        String formatted = format.format(Date.from(instant));

        Duration duration = Duration.between(instant, Instant.now());
        long minutes = duration.get(ChronoUnit.SECONDS) / 60L;

        return formatDate(formatted, minutes);
    }

    public String formatShort() {
        Instant instant = Instant.ofEpochSecond(time);
        TimeZone timezone = TimeZone.getTimeZone("UTC");
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd  HH:mm:ss");
        format.setTimeZone(timezone);
        String formatted = format.format(Date.from(instant));

        Duration duration = Duration.between(instant, Instant.now());
        long minutes = duration.getSeconds() / 60L;

        return formatConcise(formatted, minutes);
    }

    public Element toElement() {
        return new Label(format());
    }

    public static String toCardinalDirection(int rotation) {
        return switch (rotation) {
            case 0 -> "east";
            case 1 -> "north";
            case 2 -> "west";
            case 3 -> "south";
            default -> String.valueOf(rotation);
        };
    }
}
