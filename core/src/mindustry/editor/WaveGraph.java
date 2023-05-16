package mindustry.editor;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import arc.util.pooling.*;
import mindustry.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.*;

public class WaveGraph extends Table{
    public Seq<SpawnGroup> groups = new Seq<>();
    public int from = 0, to = 20;

    private Mode mode = Mode.counts;
    public WaveData[] waveData;
    private OrderedSet<UnitType> used = new OrderedSet<>();
    private int maxCount, maxTotalCount;
    private float maxHealth;
    private float maxDps;
    private Table colors;
    private ObjectSet<UnitType> hidden = new ObjectSet<>();

    public WaveGraph(){
        background(Tex.pane);

        rect((x, y, width, height) -> {
            Lines.stroke(Scl.scl(3f));

            GlyphLayout lay = Pools.obtain(GlyphLayout.class, GlyphLayout::new);
            Font font = Fonts.outline;

            lay.setText(font, "1");

            int maxY = switch(mode){
                case counts -> nextStep(maxCount);
                case health -> nextStep((int)maxHealth);
                case totals -> nextStep(maxTotalCount);
                case dps    -> nextStep((int)maxDps);
            };

            float fh = lay.height;
            float offsetX = Scl.scl(lay.width * (maxY + "").length() * 2), offsetY = Scl.scl(22f) + fh + Scl.scl(5f);

            float graphX = x + offsetX, graphY = y + offsetY, graphW = width - offsetX, graphH = height - offsetY;
            float spacing = graphW / (waveData.length - 1);

            if(mode == Mode.counts){
                for(UnitType type : used.orderedItems()){
                    if(hidden.contains(type)) continue;
                    Draw.color(color(type));
                    Draw.alpha(parentAlpha);

                    Lines.beginLine();

                    for(int i = 0; i < waveData.length; i++){
                        float cx = graphX + i * spacing, cy = graphY + waveData[i].counts[type.id] * graphH / maxY;
                        Lines.linePoint(cx, cy);
                    }

                    Lines.endLine();
                }
            }else if(mode == Mode.totals){
                Lines.beginLine();

                Draw.color(Pal.accent);
                for(int i = 0; i < waveData.length; i++){
                    float cx = graphX + i * spacing, cy = graphY + waveData[i].totalCount * graphH / maxY;
                    Lines.linePoint(cx, cy);
                }

                Lines.endLine();
            }else if(mode == Mode.health){
                Lines.beginLine();

                Draw.color(Pal.health);
                for(int i = 0; i < waveData.length; i++){
                    float cx = graphX + i * spacing, cy = graphY + waveData[i].totalHealth * graphH / maxY;
                    Lines.linePoint(cx, cy);
                }

                Lines.endLine();
            }else if(mode == Mode.dps){
                Lines.beginLine();

                Draw.color(Pal.spore);
                for(int i = 0; i < waveData.length; i++){
                    float cx = graphX + i * spacing, cy = graphY + waveData[i].totalDps * graphH / maxY;
                    Lines.linePoint(cx, cy);
                }

                Lines.endLine();
            }

            //how many numbers can fit here
            float totalMarks = Mathf.clamp(maxY, 1, 10);

            int markSpace = Math.max(1, Mathf.ceil(maxY / totalMarks));

            Draw.color(Color.lightGray);
            Draw.alpha(0.1f);

            for(int i = 0; i < maxY; i += markSpace){
                float cy = graphY + i * graphH / maxY, cx = graphX;

                Lines.line(cx, cy, cx + graphW, cy);

                lay.setText(font, "" + i);

                font.draw("" + i, cx, cy + lay.height / 2f, Align.right);
            }
            Draw.alpha(1f);

            float len = Scl.scl(4f);
            font.setColor(Color.lightGray);

            for(int i = 0; i < waveData.length; i++){
                float cy = y + fh, cx = graphX + graphW / (waveData.length - 1) * i;

                Lines.line(cx, cy, cx, cy + len);
                if(i == waveData.length / 2){
                    font.draw("" + (i + from + 1), cx, cy - Scl.scl(2f), Align.center);
                }
            }
            font.setColor(Color.white);

            Pools.free(lay);

            Draw.reset();
        }).pad(4).padBottom(10).grow();

        row();

        table(t -> colors = t).growX();

        row();

        table(t -> {
            t.left();
            ButtonGroup<Button> group = new ButtonGroup<>();

            for(Mode m : Mode.all){
                t.button("@wavemode." + m.name(), Styles.fullTogglet, () -> {
                    mode = m;
                }).group(group).height(35f).update(b -> b.setChecked(m == mode)).width(130f);
            }
        }).growX();
    }

