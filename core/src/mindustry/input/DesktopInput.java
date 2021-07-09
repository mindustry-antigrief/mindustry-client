package mindustry.input;

import arc.*;
import arc.Graphics.*;
import arc.Graphics.Cursor.*;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.ai.types.*;
import mindustry.client.*;
import mindustry.client.antigrief.*;
import mindustry.client.navigation.*;
import mindustry.client.navigation.waypoints.*;
import mindustry.client.ui.*;
import mindustry.core.*;
import mindustry.entities.units.*;
import mindustry.game.EventType.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;
import mindustry.world.*;
import mindustry.world.blocks.logic.*;
import mindustry.world.blocks.payloads.*;

import static arc.Core.*;
import static mindustry.Vars.*;
import static mindustry.client.ClientVars.*;
import static mindustry.input.PlaceMode.*;

public class DesktopInput extends InputHandler{
    public Vec2 movement = new Vec2();
    /** Current cursor type. */
    public Cursor cursorType = SystemCursor.arrow;
    /** Position where the player started dragging a line. */
    public int selectX = -1, selectY = -1, schemX = -1, schemY = -1;
    /** Last known line positions.*/
    public int lastLineX, lastLineY, schematicX, schematicY;
    /** Whether selecting mode is active. */
    public PlaceMode mode;
    /** Animation scale for line. */
    public float selectScale;
    /** Selected build request for movement. */
    public @Nullable BuildPlan sreq;
    /** Whether player is currently deleting removal requests. */
    public boolean deleting = false, shouldShoot = false;
    public static boolean panning = false;
    /** Mouse pan speed. */
    public float panScale = 0.005f, panSpeed = 4.5f, panBoostSpeed = 11f;
    /** Delta time between consecutive clicks. */
    public long selectMillis = 0;
    /** Previously selected tile. */
    public Tile prevSelected;
    private long lastShiftZ;

    @Override
    public void buildUI(Group group){
        //various hints
        group.fill(t -> {
            t.bottom();
            t.visible(() -> ui.hudfrag.shown);
            t.table(Styles.black6, b -> {
                StringBuilder str = new StringBuilder(), tmp = new StringBuilder();
                Boolp showHint = () -> str.length() != 0 || lastSchematic != null && selectRequests.any();
                b.defaults().left();
                b.label(() -> {
                    str.setLength(0);
                    if(Core.settings.getBool("hints")) {
                        if(!isBuilding && !settings.getBool("buildautopause") && !player.unit().isBuilding()){
                            str.append("\n").append(bundle.format("enablebuilding", keybinds.get(Binding.pause_building).key.toString()));
                        }else if(player.unit().isBuilding() || !player.persistPlans.isEmpty()){
                            str.append("\n")
                                .append(bundle.format(isBuilding ? "pausebuilding" : "resumebuilding", keybinds.get(Binding.pause_building).key.toString()))
                                .append("\n").append(bundle.format("cancelbuilding", keybinds.get(Binding.clear_building).key.toString()))
                                .append("\n").append(bundle.format("selectschematic", keybinds.get(Binding.schematic_select).key.toString()));
                        }
                        if(player.unit().isBuilding() || dispatchingBuildPlans){
                            str.append("\n").append(bundle.format(dispatchingBuildPlans ? "client.stopsendbuildplans" : "client.sendbuildplans", keybinds.get(Binding.send_build_queue).key.toString()));
                        }
                        if(UnitType.alpha == 0){
                            str.append("\n").append(bundle.format("client.toggleunits", "SHIFT + " + keybinds.get(Binding.invisible_units).key.toString()));
                        }
                        if(showingTurrets){
                            str.append("\n").append(bundle.format("client.toggleturrets", keybinds.get(Binding.show_turret_ranges).key.toString()));
                        }
                        if(hidingBlocks){
                            str.append("\n").append(bundle.format("client.toggleblocks", keybinds.get(Binding.hide_blocks).key.toString()));
                        }
                        if(Navigation.state == NavigationState.RECORDING){
                            str.append("\n").append(bundle.format("client.waypoint", keybinds.get(Binding.place_waypoint).key.toString()));
                        }else if(Navigation.state == NavigationState.FOLLOWING){
                            str.append("\n").append(bundle.format("client.stoppath", keybinds.get(Binding.stop_following_path).key.toString()));
                        }

                        if(selectRequests.any()){ // Any selection
                            str.append("\n").append(bundle.format("schematic.flip", keybinds.get(Binding.schematic_flip_x).key.toString(), keybinds.get(Binding.schematic_flip_y).key.toString()));
                        }
                    }
                    if(selectRequests.size > 1024){ // Any selection with more than 1024 blocks
                        str.append("\n").append(bundle.format("client.largeschematic", keybinds.get(Binding.toggle_placement_modifiers).key.toString()));
                    }

                    t.color.a = Mathf.lerpDelta(t.color.a, Mathf.num(showHint.get()), .15f);
                    if (t.color.a > .01f) {
                        t.touchable = Touchable.childrenOnly;
                    } else {
                        t.touchable = Touchable.disabled;
                        tmp.setLength(0); // Empty this so it doesnt look all wonky if hints are toggled off while playing
                    }
                    return str.length() != 0 ? tmp.replace(0, tmp.length(), str.deleteCharAt(0).toString()) : tmp;
                }).style(Styles.outlineLabel);

                b.row();
                b.table().update(c -> { // This is the worst way possible to add/remove the schematic save button but it works ok
                    if (!c.hasChildren() && lastSchematic != null && selectRequests.any()) {
                        c.button("@schematic.add", Icon.save, this::showSchematicSave).grow().padTop(10).disabled(d -> lastSchematic == null || lastSchematic.file != null).get().getLabel().setWrap(false);
                    } else if (c.hasChildren() && showHint.get() && (lastSchematic == null || selectRequests.isEmpty())) {
                        c.clearChildren();
                    }
                }).growX();
            }).margin(10f);
        });
    }

