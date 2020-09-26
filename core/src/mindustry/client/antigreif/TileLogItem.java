package mindustry.client.antigreif;

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
    public TileLogItem(Player player, Tile tile, long time, String additionalInfo) {
        this.additionalInfo = additionalInfo;
        this.player = player.name;
        this.time = time;
        x = tile.x;
        y = tile.y;
    }

    protected String formatDate(String date, long minutes) {
        return String.format("%s interacted with tile at %d,%d at %s UTC (%d minutes ago).  %s", player, x, y, date, minutes, additionalInfo);
    }

    public String format() {
        Instant instant = Instant.ofEpochSecond(time);
        TimeZone timezone = TimeZone.getTimeZone("UTC");
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd  HH:mm:ss");
        format.setTimeZone(timezone);
        String formatted = format.format(Date.from(instant));

        Duration duration = Duration.between(instant, Instant.now());
        long minutes = duration.get(ChronoUnit.MINUTES);

        return formatDate(formatted, minutes);
    }
}
