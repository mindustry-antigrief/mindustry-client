package mindustry.client;

import arc.*;
import arc.input.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.ui.*;
import arc.scene.utils.*;
import arc.struct.*;
import arc.util.*;
import mindustry.entities.traits.BuilderTrait.*;
import mindustry.input.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;
import mindustry.world.*;

import static arc.Core.*;
import static mindustry.Vars.*;
import static mindustry.client.utils.Levenshtein.distanceCompletion;

public class Client{
    public static boolean transferPaused = false;

    public static void update(){
        PowerGridFinder.INSTANCE.updatePower();

        if(!transferPaused){
            boolean updateTransfer = new Rand().chance(1 / 60f);
            for(TransferItem transfer : ui.transfer.transferRequests){
                transfer.run();
                if(updateTransfer){
                    transfer.update();
                }
            }
        }

        for(int i = 0; i < 50; i += 1){
            if(configRequests.size > 0){
                configRequests.removeFirst().runRequest();
                if(configRequests.size % 10 == 0){
                    System.out.printf("%s left...%n\n", configRequests.size);
                }
                if(configRequests.size == 0){
                    System.out.println("Done!!");
                }
            }
        }

        if(following != null && following != player){
            float dx = player.x - following.x;
            float dy = player.y - following.y;
            player.moveBy(Mathf.clamp(-dx, -player.mech.maxSpeed, player.mech.maxSpeed),
            Mathf.clamp(-dy, -player.mech.maxSpeed, player.mech.maxSpeed));
            player.isShooting = following.isShooting;
            player.rotation = following.rotation;
            if(player.buildQueue() != following.buildQueue()){
                player.buildQueue().clear();
                for(BuildRequest b : following.buildQueue()){
                    if(breakingFollowing){
                        b.breaking = !b.breaking;
                    }
                    player.buildQueue().addLast(b);
                }
            }
        }

        if(input.keyDown(KeyCode.CONTROL_LEFT) && input.keyRelease(KeyCode.F)){
            FloatingDialog dialog = new FloatingDialog("find");
            dialog.addCloseButton();
            Array<Image> imgs = new Array<>();
            for(int i = 0; i < 10; i += 1){
                imgs.add(new Image());
            }
            TextField field = Elements.newField("", (string) -> {
                Array<Block> sorted = content.blocks().copy();
                sorted = sorted.sort((b) -> distanceCompletion(string, b.name));
                found = sorted.first();
                for(int i = 0; i < imgs.size - 1; i += 1){
                    Image region = new Image(sorted.get(i).icon(Cicon.large));
                    region.setSize(32);
                    imgs.get(i).setDrawable(region.getDrawable());
                }

            });
            dialog.cont.add(field);
            for(Image img : imgs){
                dialog.cont.row().add(img);
            }

            dialog.keyDown(KeyCode.ENTER, () -> {
                if(found == null){
                    dialog.hide();
                }
                Array<Tile> tiles = new Array<>();
                for(Tile[] t : world.getTiles()){
                    for(Tile tile2 : t){
                        if(tile2.block() != null){
                            if(tile2.block().name.equals(found.name) && tile2.getTeam() == player.getTeam()){
                                tiles.add(tile2);
                            }
                        }
                    }
                }
                if(tiles.size > 0){
                    float dist = Float.POSITIVE_INFINITY;
                    Tile closest = null;

                    for(Tile t : tiles){
                        float d = Mathf.dst(player.x, player.y, t.x, t.y);
                        if(d < dist){
                            closest = t;
                            dist = d;

                        }
                    }
                    if(closest != null){
                        targetPosition = new Vec2(closest.x, closest.y);
                        ui.chatfrag.addMessage(String.format("%d, %d (/go to travel there)", (int)closest.x, (int)closest.y), "client");
                        dialog.hide();
                    }
                }
            });
            dialog.show();
            findField = dialog;
            scene.setKeyboardFocus(field);
        }

        if(scene.getKeyboardFocus() == null && control.input.block == null){
            float speed = (8F / renderer.getScale()) * Time.delta();
            if(Core.input.keyDown(KeyCode.LEFT) || Core.input.keyDown(KeyCode.RIGHT) ||
            Core.input.keyDown(KeyCode.UP) || Core.input.keyDown(KeyCode.DOWN)){
                if(cameraPositionOverride == null){
                    cameraPositionOverride = new Vec2(player.x, player.y);
                }
            }

            if(Core.input.keyDown(KeyCode.RIGHT)){
                cameraPositionOverride.x += speed;
            }

            if(Core.input.keyDown(KeyCode.LEFT)){
                cameraPositionOverride.x -= speed;
            }

            if(Core.input.keyDown(KeyCode.UP)){
                cameraPositionOverride.y += speed;
            }

            if(Core.input.keyDown(KeyCode.DOWN)){
                cameraPositionOverride.y -= speed;
            }
            if(Core.input.keyDown(Binding.zoom_in)){
                renderer.scaleCamera(0.5f);
            }
            if(Core.input.keyDown(Binding.zoom_out)){
                renderer.scaleCamera(-0.5f);
            }
            if(input.keyTap(Binding.xray_toggle)){
                xray = !xray;
//                renderer.blocks.refreshShadows();
            }
        }


    }
}