    @Override
    public void drawTop(){
        Lines.stroke(1f);
        int cursorX = tileX(Core.input.mouseX());
        int cursorY = tileY(Core.input.mouseY());

        //draw break selection
        if(mode == breaking){
            drawBreakSelection(selectX, selectY, cursorX, cursorY, /*!Core.input.keyDown(Binding.schematic_select) ? maxLength :*/ Vars.maxSchematicSize);
        }

        if(Core.input.keyDown(Binding.schematic_select) && !Core.scene.hasKeyboard() && mode != breaking){
            drawSelection(schemX, schemY, cursorX, cursorY, Vars.maxSchematicSize);
        }

        Draw.reset();
    }

    @Override
    public void drawBottom(){
        int cursorX = tileX(input.mouseX());
        int cursorY = tileY(input.mouseY());

        //draw request being moved
        if(sreq != null){
            boolean valid = validPlace(sreq.x, sreq.y, sreq.block, sreq.rotation, sreq);
            if(sreq.block.rotate){
                drawArrow(sreq.block, sreq.x, sreq.y, sreq.rotation, valid);
            }

            sreq.block.drawPlan(sreq, allRequests(), valid);

            drawSelected(sreq.x, sreq.y, sreq.block, getRequest(sreq.x, sreq.y, sreq.block.size, sreq) != null ? Pal.remove : Pal.accent);
        }

        //draw hover request
        if(mode == none && !isPlacing()){
            BuildPlan req = getRequest(cursorX, cursorY);
            if(req != null){
                drawSelected(req.x, req.y, req.breaking ? req.tile().block() : req.block, Pal.accent);
            }
        }

        //draw schematic requests
        selectRequests.each(req -> {
            req.animScale = 1f;
            drawRequest(req);
        });

        selectRequests.each(this::drawOverRequest);

        if(player.isBuilder()){
            //draw things that may be placed soon
            if(mode == placing && block != null){
                for(int i = 0; i < lineRequests.size; i++){
                    BuildPlan req = lineRequests.get(i);
                    if(req.block == null) continue;
                    if(i == lineRequests.size - 1 && req.block.rotate){
                        drawArrow(block, req.x, req.y, req.rotation);
                    }
                    drawRequest(req);
                }
                lineRequests.each(this::drawOverRequest);
            }else if(isPlacing()){
                if(block.rotate && block.drawArrow){
                    drawArrow(block, cursorX, cursorY, rotation);
                }
                Draw.color();
                boolean valid = validPlace(cursorX, cursorY, block, rotation);
                drawRequest(cursorX, cursorY, block, rotation);
                block.drawPlace(cursorX, cursorY, rotation, valid);

                if(block.saveConfig){
                    Draw.mixcol(!valid ? Pal.breakInvalid : Color.white, (!valid ? 0.4f : 0.24f) + Mathf.absin(Time.globalTime, 6f, 0.28f));
                    brequest.set(cursorX, cursorY, rotation, block);
                    brequest.config = block.lastConfig;
                    block.drawRequestConfig(brequest, allRequests());
                    brequest.config = null;
                    Draw.reset();
                }

            }else if(mode == payloadPlace){
                if(player.unit() instanceof Payloadc){
                    Payload payload = ((Payloadc)player.unit()).hasPayload() ? ((Payloadc)player.unit()).payloads().peek() : null;
                    if(payload != null){
                        if(payload instanceof BuildPayload){
                            Block block = ((BuildPayload)payload).block();
                            boolean wasVisible = block.isVisible();
                            if (!wasVisible) state.rules.revealedBlocks.add(block);
                            drawRequest(cursorX, cursorY, block, 0);
                            if(input.keyTap(Binding.select) && validPlace(cursorX, cursorY, block, 0)){
                                if(Navigation.state == NavigationState.RECORDING){
                                    Navigation.addWaypointRecording(new PayloadDropoffWaypoint(cursorX, cursorY));
                                }
                                Navigation.follow(new WaypointPath(new Seq<>(new Waypoint[]{new PositionWaypoint(player.x, player.y), new PayloadDropoffWaypoint(cursorX, cursorY)})));
                                NavigationState previousState = Navigation.state;
                                Navigation.currentlyFollowing.addListener(() -> Navigation.state = previousState);
                                mode = ((Payloadc)player.unit()).payloads().size > 1 ? payloadPlace : none; // Disable payloadplace mode if this is the only payload.
                            }
                            if (!wasVisible) state.rules.revealedBlocks.remove(block);
                        }
                    }
                }
            }
        }

        Draw.reset();
    }

