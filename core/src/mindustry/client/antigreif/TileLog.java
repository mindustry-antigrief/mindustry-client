package mindustry.client.antigreif;

import arc.struct.*;

public class TileLog {
    public Seq<TileLogItem> log = new Seq<>();

    public void addItem(TileLogItem item) {
        log.add(item);
    }
}
