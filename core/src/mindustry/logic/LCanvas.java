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

import java.util.*;

import static mindustry.Vars.*;

public class LCanvas extends Table{
    //ew static variables
    static LCanvas canvas;

    public DragLayout statements;
    public ScrollPane pane;
    public Group jumps;

    StatementElem dragging;
    static boolean jumping;
    StatementElem hovered;
    float targetWidth;
    int jumpCount = 0;
    boolean privileged;
    Seq<Tooltip> tooltips = new Seq<>();
    float visibleBoundLower, visibleBoundUpper;

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
        return Core.graphics.getWidth() < Scl.scl(900f) / (Core.settings.getInt("processorstatementscale") / 100f);
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
//        targetWidth = useRows() ? 400f : 900f;
        targetWidth = Core.graphics.getWidth() * Core.settings.getInt("processorstatementscale") / 100f;
        float s = pane != null ? pane.getScrollPercentY() : 0f;
        String toLoad = statements != null ? save() : null;

        clear();

        statements = new DragLayout();
        jumps = new WidgetGroup();

        pane = pane(t -> {
            t.center();
            t.add(statements).pad(2f).center().width(targetWidth);
            t.add(jumps);
            jumps.cullable = false;
        }).grow().get();
        pane.update(() -> {
            float cHeight = Core.graphics.getHeight();
            visibleBoundLower = pane.getMaxY() - pane.getVisualScrollY() - cHeight; // 1 screen above and below for buffer
            visibleBoundUpper = visibleBoundLower + pane.getHeight() + cHeight * 2;
        });
        pane.setFlickScroll(false);

        //load old scroll percent
        Core.app.post(() -> {
            pane.setScrollPercentY(s);
            pane.updateVisualScroll();
        });