    @Override
    public void update(){
        super.update();

        if(Core.input.keyTap(Binding.player_list) && (scene.getKeyboardFocus() == null || scene.getKeyboardFocus().isDescendantOf(ui.listfrag.content) || scene.getKeyboardFocus().isDescendantOf(ui.minimapfrag.elem))){
            ui.listfrag.toggle();
        }

        conveyorPlaceNormal = input.keyDown(Binding.toggle_placement_modifiers);

        // Holding o hides units, pressing shift + o inverts the state; holding o will now show them.
        if ((input.keyTap(Binding.invisible_units) || (input.keyRelease(Binding.invisible_units) && !input.shift())) && scene.getKeyboardFocus() == null) {
            hidingUnits = !hidingUnits;
        }

        if(Navigation.state == NavigationState.RECORDING){
            if(input.keyTap(Binding.place_waypoint) && scene.getKeyboardFocus() == null){
                Navigation.addWaypointRecording(new PositionWaypoint(player.x, player.y));
            }
        }

        if(input.keyTap(Binding.show_turret_ranges) && scene.getKeyboardFocus() == null){
            showingTurrets = !showingTurrets;
        }

        if(input.keyTap(Binding.hide_blocks) && scene.getKeyboardFocus() == null){
            hidingBlocks = !hidingBlocks;
        }

        if(input.keyTap(Binding.stop_following_path) && scene.getKeyboardFocus() == null){
            Navigation.stopFollowing();
        }

        if(input.keyTap(Binding.auto_build) && scene.getKeyboardFocus() == null){
            Navigation.follow(new BuildPath());
        }

        if(input.keyTap(Binding.auto_repair) && scene.getKeyboardFocus() == null){
            Navigation.follow(new RepairPath());
        }

        if(input.keyTap(Binding.auto_mine) && scene.getKeyboardFocus() == null){
            Navigation.follow(new MinePath());
        }

        if(input.keyTap(Binding.toggle_strict_mode) && scene.getKeyboardFocus() == null){
            settings.put("assumeunstrict", !settings.getBool("assumeunstrict"));
        }

        boolean panCam = false;
        float camSpeed = (!Core.input.keyDown(Binding.boost) ? panSpeed : panBoostSpeed) * Time.delta;

        if(input.keyTap(Binding.navigate_to_camera) && scene.getKeyboardFocus() == null){
            if(selectRequests.any() == input.shift()) Navigation.navigateTo(input.mouseWorld()); // Z to nav to camera (SHIFT + Z when placing schem)
            else if (selectRequests.isEmpty()){ // SHIFT + Z to view lastSentPos, double tap to nav there, special case for logic viruses as well (does nothing when placing schem)
                if(Time.timeSinceMillis(lastShiftZ) < 400) Navigation.navigateTo(lastSentPos.cpy().scl(tilesize));
                else Spectate.INSTANCE.spectate(lastSentPos.cpy().scl(tilesize));
                lastShiftZ = Time.millis();

                if(Time.timeSinceMillis(lastVirusWarnTime) < 3000 && lastVirusWarning != null && world.tile(lastVirusWarning.pos()).build == lastVirusWarning){ // Logic virus
                    virusBuild = lastVirusWarning; // Store this build in its own var so it isnt overwritten
                    lastVirusWarning = null;

                    virusBuild.configure(LogicBlock.compress("end\n" + virusBuild.code, virusBuild.relativeConnections())); // Disable the block while we look into it
                    try{Vars.ui.logic.show(virusBuild.code, code -> virusBuild.configure(LogicBlock.compress(code, virusBuild.relativeConnections())));}catch(Exception ignored){} // Inspect the code
                }
            }
        }

        if(input.keyDown(Binding.pan) && scene.getKeyboardFocus() == null){
            panCam = true;
            panning = true;
        }

        if(input.keyDown(Binding.freecam_modifier) && (input.axis(Binding.move_x) != 0f || input.axis(Binding.move_y) != 0f) && scene.getKeyboardFocus() == null){
            panning = true;
            Spectate.INSTANCE.setPos(null);
            float speed = Time.delta;
            speed *= camera.width;
            speed /= 75f;
            camera.position.add(input.axis(Binding.move_x) * speed, input.axis(Binding.move_y) * speed);
        }

        if(input.keyDown(Binding.drop_payload) && scene.getKeyboardFocus() == null){
            mode = payloadPlace;
        }
        if(input.keyRelease(Binding.drop_payload) && scene.getKeyboardFocus() == null){
            mode = none;
        }

        if (input.keyDown(Binding.find_modifier) && input.keyRelease(Binding.find)) {
            FindDialog.INSTANCE.show();
        }

//        if((Math.abs(Core.input.axis(Binding.move_x)) > 0 || Math.abs(Core.input.axis(Binding.move_y)) > 0 || input.keyDown(Binding.mouse_move)) && (!scene.hasField())){
//            panning = false;
//        }


        //TODO awful UI state checking code
        if(((player.dead() || state.isPaused()) && !ui.chatfrag.shown()) && !scene.hasField() && !scene.hasDialog()){
            if(input.keyDown(Binding.mouse_move)){
                panCam = true;
            }

            Core.camera.position.add(Tmp.v1.setZero().add(Core.input.axis(Binding.move_x), Core.input.axis(Binding.move_y)).nor().scl(camSpeed));
        }else if(!player.dead() && !panning){
            Core.camera.position.lerpDelta(player, Core.settings.getBool("smoothcamera") ? 0.08f : 1f);
        }

        if(panCam){
            Core.camera.position.x += Mathf.clamp((Core.input.mouseX() - Core.graphics.getWidth() / 2f) * panScale, -1, 1) * camSpeed;
            Core.camera.position.y += Mathf.clamp((Core.input.mouseY() - Core.graphics.getHeight() / 2f) * panScale, -1, 1) * camSpeed;
        }

        shouldShoot = !scene.hasMouse();
        Tile cursor = tileAt(Core.input.mouseX(), Core.input.mouseY());

        if(!scene.hasMouse()){
            if(Core.input.keyDown(Binding.tile_actions_menu_modifier) && Core.input.keyTap(Binding.select) && cursor != null){ // Tile actions / alt click menu
                int itemHeight = 30;
                Table table = new Table(Tex.buttonTrans);
                table.setWidth(400);
                table.margin(10);
                table.fill();
                table.touchable = Touchable.enabled; // This is needed
                table.defaults().height(itemHeight).padTop(5).fillX();
                try {
                    table.add(cursor.block().localizedName + ": (" + cursor.x + ", " + cursor.y + ")").height(itemHeight).left().growX().fillY().padTop(-5);
                } catch (Exception e) {ui.chatfrag.addMessage(e.getMessage(), "client", Color.scarlet);}

                table.row().fill();
                table.button("@client.log", () -> { // Tile Logs
                    TileRecords.INSTANCE.show(cursor);
                    table.remove();
                });

                table.row().fill();
                table.button("@client.unitpicker", () -> { // Unit Picker / Sniper
                    ui.unitPicker.show();
                    table.remove();
                });

                table.row().fill();
                table.button("@client.teleport", () -> {
                    NetClient.setPosition(World.unconv(cursor.x), World.unconv(cursor.y));
                    table.remove();
                });

                table.row().fill();
                table.button("@client.path.waypoints", () -> {
                    BaseDialog dialog = new BaseDialog("@client.path.waypoints");
                    dialog.addCloseButton();
                    dialog.cont.setWidth(200f);
                    dialog.cont.add(new TextButton("@client.path.record")).growX().get().clicked(() -> {Navigation.startRecording(); dialog.hide();});
                    dialog.cont.row();
                    dialog.cont.add(new TextButton("@client.path.stoprecording")).growX().get().clicked(() -> {Navigation.stopRecording(); dialog.hide();});
                    dialog.cont.row();
                    dialog.cont.add(new TextButton("@client.path.follow")).growX().get().clicked(() -> {if (Navigation.recordedPath != null) {Navigation.recordedPath.reset(); Navigation.follow(Navigation.recordedPath); Navigation.recordedPath.setShow(true);} dialog.hide();});
                    dialog.cont.row();
                    dialog.cont.add(new TextButton("@client.path.followrepeat")).growX().get().clicked(() -> {if (Navigation.recordedPath != null) {Navigation.recordedPath.reset(); Navigation.follow(Navigation.recordedPath, true); Navigation.recordedPath.setShow(true);} dialog.hide();});
                    dialog.cont.row();
                    dialog.cont.add(new TextButton("@client.path.stopfollowing")).growX().get().clicked(() -> {Navigation.stopFollowing(); dialog.hide();});
                    dialog.show();
                });

                table.setHeight((itemHeight + 10) * (table.getRows() + 1));
                table.setPosition(input.mouseX() - 1, input.mouseY() + 1, Align.topLeft); // Offset by 1 pixel so the code below doesn't trigger instantly
                table.update(() -> {
                    if(input.keyTap(Binding.select) && !table.hasMouse()){
                        table.remove();
                    }
                });
                scene.add(table);
            }
            if((input.keyDown(Binding.control) || input.shift()) && Core.input.keyTap(Binding.select)){
                Unit on = selectedUnit(true);
                var build = selectedControlBuild();
                if(on != null){
                    if (input.keyDown(Binding.control) && on.isAI()) Call.unitControl(player, on); // Ctrl + click: control unit
                    else if (input.shift() && on.isPlayer() && !on.isLocal()) Navigation.follow(new AssistPath(on.playerNonNull())); // Shift + click player: quick assist
                    else if (on.controller() instanceof LogicAI p && p.controller != null) Spectate.INSTANCE.spectate(p.controller); // Shift + click logic unit: spectate processor
                    shouldShoot = false;
                    recentRespawnTimer = 1f;
                }else if(build != null){
                    Call.buildingControlSelect(player, build);
                    recentRespawnTimer = 1f;
                }
            }
        }

        if(!player.dead() && !state.isPaused() && !scene.hasField()){
            updateMovement(player.unit());

            if(Core.input.keyTap(Binding.respawn)){
                controlledType = null;
                recentRespawnTimer = 1f;
                Call.unitClear(player);
            }
        }

        if(Core.input.keyRelease(Binding.select)){
            player.shooting = false;
        }

        if(state.isGame() && !scene.hasDialog() && !(scene.getKeyboardFocus() instanceof TextField)){
            if(Core.input.keyTap(Binding.minimap)) ui.minimapfrag.toggle();
            if(Core.input.keyTap(Binding.planet_map) && state.isCampaign()) ui.planet.toggle();
            if(Core.input.keyTap(Binding.research) && state.isCampaign()) ui.research.toggle();
        }

        if(state.isMenu() || Core.scene.hasDialog()) return;

        //zoom camera
        if((!Core.scene.hasScroll() || Core.input.keyDown(Binding.diagonal_placement)) && !ui.chatfrag.shown() && Math.abs(Core.input.axisTap(Binding.zoom)) > 0
            && !Core.input.keyDown(Binding.rotateplaced) && (Core.input.keyDown(Binding.diagonal_placement) || ((!player.isBuilder() || !isPlacing() || !block.rotate) && selectRequests.isEmpty()))){
            renderer.scaleCamera(Core.input.axisTap(Binding.zoom));
        }

        if(Core.input.keyTap(Binding.select) && !Core.scene.hasMouse()){
            Tile selected = world.tileWorld(input.mouseWorldX(), input.mouseWorldY());
            if(selected != null){
                Call.tileTap(player, selected);
            }
        }

        if(player.dead()){
            cursorType = SystemCursor.arrow;
            return;
        }

        pollInput();

        //deselect if not placing
        if(!isPlacing() && mode == placing){
            mode = none;
        }

        if(player.shooting && !canShoot()){
            player.shooting = false;
        }

        if(isPlacing() && player.isBuilder()){
            cursorType = SystemCursor.hand;
            selectScale = Mathf.lerpDelta(selectScale, 1f, 0.2f);
        }else{
            selectScale = 0f;
        }

        if(!Core.input.keyDown(Binding.diagonal_placement) && Math.abs((int)Core.input.axisTap(Binding.rotate)) > 0){
            rotation = Mathf.mod(rotation + (int)Core.input.axisTap(Binding.rotate), 4);

            if(sreq != null){
                sreq.rotation = Mathf.mod(sreq.rotation + (int)Core.input.axisTap(Binding.rotate), 4);
            }

            if(isPlacing() && mode == placing){
                updateLine(selectX, selectY);
            }else if(!selectRequests.isEmpty() && !ui.chatfrag.shown()){
                rotateRequests(selectRequests, Mathf.sign(Core.input.axisTap(Binding.rotate)));
            }
        }

        if(cursor != null){
            if(cursor.build != null){
                cursorType = cursor.build.getCursor();
            }

            if((isPlacing() && player.isBuilder()) || !selectRequests.isEmpty()){
                cursorType = SystemCursor.hand;
            }

            if(!isPlacing() && canMine(cursor)){
                cursorType = ui.drillCursor;
            }

            if(getRequest(cursor.x, cursor.y) != null && mode == none){
                cursorType = SystemCursor.hand;
            }

            if(canTapPlayer(Core.input.mouseWorld().x, Core.input.mouseWorld().y)){
                cursorType = ui.unloadCursor;
            }

            if(cursor.build != null && cursor.interactable(player.team()) && !isPlacing() && Math.abs(Core.input.axisTap(Binding.rotate)) > 0 && Core.input.keyDown(Binding.rotateplaced) && cursor.block().rotate && cursor.block().quickRotate){
                Call.rotateBlock(player, cursor.build, Core.input.axisTap(Binding.rotate) > 0);
            }
        }

        if(input.keyTap(Binding.reset_camera) && scene.getKeyboardFocus() == null && (cursor == null || cursor.build == null || !(cursor.build.block.rotate && cursor.build.block.quickRotate && cursor.build.interactable(player.team())))){
            panning = false;
            Spectate.INSTANCE.setPos(null);
        }

        if(!Core.scene.hasMouse()){
            Core.graphics.cursor(cursorType);
        }

        cursorType = SystemCursor.arrow;
    }

