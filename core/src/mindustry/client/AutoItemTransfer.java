package mindustry.client;

import arc.math.*;

import static mindustry.Vars.ui;

public class AutoItemTransfer{

    public static int defaultTilePos = 0;
    public static boolean transferPaused = false;

    public static void runTransfers(){
        if(!AutoItemTransfer.transferPaused){
            boolean updateTransfer = new Rand().chance(1 / 60f);
            for(TransferItem transfer : ui.transfer.transferRequests){
                transfer.run();
                if(updateTransfer){
                    transfer.update();
                }
            }
        }
    }
}
