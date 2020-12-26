package mindustry.client.antigreif;

import arc.scene.*;
import arc.scene.ui.*;
import mindustry.gen.*;
import mindustry.world.*;
import java.text.*;
import java.time.*;
import java.time.temporal.*;
import java.util.*;

public class TileLogItem {
    public String additionalInfo;
    public String player;
    public long time;
    public int x, y;

    /** Creates a TileLogItem.  time is unix time. */
    public TileLogItem(Unitc player, Tile tile, long time, String additionalInfo) {
        this.additionalInfo = additionalInfo;
        this.player = player.isPlayer()? player.getPlayer().name + "[white]" : (player.type() == null? "Null unit" : player.type().name);
        this.time = time;
        x = tile.x;
        y = tile.y;
    }

    protected String formatDate(String date, long minutes) {
        return String.format("%s interacted with tile at %s UTC (%d minutes ago).  %s", player, date, minutes, additionalInfo);
    }

    protected String formatConcise(String date, long minutes) {
        return String.format("%s interacted %d minutes ago", player, minutes);
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
        long minutes = duration.get(ChronoUnit.SECONDS) / 60L;

        return formatConcise(formatted, minutes);
    }

    public Element toElement() {
        return new Label(format());
    }
}