    @Override
    public void useSchematic(Schematic schem){
        block = null;
        schematicX = tileX(getMouseX());
        schematicY = tileY(getMouseY());

        selectRequests.clear();
        selectRequests.addAll(schematics.toRequests(schem, schematicX, schematicY));
        mode = none;
    }

    @Override
    public boolean isBreaking(){
        return mode == breaking;
    }

    @Override
    public void buildPlacementUI(Table table){
        table.image().color(Pal.gray).height(4f).colspan(4).growX();
        table.row();
        table.left().margin(0f).defaults().size(48f).left();

        table.button(Icon.paste, Styles.clearPartiali, () -> {
            ui.schematics.show();
        }).tooltip("@schematics");

        table.button(Icon.book, Styles.clearPartiali, () -> {
            ui.database.show();
        }).tooltip("@database");

        table.button(Icon.map, Styles.clearPartiali, () -> {
            if (state.isCampaign() && !Vars.net.client()) ui.planet.show();
            else MarkerDialog.INSTANCE.show();
        }).tooltip(state.isCampaign() ? "@planetmap" : "Map Markers"); // FINISHME: Doesn't update

        table.button(Icon.tree, Styles.clearPartiali, () -> {
            ui.research.show();
        }).visible(() -> state.isCampaign()).tooltip("@research");
    }

