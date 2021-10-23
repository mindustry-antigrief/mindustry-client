package mindustry.logic;

import arc.*;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.input.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.logic.LStatements.*;
import mindustry.ui.*;
import org.jetbrains.annotations.NotNull;

public class LCanvas extends Table{
    public static final int maxJumpsDrawn = 100;
    //ew static variables
    static LCanvas canvas;

    public DragLayout statements;
    public ScrollPane pane;
    public Group jumps;
    StatementElem dragging;
    StatementElem hovered;
    float targetWidth;
    int jumpCount = 0;
    Seq<Tooltip> tooltips = new Seq<>();

    public LCanvas(){
        canvas = this;

        Core.scene.addListener(new InputListener(){
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
                //hide tooltips on tap
                for(var t : tooltips){
                    t.container.toFront();
                }
                Core.app.post(() -> {
                    tooltips.each(Tooltip::hide);
                    tooltips.clear();
                });
                return super.touchDown(event, x, y, pointer, button);
            }
        });

        rebuild();
    }

    /** @return if statement elements should have rows. */
    public static boolean useRows(){
        return Core.graphics.getWidth() < Scl.scl(900f) * 1.2f;
    }

    public static void tooltip(Cell<?> cell, String key){
        String lkey = key.toLowerCase().replace(" ", "");
        if(Core.settings.getBool("logichints", true) && Core.bundle.has(lkey)){
            var tip = new Tooltip(t -> t.background(Styles.black8).margin(4f).add("[lightgray]" + Core.bundle.get(lkey)).style(Styles.outlineLabel));

            //mobile devices need long-press tooltips
            if(Vars.mobile){
                cell.get().addListener(new ElementGestureListener(20, 0.4f, 0.43f, 0.15f){
                    @Override
                    public boolean longPress(Element element, float x, float y){
                        tip.show(element, x, y);
                        canvas.tooltips.add(tip);
                        //prevent touch down for other listeners
                        for(var list : cell.get().getListeners()){
                            if(list instanceof ClickListener cl){
                                cl.cancel();
                            }
                        }
                        return true;
                    }
                });
            }else{
                cell.get().addListener(tip);
            }

        }
    }

    public static void tooltip(Cell<?> cell, Enum<?> key){
        String cl = key.getClass().getSimpleName().toLowerCase() + "." + key.name().toLowerCase();
        if(Core.bundle.has(cl)){
            tooltip(cell, cl);
        }else{
            tooltip(cell, "lenum." + key.name());
        }
    }

    public void rebuild(){
        targetWidth = useRows() ? 400f : 900f;
        float s = pane != null ? pane.getScrollPercentY() : 0f;
        String toLoad = statements != null ? save() : null;

        clear();

        statements = new DragLayout();
        jumps = new WidgetGroup();

        pane = pane(t -> {
            t.center();
            t.add(statements).pad(2f).center().width(targetWidth);
            t.addChild(jumps);

            jumps.cullable = false;
        }).grow().get();
        pane.setFlickScroll(false);
        recalculate();

        //load old scroll percent
        Core.app.post(() -> {
            pane.setScrollPercentY(s);
            pane.updateVisualScroll();
        });

        if(toLoad != null){
            load(toLoad);
        }
    }
    public Seq<JumpSegment> jumpSegmentSeq = new Seq<>();
    public Seq<JumpHeight> jumpHeightSeq = new Seq<>();
    public void recalculate(){
        var temp = statements.seq.<StatementElem>as();
        for(int i=0; i < temp.size; i++)
            if(temp.get(i).st instanceof JumpStatement js)
                js.saveUI();
        JumpSegment.resetAll();
        for(var s: temp){
            if(s == dragging || !(s.st instanceof JumpStatement js)) continue;
            if(js.destIndex == -1) continue; // empty jumps a
            if(JumpSegment.isCreated(js.destIndex)) JumpSegment.allMap.get(js.destIndex).add(s.index);
            else jumpSegmentSeq.add(new JumpSegment(js.destIndex, s.index));
            JumpSegment.allMap.get(js.destIndex).add(js.destIndex);
        }
        jumpSegmentSeq.sort();
        JumpSegment jtemp;
        while(jumpSegmentSeq.size > 0 && (jtemp = jumpSegmentSeq.get(jumpSegmentSeq.size - 1)).span() == JumpSegment.MAX_SPAN){
            JumpSegment.allMap.remove(jtemp.dest);
            jumpSegmentSeq.remove(jumpSegmentSeq.size - 1);
        }
        jumpHeightSeq.forEach(JumpHeight::reset);
        while(jumpHeightSeq.size <= JumpSegment.global_max) jumpHeightSeq.add(new JumpHeight());
        jumpSegmentSeq.forEach(v -> {
            long mask = Long.MAX_VALUE;
            int maxHeight;
            for(int i=v.min; i <= v.max; i++){
                mask &= jumpHeightSeq.get(i).available_mask;
            }
            maxHeight = Mathf.round(Mathf.log(2, mask & -mask));
            for(int i=v.min; i <= v.max; i++){
                jumpHeightSeq.get(i).take(maxHeight);
            }
            v.height = maxHeight;
        });
    }

    @Override
    public void draw(){
        jumpCount = 0;
        super.draw();
    }

    public void add(LStatement statement){
        statements.addChild(new StatementElem(statement));
    }

    public void addAt(int at, LStatement statement){
        statements.addChildAt(at, new StatementElem(statement));
    }

    public String save(){
        Seq<LStatement> st = statements.getChildren().<StatementElem>as().map(s -> s.st);
        st.each(LStatement::saveUI);

        return LAssembler.write(st);
    }

    public void load(String asm){
        jumps.clear();

        Seq<LStatement> statements = LAssembler.read(asm);
        statements.truncate(LExecutor.maxInstructions);
        this.statements.clearChildren();
        for(LStatement st : statements){
            add(st);
        }

        for(LStatement st : statements){
            st.setupUI();
        }

        this.statements.layout();
        recalculate();
    }

    StatementElem checkHovered(){
        Element e = Core.scene.hit(Core.input.mouseX(), Core.input.mouseY(), true);
        if(e != null){
            while(e != null && !(e instanceof StatementElem)){
                e = e.parent;
            }
        }
        if(e == null || isDescendantOf(e)) return null;
        return (StatementElem)e;
    }

    @Override
    public void act(float delta){
        super.act(delta);

        hovered = checkHovered();

        if(Core.input.isTouched()){
            float y = Core.input.mouseY();
            float dst = Math.min(y - this.y, Core.graphics.getHeight() - y);
            if(dst < Scl.scl(100f)){ //scroll margin
                int sign = Mathf.sign(Core.graphics.getHeight()/2f - y);
                pane.setScrollY(pane.getScrollY() + sign * Scl.scl(15f) * Time.delta);
            }
        }
    }

    public class JumpHeight{
        long available_mask = Long.MAX_VALUE;
        public static int maxHeight = 0;

        public void take(int loc){
            available_mask ^= (1L << loc);
            maxHeight = Math.max(maxHeight, loc);
        }
        public void reset(){
            available_mask = Long.MAX_VALUE;
            maxHeight = 0;
        }
    }
    public class JumpSegment implements Comparable{
        int min = 2147483646, max = -1, dest, height;
        public static int global_max = 0;
        public final static int MAX_SPAN = -2147483647;
        public static IntMap<JumpSegment> allMap = new IntMap<>();

        public JumpSegment(int dest, int loc){
            this.dest = dest;
            if(!allMap.containsKey(dest)) allMap.put(dest, this);
            add(loc);
        }
        public void add(int loc){
            min = loc < min ? loc : min;
            max = loc > max ? loc : max;
            global_max = loc > global_max ? loc : global_max;
        }
        public void reset(){
            min = 2147483646;
            max = -1;
            height = 0;
        }
        public int span(){
            return max - min;
        }
        public static boolean isCreated(int loc){
            return allMap.containsKey(loc);
        }
        public static void resetAll(){
            allMap.values().forEach(JumpSegment::reset);
            global_max = 0;
        }
        @Override
        public int compareTo(@NotNull Object o) {
            if(!(o instanceof JumpSegment e)) return 0;
            return e.span() - this.span(); // larger lengths go to the front
        }
    }
    public class DragLayout extends WidgetGroup{
        float space = Scl.scl(10f), prefWidth, prefHeight;
        Seq<Element> seq = new Seq<>();
        int insertPosition = 0;
        boolean invalidated;

        {
            setTransform(true);
        }

        @Override
        public void layout(){
            invalidated = true;
            float cy = 0;
            seq.clear();

            float totalHeight = getChildren().sumf(e -> e.getHeight() + space);

            height = prefHeight = totalHeight;
            width = prefWidth = Scl.scl(targetWidth);

            //layout everything normally
            for(int i = 0; i < getChildren().size; i++){
                Element e = getChildren().get(i);

                //ignore the dragged element
                if(dragging == e) continue;

                e.setSize(width, e.getPrefHeight());
                e.setPosition(0, height - cy, Align.topLeft);
                ((StatementElem)e).updateAddress(i);

                cy += e.getPrefHeight() + space;
                seq.add(e);
            }

            //insert the dragged element if necessary
            if(dragging != null){
                //find real position of dragged element top
                float realY = dragging.getY(Align.top) + dragging.translation.y;

                insertPosition = 0;

                for(int i = 0; i < seq.size; i++){
                    Element cur = seq.get(i);
                    //find fit point
                    if(realY < cur.y && (i == seq.size - 1 || realY > seq.get(i + 1).y)){
                        insertPosition = i + 1;
                        break;
                    }
                }

                float shiftAmount = dragging.getHeight() + space;

                //shift elements below insertion point down
                for(int i = insertPosition; i < seq.size; i++){
                    seq.get(i).y -= shiftAmount;
                }
            }

            invalidateHierarchy();

            if(parent != null && parent instanceof Table){
                setCullingArea(parent.getCullingArea());
            }
        }

        @Override
        public float getPrefWidth(){
            return prefWidth;
        }

        @Override
        public float getPrefHeight(){
            return prefHeight;
        }

        @Override
        public void draw(){
            Draw.alpha(parentAlpha);

            //draw selection box indicating placement position
            if(dragging != null && insertPosition <= seq.size){
                float shiftAmount = dragging.getHeight();
                float lastX = x;
                float lastY = insertPosition == 0 ? height + y : seq.get(insertPosition - 1).y + y - space;

                Tex.pane.draw(lastX, lastY - shiftAmount, width, dragging.getHeight());
            }

            if(invalidated){
                children.each(c -> c.cullable = false);
            }

            super.draw();

            if(invalidated){
                children.each(c -> c.cullable = true);
                invalidated = false;
            }
        }

        void finishLayout(){
            if(dragging != null){
                //reset translation first
                for(Element child : getChildren()){
                    child.setTranslation(0, 0);
                }
                clearChildren();

                //reorder things
                for(int i = 0; i <= insertPosition - 1 && i < seq.size; i++){
                    addChild(seq.get(i));
                }

                addChild(dragging);

                for(int i = insertPosition; i < seq.size; i++){
                    addChild(seq.get(i));
                }

                dragging = null;
            }

            layout();
        }
    }

    public class StatementElem extends Table{
        public LStatement st;
        public int index;
        Label addressLabel;

        public StatementElem(LStatement st){
            this.st = st;
            st.elem = this;

            background(Tex.whitePane);
            setColor(st.color());
            margin(0f);
            touchable = Touchable.enabled;

            table(Tex.whiteui, t -> {
                t.color.set(color);
                t.addListener(new HandCursorListener());

                t.margin(6f);
                t.touchable = Touchable.enabled;

                t.add(st.name()).style(Styles.outlineLabel).name("statement-name").color(color).padRight(8);
                t.add().growX();

                addressLabel = t.add(index + "").style(Styles.outlineLabel).color(color).padRight(8).get();

                t.button(Icon.add, Styles.logici, () -> Vars.ui.logic.addDialog(statements.insertPosition + 1)).tooltip("Add Here")
                    .disabled(b -> canvas.statements.getChildren().size >= LExecutor.maxInstructions).size(24f).padRight(6);

                t.button(Icon.copy, Styles.logici, () -> {
                }).size(24f).padRight(6).get().tapped(this::copy);

                t.button(Icon.paste, Styles.logici, () -> {
                }).size(24f).padRight(6).tooltip("Paste Here").get().tapped(() -> {
                    this.paste(LAssembler.read(Core.app.getClipboardText().replace("\r\n", "\n")));
                });

                t.button(Icon.cancel, Styles.logici, () -> {
                    remove();
                    dragging = null;
                    statements.layout();
                    recalculate();
                }).size(24f);

                t.addListener(new InputListener(){
                    float lastx, lasty;

                    @Override
                    public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){

                        if(button == KeyCode.mouseMiddle){
                            copy();
                            return false;
                        }

                        Vec2 v = localToParentCoordinates(Tmp.v1.set(x, y));
                        lastx = v.x;
                        lasty = v.y;
                        dragging = StatementElem.this;
                        toFront();
                        statements.layout();
                        recalculate();
                        return true;
                    }

                    @Override
                    public void touchDragged(InputEvent event, float x, float y, int pointer){
                        Vec2 v = localToParentCoordinates(Tmp.v1.set(x, y));

                        translation.add(v.x - lastx, v.y - lasty);
                        lastx = v.x;
                        lasty = v.y;

                        statements.layout();
                        recalculate();
                    }

                    @Override
                    public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button){
                        statements.finishLayout();
                        recalculate();
                    }
                });
            }).growX().height(38);

            row();

            table(t -> {
                t.left();
                t.marginLeft(4);
                t.setColor(color);
                st.build(t);
            }).pad(4).padTop(2).left().grow();

            marginBottom(7);
        }

        public void updateAddress(int index){
            this.index = index;
            addressLabel.setText(index + "");
        }

        public void copy(){
            st.saveUI();
            LStatement copy = st.copy();

            if(copy instanceof JumpStatement st && st.destIndex != -1){
                int index = statements.getChildren().indexOf(this);
                if(index != -1 && index < st.destIndex){
                    st.destIndex ++;
                }
            }

            if(copy != null){
                StatementElem s = new StatementElem(copy);

                statements.addChildAfter(StatementElem.this, s);
                statements.layout();
                copy.elem = s;
                copy.setupUI();
                recalculate();
            }
        }

        public void paste(Seq<LStatement> states) {
            var idx = statements.getChildren().indexOf(this) + 1;
            states.truncate(LExecutor.maxInstructions - statements.getChildren().size);
            states.reverse();

            for (var state : states) {
                if (state instanceof JumpStatement jump && jump.destIndex != -1) jump.destIndex += idx;
                addAt(idx, state);
            }
            for (var state : states) state.setupUI();
            statements.layout();
            recalculate();
        }

        @Override
        public void draw(){
            float pad = 5f;
            Fill.dropShadow(x + width/2f, y + height/2f, width + pad, height + pad, 10f, 0.9f * parentAlpha);

            Draw.color(0, 0, 0, 0.3f * parentAlpha);
            Fill.crect(x, y, width, height);
            Draw.reset();

            super.draw();
        }
    }

    public static class JumpButton extends ImageButton{
        Color hoverColor = Pal.place;
        Color defaultColor = Color.white;
        Prov<StatementElem> to;
        boolean selecting;
        float mx, my;
        ClickListener listener;

        public JumpLine line;

        public JumpButton(Prov<StatementElem> getter, Cons<StatementElem> setter){
            super(Tex.logicNode, Styles.colori);

            to = getter;
            addListener(listener = new ClickListener());

            addListener(new InputListener(){
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode code){
                    selecting = true;
                    setter.get(null);
                    mx = x;
                    my = y;
                    return true;
                }

                @Override
                public void touchDragged(InputEvent event, float x, float y, int pointer){
                    mx = x;
                    my = y;
                }

                @Override
                public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode code){
                    localToStageCoordinates(Tmp.v1.set(x, y));
                    StatementElem elem = canvas.hovered;

                    setter.get(elem); // Changed to allow jumping to self
                    canvas.recalculate();
                    selecting = false;
                }
            });

            update(() -> {
                if(to.get() != null && to.get().parent == null){
                    setter.get(null);
                }

                setColor(listener.isOver() ? hoverColor : defaultColor);
                getStyle().imageUpColor = this.color;
            });

            line = new JumpLine(this);
        }

        @Override
        protected void setScene(Scene stage){
            super.setScene(stage);

            if(stage == null){
                line.remove();
            }else{
                canvas.jumps.addChild(line);
            }
        }
    }

    public static class JumpLine extends Element{
        public JumpButton button;
        int heightx;

        public JumpLine(JumpButton button){
            this.button = button;
        }

        @Override
        public void act(float delta){
            super.act(delta);

            if(button.listener.isOver()){
                toFront();
            }
        }

        @Override
        public void draw(){
            canvas.jumpCount ++;

            if(canvas.jumpCount > maxJumpsDrawn && !button.selecting && !button.listener.isOver()){
                return;
            }

            if(button.to.get() == null || JumpSegment.allMap.get(button.to.get().index) == null ||
                    (button.parent.parent != null) && button.parent.parent instanceof StatementElem se && se == canvas.dragging) // hack ://
                heightx = JumpHeight.maxHeight + 2;
            else
                heightx = JumpSegment.allMap.get(button.to.get().index).height;

            Element hover = button.to.get() == null && button.selecting ? canvas.hovered : button.to.get();
            boolean draw = false;
            Vec2 t = Tmp.v1, r = Tmp.v2;

            Group desc = canvas.pane;

            button.localToAscendantCoordinates(desc, r.set(0, 0));

            if(hover != null){
                hover.localToAscendantCoordinates(desc, t.set(hover.getWidth(), hover.getHeight()/2f));

                draw = true;
            }else if(button.selecting){
                t.set(r).add(button.mx, button.my);
                draw = true;
            }

            float offset = canvas.pane.getVisualScrollY() - canvas.pane.getMaxY();

            t.y += offset;
            r.y += offset;

            if(draw){
                drawLine(r.x + button.getWidth()/2f, r.y + button.getHeight()/2f, t.x, t.y);

                float s = button.getWidth();
                Draw.color(button.color);
                Tex.logicNode.draw(t.x + s*0.75f, t.y - s/2f, -s, s);
                Draw.reset();
            }
        }

        public void drawLine(float x, float y, float x2, float y2){
            float width = 3f;
            Lines.stroke(width, button.color);
            Draw.alpha(parentAlpha);

            float len = heightx * (width * 3f) + 15f;
            float maxX = Math.max(x, x2) + len;
            Lines.beginLine();
            Lines.linePoint(x, y);
            Lines.linePoint(maxX, y);
            Lines.linePoint(maxX, y2);
            Lines.linePoint(x2, y2);
            Lines.endLine();

            /*
            Lines.curve(
            x, y,
            x + dist, y,
            x2 + dist, y2,
            x2, y2,
            Math.max(18, (int)(Mathf.dst(x, y, x2, y2) / 6)));
            */
        }
    }
}