        if(toLoad != null){
            load(toLoad);
        } else {
            statements.forceLayout();
            statements.layout();
            recalculate();
        }
    }
    public int maxJumpHeight = 0;
    public Seq<StatementElem> tempseq = new Seq<>();
    public PQueue<StatementElem> pq = new PQueue<>(12, Comparator.comparingInt(o -> o.maxJump));
    public final Bits mask = new Bits(LExecutor.maxInstructions + 5);

    public void recalculate(){
        tempseq.set(statements.getChildren().as());
        tempseq.each(t -> {t.resetJumpInfo(); if(t.st instanceof JumpStatement) t.st.saveUI();});
        tempseq.each(s -> {
            if(!(s.st instanceof JumpStatement js) || js.destIndex == -1) return;
            js.dest.updateJumpsToHere(s.index);
        });
        tempseq.sort((o1, o2) -> o1.minJump == o2.minJump ? o2.jumpSpan() - o1.jumpSpan() : o1.minJump - o2.minJump);

        int i = maxJumpHeight = 0, curr;
        StatementElem temp;
        mask.clear();
        pq.clear();
        while(i < tempseq.size && (curr = (temp = tempseq.get(i)).minJump) < Integer.MAX_VALUE){
            if(pq.empty() || curr <= pq.peek().maxJump){
                mask.set(temp.jumpHeight = mask.nextClearBit(0));
                maxJumpHeight = Math.max(maxJumpHeight, temp.jumpHeight);
                pq.add(temp);
                i++;
            }else mask.clear(pq.poll().jumpHeight);
        }
    }

    @Override
    public void layout(){
        if(dragging != null) return;
        super.layout();
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

        Seq<LStatement> statements = LAssembler.read(asm, privileged);
        statements.truncate(LExecutor.maxInstructions);
        this.statements.clearChildren();
        for(LStatement st : statements){
            add(st);
        }

        for(LStatement st : statements){
            st.setupUI();
        }

        this.statements.forceLayout();
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
        if(Core.input.isTouched() && (dragging != null || jumping)){
            float y = Core.input.mouseY();
            float dst = Math.min(y - this.y, Core.graphics.getHeight() - y);
            if(dst < Scl.scl(100f)){ //scroll margin
                int sign = Mathf.sign(Core.graphics.getHeight()/2f - y);
                pane.setScrollY(pane.getScrollY() + sign * Scl.scl(15f) * Time.delta);
            }
        }
    }

    public class DragLayout extends WidgetGroup{
        float space = Scl.scl(10f), prefWidth, prefHeight;
        Seq<Element> seq = new Seq<>();
        int insertPosition = 0;
        {
            setTransform(true);
        }
        @Override
        public void layout(){
            float cy = 0;
            seq.clear();

            float totalHeight = children.sumf(e -> e.getHeight() + space);

            height = prefHeight = totalHeight;
            width = prefWidth = Scl.scl(targetWidth);

            //layout everything normally
            for(int i = 0; i < children.size; i++){
                Element e = children.get(i);

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

                dragging.updateAddress(insertPosition);
                StatementElem e;
                //shift elements below insertion point down
                for(int i = insertPosition; i < seq.size; i++){
                    seq.get(i).y -= shiftAmount;
                    (e = (StatementElem) seq.get(i)).updateAddress(e.index + 1);
                }
            }
            pack();
        }

        public void forceLayout(){
            for(Element e : children){
                if(!(e instanceof StatementElem se)) return;
                se.forceLayout = true;
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

            super.draw();
        }

        void finishLayout(){
            if(dragging != null){
                //reset translation first
                for(Element child : children){
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
        public final static int MAX_SPAN = Integer.MIN_VALUE;
        public int minJump = Integer.MAX_VALUE, maxJump = -1, jumpHeight = -1;
        private boolean isDeleting = false;
        private JumpButton button;
        boolean forceLayout = true;

        public StatementElem(LStatement st){
            this.st = st;
            st.elem = this;

            background(Tex.whitePane);
            setColor(st.category().color);
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
                    try {
                        this.paste(LAssembler.read(Core.app.getClipboardText().replace("\r\n", "\n"), privileged));
                    } catch (Throwable e) {
                        ui.showException(e);
                    }
                });

                var temp = t.button(Icon.cancel, Styles.logici, () -> {
                    remove();
                    dragging = null;
                    statements.layout();
                    recalculate();
                }).size(24f);
                temp.get().tapped(() -> isDeleting = true);
                temp.get().released(() -> isDeleting = false);

                t.addListener(new InputListener(){
                    float lastx, lasty;

                    @Override
                    public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
                        canvas.setLayoutEnabled(false);
                        if(button == KeyCode.mouseMiddle){
                            copy();
                            return false;
                        }
                        if(isDeleting) return false;

                        Vec2 v = localToParentCoordinates(Tmp.v1.set(x, y));
                        lastx = v.x;
                        lasty = v.y;
                        dragging = StatementElem.this;
                        toFront();
                        statements.layout();
                        dragging.jumpHeight = -1;
                        return true;
                    }

                    @Override
                    public void touchDragged(InputEvent event, float x, float y, int pointer){
                        Vec2 v = localToParentCoordinates(Tmp.v1.set(x, y));

                        translation.add(v.x - lastx, v.y - lasty);
                        lastx = v.x;
                        lasty = v.y;

                        statements.layout();
                    }

                    @Override
                    public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button){
                        canvas.setLayoutEnabled(true);
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
                if(st instanceof JumpStatement){
                    button = (JumpButton)t.getChildren().peek();
                }
            }).pad(4).padTop(2).left().grow();

            marginBottom(7);
        }

        public void updateJumpsToHere(int loc){
            minJump = Math.min(minJump, index);
            maxJump = Math.max(maxJump, index);
            minJump = Math.min(minJump, loc);
            maxJump = Math.max(maxJump, loc);
        }

        public int jumpSpan(){
            int temp = maxJump - minJump;
            return temp == MAX_SPAN ? -1 : temp;
        }

        public void resetJumpInfo(){
            minJump = Integer.MAX_VALUE;
            jumpHeight = maxJumpHeight = -1;
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
        public void layout(){
            if(canvas.dragging != null && (y + height < canvas.visibleBoundLower || y > canvas.visibleBoundUpper)) return;
            super.layout();
        }

        @Override
        public void draw(){
            if(forceLayout){ // forces jump buttons to lay themselves out
                super.layout();
                forceLayout = false;
            }
            if(y + height < canvas.visibleBoundLower || y > canvas.visibleBoundUpper){
                if(button != null) button.draw();
                return;
            }
            float pad = 5f;
            Fill.dropShadow(x + width/2f, y + height/2f, width + pad, height + pad, 10f, 0.9f * parentAlpha);

            Draw.color(0, 0, 0, 0.3f * parentAlpha);
            Fill.crect(x, y, width, height);
            Draw.reset();

            super.draw();
        }
    }

    public static class JumpButton extends ImageButton{
        //Color hoverColor = Pal.place;
        Color hoverColor = new Color(Pal.place);
        Color defaultColor = Color.white;
        Prov<StatementElem> to;
        boolean selecting, colored;
        float mx, my;
        ClickListener listener;

        public JumpCurve curve;

        public JumpButton(Prov<StatementElem> getter, Cons<StatementElem> setter){
            super(Tex.logicNode, new ImageButtonStyle(){{
                imageUpColor = Color.white;
            }});

            to = getter;
            addListener(listener = new ClickListener());

            addListener(new InputListener(){
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode code){
                    selecting = true;
                    jumping = true;
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
                    jumping = false;
                }
            });

            update(() -> {
                if(to.get() != null && to.get().parent == null){
                    setter.get(null);
                }

                colored = listener.isOver() || selecting;
                if(colored){
                    getColor((Time.globalTime / 60f / 2f) % 3f);
                }
                setColor(colored ? hoverColor : defaultColor);
                getStyle().imageUpColor = this.color;
            });

            curve = new JumpCurve(this);
        }

        @Override
        protected void setScene(Scene stage){
            super.setScene(stage);

            if(stage == null){
                curve.remove();
            }else{
                canvas.jumps.addChild(curve);
            }
        }

        public void getColor(float state){
            //https://www.instructables.com/How-to-Make-Proper-Rainbow-and-Random-Colors-With-/ sine wave method
            //Log.debug("state: @\nr: @ g: @ b: @", state, sinColor(state, 0), sinColor(state, 1), sinColor(state, 2));
            hoverColor.set(sinColor(state, 0f), sinColor(state, 1f), sinColor(state, 2f));
        }
        private static float sinColor(float state, float rise){
            if(state < rise) state += 3f;
            if(state > rise + 2f) return 0f;
            return Mathf.absin((state - rise - 0.5f) * Mathf.pi, 0.5f, 1f); //im an idiot, 0.5f because at 0, the sin function should be 0 not -1
        }
    }

    public static class JumpCurve extends Element{
        public JumpButton button;
        int heightx;

        public JumpCurve(JumpButton button){
            this.button = button;
            calcArrowSpeed(); //TODO: fix 1000 calls
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
            //if(canvas.jumpCount > maxJumpsDrawn=100 && !button.selecting && !button.listener.isOver()){
            //return;
            //}
            @Nullable StatementElem to = button.to.get();
            @Nullable StatementElem from = button.parent.parent instanceof StatementElem ? (StatementElem) button.parent.parent : null;
            if(to == null || to.jumpHeight == -1 ||
                    (from != null && from == canvas.dragging))
                heightx = canvas.maxJumpHeight + 2;
            else
                heightx = button.to.get().jumpHeight;

            Element hover = to == null && button.selecting ? canvas.hovered : to;
            Vec2 t = Tmp.v1, r = Tmp.v2;

            Group desc = canvas.pane;

            button.localToAscendantCoordinates(desc, r.set(0, 0));

            if(hover != null){
                hover.localToAscendantCoordinates(desc, t.set(hover.getWidth(), hover.getHeight()/2f));
            }else if(button.selecting){
                t.set(r).add(button.mx, button.my);
            }else{
                return;
            }

            float offset = canvas.pane.getVisualScrollY() - canvas.pane.getMaxY();
            t.y += offset;
            r.y += offset;

            drawCurve(r.x + button.getWidth()/2f, r.y + button.getHeight()/2f, t.x, t.y);

            float s = button.getWidth();
            Draw.color(button.color);
            Tex.logicNode.draw(t.x + s*0.75f, t.y - s/2f, -s, s);
            Draw.reset();
        }

        static float lineWidth = 3f, heightSpacing = 8f, idealCurveRadius = 8f;
        static float arrowInterval = 5f;
        static float arrowSpeed;
        public static void calcArrowSpeed(){
            arrowSpeed = Core.graphics.getHeight() / 10f; // arbitrary speed of one screen height every x seconds //TODO: change this back (currently for debug)
        }
        public void drawCurve(float x, float y, float x2, float y2){
            float cHeight = Core.graphics.getHeight();
            if((y > cHeight && y2 > cHeight) || (y < 0 && y2 < 0)) return; //TODO: shift this to the draw method to prevent unnecessary calc?
            float yNew = Mathf.clamp(y, -lineWidth, cHeight + lineWidth), y2New = Mathf.clamp(y2, -lineWidth, cHeight + lineWidth); // margin so that curves are not partially cut off
            boolean draw1curve = yNew == y, draw2curve = y2New == y2;
            float curveRadius = Math.min(idealCurveRadius, Math.abs((y2 - y) / 2));
            Lines.stroke(lineWidth, button.color);
            Draw.alpha(parentAlpha);

            float len = heightx * (lineWidth + heightSpacing) + curveRadius + 2f;
            float maxX = Math.max(x, x2) + len + button.getWidth()*0.5f;
            int curveDirection = Mathf.sign(y2 - y);
            int isUpwards = Mathf.clamp(curveDirection, 0, 1);

            if(draw1curve){
                Lines.line(x, y, maxX - curveRadius, y);
                Lines.arc(maxX - curveRadius, y + curveRadius * curveDirection, curveRadius, 1/4f, isUpwards * -90);
            }
            Lines.line(maxX, yNew + curveRadius * curveDirection, maxX, y2New - curveRadius * curveDirection);
            if(draw2curve){
                Lines.arc(maxX - curveRadius, y2 - curveRadius * curveDirection, curveRadius, 1/4f, (isUpwards - 1) * 90);
                Lines.line(maxX - curveRadius, y2, x2, y2);
            }
            if(button.colored && button.to.get() != null){
                float bw = button.getWidth(), bh = button.getHeight();
                // new strategy: just spawn a new arrow every x seconds and we do math from there.
                float hLen1 = maxX - curveRadius - x, hLen2 = hLen1 - (x2 - x);
                float vLen = Math.abs(y2 - y) - curveRadius * 2f;
                float curvePathLength = Mathf.halfPi * curveRadius;
                float totalPathLength = hLen1 + hLen2 + vLen + curvePathLength * 2f - bw / 4f; //the second node placement is jank so -1/4bw
                float yarrow = (Time.globalTime / 60f) % arrowInterval; // Time.time will not work since we want it to run while game is paused

                for(float currPathProgress = yarrow * arrowSpeed; currPathProgress <= totalPathLength; currPathProgress += arrowInterval * arrowSpeed){
                    if(currPathProgress <= hLen1){
                        if(draw1curve) Tex.logicNode.draw(x + currPathProgress - bw/2, y - bh/2, bw, bh);
                    }else if(currPathProgress < hLen1 + curvePathLength){
                        if(draw1curve){ // Math intensifies
                            float theta = (currPathProgress - hLen1) / curvePathLength * 90f;
                            Tex.logicNode.draw(maxX - curveRadius * Mathf.cosDeg(theta) - bw/2, y + curveRadius * Mathf.sinDeg(theta) * curveDirection - bh/2,
                                    bw/2, bh/2, bw, bh, 1f, 1f, curveDirection * theta);
                        }
                    }else if(currPathProgress <= hLen1 + curvePathLength + vLen){
                        Tex.logicNode.draw(maxX - bw/2, y + curveDirection * (curveRadius + currPathProgress - hLen1 - curvePathLength) - bh/2,
                                bw/2, bh/2, bw, bh, 1f, 1f, curveDirection * 90f);
                    }else if(currPathProgress < totalPathLength - hLen2){
                        if(draw2curve){
                            float theta = (currPathProgress - (totalPathLength - hLen2 - curvePathLength)) / curvePathLength * 90f;
                            Tex.logicNode.draw(maxX - curveRadius * Mathf.sinDeg(theta) - bw/2, y2 - curveRadius * Mathf.cosDeg(theta) * curveDirection - bh/2,
                                    bw/2, bh/2, bw, bh, 1f, 1f, curveDirection * 90f + curveDirection * theta);
                        } else break;
                    }else if(currPathProgress <= totalPathLength){
                        if(draw2curve) Tex.logicNode.draw(x2 + (totalPathLength - currPathProgress) + bw/2, y2 - bh/2, -bw, bh); //+bw/2 because the scale is -bw (that's funny)
                        else break;
                    }
                }
            }

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