    void pollInput(){
        if(scene.getKeyboardFocus() instanceof TextField) return;

        Tile selected = tileAt(Core.input.mouseX(), Core.input.mouseY());
        int cursorX = tileX(Core.input.mouseX());
        int cursorY = tileY(Core.input.mouseY());
        int rawCursorX = World.toTile(Core.input.mouseWorld().x), rawCursorY = World.toTile(Core.input.mouseWorld().y);

        //automatically pause building if the current build queue is empty
        if(Core.settings.getBool("buildautopause") && isBuilding && !player.unit().isBuilding()){
            isBuilding = false;
            buildWasAutoPaused = true;
        }

        if(!selectRequests.isEmpty()){
            int shiftX = rawCursorX - schematicX, shiftY = rawCursorY - schematicY;

            selectRequests.each(s -> {
                s.x += shiftX;
                s.y += shiftY;
            });

            schematicX += shiftX;
            schematicY += shiftY;
        }

        if(Core.input.keyTap(Binding.deselect) && !isPlacing()){
            player.unit().mineTile = null;
        }

        if(Core.input.keyTap(Binding.clear_building)){
            player.unit().clearBuilding();
        }

        if(Core.input.keyTap(Binding.schematic_select) && !Core.scene.hasKeyboard() && mode != breaking){
            schemX = rawCursorX;
            schemY = rawCursorY;
        }

        if(Core.input.keyTap(Binding.schematic_menu) && !Core.scene.hasKeyboard()){
            if(ui.schematics.isShown()){
                ui.schematics.hide();
            }else{
                ui.schematics.show();
            }
        }

        if(Core.input.keyTap(Binding.clear_building) || isPlacing()){
            lastSchematic = null;
            selectRequests.clear();
        }

        if(Core.input.keyRelease(Binding.schematic_select) && !Core.scene.hasKeyboard() && selectX == -1 && selectY == -1 && schemX != -1 && schemY != -1){
            lastSchematic = schematics.create(schemX, schemY, rawCursorX, rawCursorY);
            useSchematic(lastSchematic);
            if(selectRequests.isEmpty()){
                lastSchematic = null;
            }
            schemX = -1;
            schemY = -1;
        }

        if(!selectRequests.isEmpty()){
            if(Core.input.keyTap(Binding.schematic_flip_x) && !input.shift()){ // Don't rotate when shift is held, if shift is held navigate instead.
                flipRequests(selectRequests, true);
            }

            if(Core.input.keyTap(Binding.schematic_flip_y)){
                flipRequests(selectRequests, false);
            }
        }

        if(sreq != null){
            float offset = ((sreq.block.size + 2) % 2) * tilesize / 2f;
            float x = Core.input.mouseWorld().x + offset;
            float y = Core.input.mouseWorld().y + offset;
            sreq.x = (int)(x / tilesize);
            sreq.y = (int)(y / tilesize);
        }

        if(block == null || mode != placing){
            lineRequests.clear();
        }

        if(Core.input.keyTap(Binding.pause_building)){
            isBuilding = !isBuilding;
            buildWasAutoPaused = false;

            if(isBuilding){
                player.shooting = false;
            }
        }

        if((cursorX != lastLineX || cursorY != lastLineY) && isPlacing() && mode == placing){
            updateLine(selectX, selectY);
            lastLineX = cursorX;
            lastLineY = cursorY;
        }

        if(Core.input.keyTap(Binding.select) && !Core.scene.hasMouse()){
            BuildPlan req = getRequest(cursorX, cursorY);

            if(Core.input.keyDown(Binding.break_block)){
                mode = none;
            }else if(selectRequests.any()){
                flushRequests(selectRequests);
                if(selectRequests.size > 1024 && !Core.input.keyDown(Binding.toggle_placement_modifiers)) { // Deselect large schems
                    selectRequests.clear();
                    lastSchematic = null;
                }
            }else if(isPlacing()){
                selectX = cursorX;
                selectY = cursorY;
                lastLineX = cursorX;
                lastLineY = cursorY;
                mode = placing;
                updateLine(selectX, selectY);
            }else if(req != null && !req.breaking && mode == none && !req.initialized){
                sreq = req;
            }else if(req != null && req.breaking){
                deleting = true;
            }else if(selected != null){
                //only begin shooting if there's no cursor event
                if(!tryTapPlayer(Core.input.mouseWorld().x, Core.input.mouseWorld().y) && !tileTapped(selected.build) && !player.unit().activelyBuilding() && !droppingItem
                    && !(tryStopMine(selected) || (!settings.getBool("doubletapmine") || selected == prevSelected && Time.timeSinceMillis(selectMillis) < 500) && tryBeginMine(selected)) && !Core.scene.hasKeyboard()){
                    player.shooting = shouldShoot;
                }
            }else if(!Core.scene.hasKeyboard()){ //if it's out of bounds, shooting is just fine
                player.shooting = shouldShoot;
            }
            selectMillis = Time.millis();
            prevSelected = selected;
        }else if(Core.input.keyTap(Binding.deselect) && isPlacing()){
            block = null;
            mode = none;
        }else if(Core.input.keyTap(Binding.deselect) && !selectRequests.isEmpty()){
            selectRequests.clear();
            lastSchematic = null;
        }else if(Core.input.keyTap(Binding.break_block) && !Core.scene.hasMouse() && player.isBuilder()){
            //is recalculated because setting the mode to breaking removes potential multiblock cursor offset
            deleting = false;
            mode = breaking;
            selectX = tileX(Core.input.mouseX());
            selectY = tileY(Core.input.mouseY());
            schemX = rawCursorX;
            schemY = rawCursorY;
        }

        if(Core.input.keyDown(Binding.select) && mode == none && !isPlacing() && deleting){
            BuildPlan req = getRequest(cursorX, cursorY);
            if(req != null && req.breaking){
                player.unit().plans().remove(req);
            }
        }else{
            deleting = false;
        }

        if(mode == placing && block != null){
            if(!overrideLineRotation && !Core.input.keyDown(Binding.diagonal_placement) && (selectX != cursorX || selectY != cursorY) && ((int)Core.input.axisTap(Binding.rotate) != 0)){
                rotation = ((int)((Angles.angle(selectX, selectY, cursorX, cursorY) + 45) / 90f)) % 4;
                overrideLineRotation = true;
            }
        }else{
            overrideLineRotation = false;
        }

        if(Core.input.keyRelease(Binding.break_block) && Core.input.keyDown(Binding.schematic_select) && mode == breaking){
            lastSchematic = schematics.create(schemX, schemY, rawCursorX, rawCursorY);
            schemX = -1;
            schemY = -1;
        }

        if(Core.input.keyRelease(Binding.break_block) || Core.input.keyRelease(Binding.select)){

            if(mode == placing && block != null){ //touch up while placing, place everything in selection
                flushRequests(lineRequests);
                lineRequests.clear();
                Events.fire(new LineConfirmEvent());
            }else if(mode == breaking){ //touch up while breaking, break everything in selection
                removeSelection(selectX, selectY, cursorX, cursorY, /*!Core.input.keyDown(Binding.schematic_select) ? maxLength :*/ Vars.maxSchematicSize);
                if(lastSchematic != null){
                    useSchematic(lastSchematic);
                    lastSchematic = null;
                }
            }
            selectX = -1;
            selectY = -1;

            tryDropItems(selected == null ? null : selected.build, Core.input.mouseWorld().x, Core.input.mouseWorld().y);

            if(sreq != null){
                if(getRequest(sreq.x, sreq.y, sreq.block.size, sreq) != null){
                    player.unit().plans().remove(sreq, true);
                }
                sreq = null;
            }

            mode = none;
        }

        if(Core.input.keyTap(Binding.toggle_block_status)){
            Core.settings.put("blockstatus", !Core.settings.getBool("blockstatus"));
        }

        if(Core.input.keyTap(Binding.toggle_power_lines)){
            if(Core.settings.getInt("lasersopacity") == 0){
                Core.settings.put("lasersopacity", Core.settings.getInt("preferredlaseropacity", 100));
            }else{
                Core.settings.put("preferredlaseropacity", Core.settings.getInt("lasersopacity"));
                Core.settings.put("lasersopacity", 0);
            }
        }
    }

