package mindustry;

import arc.Events;
import arc.func.Cons;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.math.geom.Circle;
import arc.util.Log;
import mindustry.core.World;
import mindustry.entities.units.UnitCommand;
import mindustry.game.EventType.*;
import mindustry.gen.BlockUnitUnit;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.graphics.Layer;
import mindustry.world.Tile;
import mindustry.world.blocks.sandbox.ItemSource;

public class CustomScripts {
    public void set(boolean f) {}

    public static class onCommandCenterChange extends CustomScripts {
        static final Class<CommandBeforeAfterEvent> listening = CommandBeforeAfterEvent.class;
        private Cons<CommandBeforeAfterEvent> onChange;
        public boolean enabled = false;

        onCommandCenterChange() {
            init();
            Events.on(listening, onChange); // cannot be accessed from within js console
        }

        @Override
        public void set(boolean f){
            enabled = f;
        }
        public void remove() {
            Events.on(listening, onChange);
        }

        private void init() {
            onChange = e -> {
                Thread t = new Thread(() -> {
                    try{
                        Thread.sleep(50);
                    } catch (InterruptedException ignored){}
                    if (!enabled) return;
                    if (e.command_aft != UnitCommand.attack) return;
                    Call.tileConfig(Vars.player, e.tile, e.command_bef);
                }); t.start();
            };
        }
    }


}
