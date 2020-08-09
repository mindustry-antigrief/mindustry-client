package mindustry.game;

public class TileLogItem{
    public TileLogType type;
    public String playerName;
    public String info = "";

    public TileLogItem(TileLogType type, String playerName){
        new TileLogItem(type, playerName, "");
    }

    public TileLogItem(TileLogType type, String playerName, String info){
        this.type = type;
        this.playerName = playerName;
        this.info = info;
    }

    @Override
    public String toString(){
        if(type == TileLogType.Broken){
            return String.format("%s broke %s", playerName, info);
        }else if(type == TileLogType.Placed){
            return String.format("%s placed %s", playerName, info);
        }else{
            return String.format("%s configured %s", playerName, info);
        }
    }
}
