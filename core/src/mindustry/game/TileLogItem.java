package mindustry.game;

public class TileLogItem{
    public TileLogType type;
    public String playerName;

    public TileLogItem(TileLogType type, String playerName){
        this.type = type;
        this.playerName = playerName;
    }

    @Override
    public String toString(){
        if(type == TileLogType.Broken){
            return String.format("Broken by %s", playerName);
        }else if(type == TileLogType.Placed){
            return String.format("Placed by %s", playerName);
        }else{
            return String.format("Configured by %s", playerName);
        }
    }
}