    @Override
    public boolean selectedBlock(){
        return isPlacing() && mode != breaking;
    }

    @Override
    public float getMouseX(){
        return Core.input.mouseX();
    }

    @Override
    public float getMouseY(){
        return Core.input.mouseY();
    }

    @Override
    public void updateState(){
        super.updateState();

        if(state.isMenu()){
            droppingItem = false;
            mode = none;
            block = null;
            sreq = null;
            selectRequests.clear();
        }
    }

    protected void updateMovement(Unit unit){ // Heavily modified to support navigation
        boolean omni = unit.type.omniMovement;

        float speed = unit.realSpeed();
        float xa = Core.input.axis(Binding.move_x);
        float ya = Core.input.axis(Binding.move_y);
        if(input.keyDown(Binding.freecam_modifier)){
            xa = ya = 0f;
        }
        boolean boosted = (unit instanceof Mechc && unit.isFlying());

        movement.set(xa, ya).nor().scl(speed);
        if(Core.input.keyDown(Binding.mouse_move)){
            movement.add(input.mouseWorld().sub(player).scl(1f / 25f * speed)).limit(speed);
        }

        if(!Navigation.isFollowing()){
            float mouseAngle = Angles.mouseAngle(unit.x, unit.y);
            boolean aimCursor = omni && player.shooting && unit.type.hasWeapons() && unit.type.faceTarget && !boosted && unit.type.rotateShooting;

            if(aimCursor){
                unit.lookAt(mouseAngle);
            }else{
                unit.lookAt(unit.prefRotation());
            }

            if(omni || true){
                unit.moveAt(movement);
            }else{
                unit.moveAt(Tmp.v2.trns(unit.rotation, movement.len()));
                //problem: actual unit rotation is controlled by velocity, but velocity is 1) unpredictable and 2) can be set to 0            if(!movement.isZero()){
                if(!movement.isZero()){
                    unit.rotation = Angles.moveToward(unit.rotation,movement.angle(), unit.type.rotateSpeed * Math.max(Time.delta, 1));
                }
            }
            unit.aim(unit.type.faceTarget ? Core.input.mouseWorld() : Tmp.v1.trns(unit.rotation, Core.input.mouseWorld().dst(unit)).add(unit.x, unit.y));

            // if autoboost, invert the behavior of the boost key
            player.boosting = (Core.settings.getBool("autoboost") != input.keyDown(Binding.boost));
        }
        unit.controlWeapons(true, player.shooting && !boosted);

        player.mouseX = unit.aimX();
        player.mouseY = unit.aimY();

        //update payload input
        if(unit instanceof Payloadc){
            if(Core.input.keyTap(Binding.pickupCargo)){
                tryPickupPayload();
            }

            if(Core.input.keyTap(Binding.dropCargo)){
                tryDropPayload();
            }
        }

        //update commander unit
        if(Core.input.keyTap(Binding.command) && unit.type.commandLimit > 0){
            Call.unitCommand(player);
        }
    }
}