    public void rebuild(){
        waveData = new WaveData[to - from + 1];
        used.clear();
        maxCount = maxTotalCount = 1;
        maxHealth = 1f;
        maxDps = 1f;

        for(int i = from; i <= to; i++){
            int index = i - from;
            waveData[index] = new WaveData();
            for(SpawnGroup spawn : groups){
                int spawned = spawn.getSpawned(i);
                if(spawned > 0){
                    used.add(spawn.type);
                }
                if(hidden.contains(spawn.type)) continue;
                waveData[index].counts[spawn.type.id] += spawned;
                waveData[index].totalHealth += spawned * (spawn.type.health + spawn.getShield(i));
                waveData[index].totalDps += spawned * spawn.type.dpsEstimate;

                waveData[index].totalCount += spawned;
            }
            for(UnitType type : used){
                if(!hidden.contains(type)) maxCount = Math.max(maxCount, waveData[index].counts[type.id]);
            }
            maxDps = Math.max(maxDps, waveData[index].totalDps);
            maxTotalCount = Math.max(maxTotalCount, waveData[index].totalCount);
            maxHealth = Math.max(maxHealth, waveData[index].totalHealth);
        }

        colors.clear();
        colors.left();
        colors.button("@waves.units.hide", Styles.flatt, () -> {
            if(hidden.size == used.size){
                hidden.clear();
            }else{
                hidden.addAll(used);
            }
            rebuild();
        }).update(b -> b.setText(hidden.size == used.size ? "@waves.units.show" : "@waves.units.hide")).height(32f).width(130f);
        colors.pane(t -> {
            t.left();
            for(UnitType type : used){
                t.button(b -> {
                    Color tcolor = color(type).cpy();
                    b.image().size(32f).update(i -> i.setColor(b.isChecked() ? Tmp.c1.set(tcolor).mul(0.5f) : tcolor)).get().act(1);
                    b.image(type.uiIcon).size(32f).padRight(20).update(i -> i.setColor(b.isChecked() ? Color.gray : Color.white)).get().act(1);
                    b.margin(0f);
                }, Styles.fullTogglet, () -> {
                    if(!hidden.add(type)){
                        hidden.remove(type);
                    }

                    rebuild();
                }).update(b -> b.setChecked(hidden.contains(type)));
            }
        }).scrollY(false);

    }

    Color color(UnitType type){
        return Tmp.c1.fromHsv(type.id / (float)Vars.content.units().size * 360f, 0.7f, 1f);
    }

    int nextStep(float value){
        int order = 1;
        while(order < value){
            if(order * 2 > value){
                return order * 2;
            }
            if(order * 5 > value){
                return order * 5;
            }
            if(order * 10 > value){
                return order * 10;
            }
            order *= 10;
        }
        return order;
    }

    enum Mode{
        counts, totals, health, dps;

        static Mode[] all = values();
    }

    public static class WaveData {
        /** Mapping from unit ID to count. */
        public int[] counts = new int[Vars.content.units().size];
        /** Total number of units this wave. */
        public int totalCount = 0;
        /** Total health this wave. */
        public int totalHealth = 0;
        /** Total DPS this wave. */
        public int totalDps = 0;

    }
}
