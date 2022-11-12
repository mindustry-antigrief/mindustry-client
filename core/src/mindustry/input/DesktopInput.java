package mindustry.input;

import arc.*;
import arc.Graphics.*;
import arc.Graphics.Cursor.*;
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
import arc.util.pooling.*;
import mindustry.*;
import mindustry.ai.types.*;
import mindustry.client.*;
import mindustry.client.antigrief.*;
import mindustry.client.navigation.*;
import mindustry.client.navigation.waypoints.*;
import mindustry.client.ui.*;
import mindustry.client.utils.*;
import mindustry.content.*;
import mindustry.core.*;
import mindustry.entities.*;
import mindustry.entities.units.*;
import mindustry.game.EventType.*;
import mindustry.game.*;
import mindustry.game.Teams.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.input.Placement.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;
import mindustry.world.*;
import mindustry.world.blocks.logic.*;
import mindustry.world.blocks.payloads.*;

import java.util.*;

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
    /** Selected build plan for movement. */
    public @Nullable BuildPlan splan;
    /** Used to track whether the splan was moved. */
    public boolean splanMoved = false;
    /** Whether player is currently deleting removal plans. */
    public boolean deleting = false, shouldShoot = false, panning = false;
    /** Mouse pan speed. */
    public float panScale = 0.005f, panSpeed = 4.5f, panBoostSpeed = 15f;
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
                Boolp showHint = () -> str.length() != 0 || lastSchematic != null && selectPlans.any();
                b.defaults().left();
                b.label(() -> {
                    str.setLength(0);
                    if(Core.settings.getBool("hints")) {
                        if (isFreezeQueueing) {
                            str.append("\n").append(bundle.format("client.freezequeueing", keybinds.get(Binding.pause_building).key.toString()));
                        }
                        if(!isBuilding && !isBuildingLock && !settings.getBool("buildautopause") && !player.unit().isBuildingIgnoreNetworking()){
                            str.append("\n").append(bundle.format("enablebuilding", keybinds.get(Binding.pause_building).key.toString()));
                        }else if(player.unit().isBuildingIgnoreNetworking() || !Player.persistPlans.isEmpty()){
                            str.append("\n")
                                .append(bundle.format(isBuilding ? "pausebuilding" : "resumebuilding", keybinds.get(Binding.pause_building).key.toString()))
                                .append("\n").append(bundle.format("cancelbuilding", keybinds.get(Binding.clear_building).key.toString()))
                                .append("\n").append(bundle.format("selectschematic", keybinds.get(Binding.schematic_select).key.toString()));
                        }
                        if(player.unit().isBuildingIgnoreNetworking() || dispatchingBuildPlans){
                            str.append("\n").append(bundle.format(dispatchingBuildPlans ? "client.stopsendbuildplans" : "client.sendbuildplans", keybinds.get(Binding.send_build_queue).key.toString()));
                        }
                        if (hidingFog){
                            str.append("\n").append(bundle.format("client.togglefog", keybinds.get(Binding.invisible_units).key.toString()));
                        }
                        if(hidingUnits || hidingAirUnits){
                            str.append("\n").append(bundle.format("client.toggleunits", keybinds.get(Binding.invisible_units).key.toString()));
                            str.append("\n").append(bundle.format("client.toggleairunits", keybinds.get(Binding.invisible_units).key.toString()));
                        }
                        if(showingTurrets){
                            str.append("\n").append(bundle.format("client.toggleenemyturrets", keybinds.get(Binding.show_turret_ranges).key.toString()));
                        }
                        if (showingAllyTurrets) {
                            str.append("\n").append(bundle.format("client.toggleallyturrets", keybinds.get(Binding.show_turret_ranges).key.toString()));
                        }
                        if(showingInvTurrets){
                            str.append("\n").append(bundle.format("client.toggleinvturrets", keybinds.get(Binding.show_turret_ranges).key.toString()));
                        }
                        if(showingOverdrives){
                            str.append("\n").append(bundle.format("client.toggleoverdrives", keybinds.get(Binding.show_turret_ranges).key.toString()));
                        }
                        if(showingMassDrivers){
                            str.append("\n").append(bundle.format("client.togglemassdrivers", keybinds.get(Binding.show_massdriver_configs).key.toString()));
                        }
                        if(hidingBlocks){
                            str.append("\n").append(bundle.format("client.toggleblocks", keybinds.get(Binding.hide_blocks).key.toString()));
                        }
                        if (hidingPlans) {
                            str.append("\n").append(bundle.format("client.toggleplans", keybinds.get(Binding.hide_blocks).key.toString()));
                        }
                        if(Navigation.state == NavigationState.RECORDING){
                            str.append("\n").append(bundle.format("client.waypoint", keybinds.get(Binding.place_waypoint).key.toString()));
                        }else if(Navigation.state == NavigationState.FOLLOWING){
                            str.append("\n").append(bundle.format("client.stoppath", keybinds.get(Binding.stop_following_path).key.toString()));
                        }

                        if(selectPlans.any()){ // Any selection
                            str.append("\n").append(bundle.format("schematic.flip", keybinds.get(Binding.schematic_flip_x).key.toString(), keybinds.get(Binding.schematic_flip_y).key.toString()));
                        }
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
                    if (!c.hasChildren() && lastSchematic != null && selectPlans.any()) {
                        c.button("@schematic.add", Icon.save, this::showSchematicSave).grow().padTop(10).disabled(d -> lastSchematic == null || lastSchematic.file != null).get().getLabel().setWrap(false);
                    } else if (c.hasChildren() && showHint.get() && (lastSchematic == null || selectPlans.isEmpty())) {
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

        //draw freezing selection
        if(mode == freezing){
            drawFreezeSelection(selectX, selectY, cursorX, cursorY, Vars.maxSchematicSize);
        }
        //draw break selection
        if(mode == breaking){
            drawBreakSelection(selectX, selectY, cursorX, cursorY, /*!Core.input.keyDown(Binding.schematic_select) ? maxLength :*/ Vars.maxSchematicSize);
        }
        //draw dequeueing selection
        if (mode == dequeue){
            drawRemovePlanSelection(selectX, selectY, cursorX, cursorY, Vars.maxSchematicSize);
        }

        if(!Core.scene.hasKeyboard() && mode != breaking && mode != freezing && mode != dequeue){

            if(Core.input.keyDown(Binding.schematic_select)){
                drawSelection(schemX, schemY, cursorX, cursorY, Vars.maxSchematicSize);
            }else if(Core.input.keyDown(Binding.rebuild_select)){
                //TODO color?
                drawSelection(schemX, schemY, cursorX, cursorY, 0, Pal.sapBulletBack, Pal.sapBullet);

                NormalizeDrawResult result = Placement.normalizeDrawArea(Blocks.air, schemX, schemY, cursorX, cursorY, false, 0, 1f);

                Tmp.r1.set(result.x, result.y, result.x2 - result.x, result.y2 - result.y);

                for(BlockPlan plan : player.team().data().plans){
                    Block block = content.block(plan.block);
                    if(block.bounds(plan.x, plan.y, Tmp.r2).overlaps(Tmp.r1)){
                        drawSelected(plan.x, plan.y, content.block(plan.block), Pal.sapBullet);
                    }
                }
            }
        }


        drawCommanded();

        Draw.reset();
    }

    @Override
    public void drawBottom(){
        int cursorX = tileX(input.mouseX());
        int cursorY = tileY(input.mouseY());

        //draw plan being moved
        if(splan != null){
            boolean valid = validPlace(splan.x, splan.y, splan.block, splan.rotation, splan);
            if(splan.block.rotate){
                drawArrow(splan.block, splan.x, splan.y, splan.rotation, valid);
            }

            splan.block.drawPlan(splan, allPlans(), valid);

            drawSelected(splan.x, splan.y, splan.block, getPlan(splan.x, splan.y, splan.block.size, splan) != null ? Pal.remove : Pal.accent);
        }

        //draw hover plans
        if(mode == none && !isPlacing()){
            var plan = getPlan(cursorX, cursorY);
            if(plan != null){
                drawSelected(plan.x, plan.y, plan.breaking ? plan.tile().block() : plan.block, Pal.accent);
            }
        }

        var items = selectPlans.items;
        int size = selectPlans.size;

        //draw schematic plans
        for(int i = 0; i < size; i++){
            var plan = items[i];
            plan.animScale = 1f;
            drawPlan(plan);
        }

        //draw schematic plans - over version, cached results
        for(int i = 0; i < size; i++){
            var plan = items[i];
            //use cached value from previous invocation
            drawOverPlan(plan, plan.cachedValid);
        }

//        if(player.isBuilder()){
            //draw things that may be placed soon
            if(mode == placing && block != null){
                for(int i = 0; i < linePlans.size; i++){
                    var plan = linePlans.get(i);
                    if(i == linePlans.size - 1 && plan.block.rotate){
                        drawArrow(block, plan.x, plan.y, plan.rotation);
                    }
                    drawPlan(linePlans.get(i));
                }
                linePlans.each(this::drawOverPlan);
            }else if(isPlacing()){
                if(block.rotate && block.drawArrow){
                    drawArrow(block, cursorX, cursorY, rotation);
                }
                Draw.color();
                boolean valid = validPlace(cursorX, cursorY, block, rotation);
                drawPlan(cursorX, cursorY, block, rotation);
                block.drawPlace(cursorX, cursorY, rotation, valid);

                if(block.saveConfig){
                    Draw.mixcol(!valid ? Pal.breakInvalid : Color.white, (!valid ? 0.4f : 0.24f) + Mathf.absin(Time.globalTime, 6f, 0.28f));
                    bplan.set(cursorX, cursorY, rotation, block);
                    bplan.config = block.lastConfig;
                    block.drawPlanConfig(bplan, allPlans());
                    bplan.config = null;
                    Draw.reset();
                }

                drawOverlapCheck(block, cursorX, cursorY, valid);
            }else if(mode == payloadPlace){ // FINISHME: This is actually mortifying, what the hell
                if(player.unit() instanceof Payloadc pay){
                    Payload payload = pay.hasPayload() ? pay.payloads().peek() : null;
                    if(payload != null){
                        if(payload instanceof BuildPayload build){
                            Block block = build.block();
                            boolean wasVisible = block.isVisible();
                            if (!wasVisible) state.rules.revealedBlocks.add(block);
                            drawPlan(cursorX, cursorY, block, 0);
                            if(input.keyTap(Binding.select) && validPlace(cursorX, cursorY, block, 0)){
                                if (Navigation.state == NavigationState.RECORDING) Navigation.addWaypointRecording(new PayloadDropoffWaypoint(cursorX, cursorY));
                                Navigation.follow(new WaypointPath<>(Seq.with(new PositionWaypoint(player.x, player.y), new PayloadDropoffWaypoint(cursorX, cursorY))));
                                NavigationState previousState = Navigation.state;
                                Navigation.currentlyFollowing.addListener(() -> Navigation.state = previousState);
                                mode = pay.payloads().size > 1 ? payloadPlace : none; // Disable payloadplace mode if this is the only payload.
                            }
                            if (!wasVisible) state.rules.revealedBlocks.remove(block);
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

        if(Navigation.state == NavigationState.RECORDING){
            if(input.keyTap(Binding.place_waypoint) && scene.getKeyboardFocus() == null){
                Navigation.addWaypointRecording(Pools.obtain(PositionWaypoint.class, PositionWaypoint::new).set(player.x, player.y));
            }
        }

        if(input.keyTap(Binding.invisible_units) && scene.getKeyboardFocus() == null){
            if (input.ctrl()) {
                hidingFog = !hidingFog;
                renderer.updateAllDarkness();
            }
            else if (input.shift()) hidingAirUnits = !hidingAirUnits;
            else hidingUnits = !hidingUnits;
        }

        if(input.keyTap(Binding.show_reactor_and_dome_ranges)){
            settings.put("showreactors", !settings.getBool("showreactors"));
            settings.put("showdomes", !settings.getBool("showdomes"));
        }

        if(input.keyTap(Binding.show_turret_ranges) && scene.getKeyboardFocus() == null){
            if (input.shift()) showingOverdrives = !showingOverdrives;
            else if (input.ctrl() && settings.getBool("allowinvturrets")) showingInvTurrets = !showingInvTurrets;
            else if (input.alt()) showingAllyTurrets = !showingAllyTurrets;
            else showingTurrets = !showingTurrets;
        }

        if(input.keyTap(Binding.show_massdriver_configs)){
            showingMassDrivers = !showingMassDrivers;
        }

        if(input.keyTap(Binding.hide_blocks) && scene.getKeyboardFocus() == null){
            if (input.shift()) hidingPlans = !hidingPlans;
            else hidingBlocks = !hidingBlocks;
        }

        if(input.keyTap(Binding.stop_following_path) && scene.getKeyboardFocus() == null){
            Navigation.stopFollowing();
        }

        if(input.keyTap(Binding.auto_build) && scene.getKeyboardFocus() == null){
            if(input.shift()) { // Sort build plans on shift + ; FINISHME: Surely there are no off by 1 errors... right?
                var plans = player.unit().plans;
                int head = Reflect.get(plans, "head"), tail = Reflect.<Integer>get(plans, "tail") - 1;
//                Sort.instance().sort(plans.values, Structs.comparingFloat(p -> p.dst2(player)), Math.min(head, tail), Math.max(head, tail) + 1); This was too good for the game.
                new Toast(3).add("@client.sortedplans");
            }
            else Navigation.follow(new BuildPath());
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

        if(input.keyTap(Binding.toggle_auto_target) && scene.getKeyboardFocus() == null && selectPlans.isEmpty()){
            if (input.shift()) { // Toggle auto transfer
                AutoTransfer.enabled ^= true;
                settings.put("autotransfer", AutoTransfer.enabled);
                new Toast(1).add(bundle.get("client.autotransfer") + ": " + bundle.get(AutoTransfer.enabled ? "mod.enabled" : "mod.disabled"));
            } else { // Toggle auto target
                player.shooting = false;
                settings.put("autotarget", !settings.getBool("autotarget"));
                new Toast(1).add(bundle.get("setting.autotarget.name") + ": " + bundle.get((settings.getBool("autotarget") ? "mod.enabled" : "mod.disabled")));
            }
        }

        boolean locked = locked();
        boolean panCam = false;
        float camSpeed = (!Core.input.keyDown(Binding.boost) ? panSpeed : panBoostSpeed) * Time.delta;

        if(input.keyTap(Binding.navigate_to_camera) && scene.getKeyboardFocus() == null){
            if(selectPlans.any() == input.shift() && !input.ctrl()) Navigation.navigateTo(input.mouseWorld()); // Z to nav to cursor (SHIFT + Z when placing schem)
            else if (selectPlans.isEmpty()){ // SHIFT + Z to view lastSentPos, double tap to nav there, special case for logic viruses as well (does nothing when placing schem)
                if(input.shift()) {
                    if (Time.timeSinceMillis(lastShiftZ) < 400) Navigation.navigateTo(lastSentPos.cpy().scl(tilesize));
                    else Spectate.INSTANCE.spectate(lastSentPos.cpy().scl(tilesize));
                } else if(input.ctrl()) {
                    if (Time.timeSinceMillis(lastShiftZ) < 400) Navigation.navigateTo(lastCorePos.cpy().scl(tilesize));
                    else Spectate.INSTANCE.spectate(lastCorePos.cpy().scl(tilesize)); // reusing lastShiftZ should be fine since its a small interval welp
                }
                lastShiftZ = Time.millis();

                if(Time.timeSinceMillis(lastVirusWarnTime) < 3000 && lastVirusWarning != null && world.tile(lastVirusWarning.pos()).build == lastVirusWarning){ // Logic virus
                    virusBuild = lastVirusWarning; // Store this build in its own var so it isn't overwritten
                    lastVirusWarning = null;

                    virusBuild.configure(LogicBlock.compress("end\n" + virusBuild.code, virusBuild.relativeConnections())); // Disable the block while we look into it
                    try{Vars.ui.logic.show(virusBuild.code, virusBuild.executor, virusBuild.block.privileged, code -> virusBuild.configure(LogicBlock.compress(code, virusBuild.relativeConnections())));}catch(Exception ignored){} // Inspect the code
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

        if(Core.settings.getBool("returnonmove") && ((!input.keyDown(Binding.freecam_modifier) && (Math.abs(Core.input.axis(Binding.move_x)) > 0 || Math.abs(Core.input.axis(Binding.move_y)) > 0)) || input.keyDown(Binding.mouse_move)) && !scene.hasField()){
            panning = false;
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

        if(!locked){
            if(((player.dead() || state.isPaused()) && !ui.chatfrag.shown()) && !scene.hasField() && !scene.hasDialog()){
                if(input.keyDown(Binding.mouse_move)){
                    panCam = true;
                }

                Core.camera.position.add(Tmp.v1.setZero().add(Core.input.axis(Binding.move_x), Core.input.axis(Binding.move_y)).nor().scl(camSpeed));
            }else if(!player.dead() && !panning){
                //TODO do not pan
                Team corePanTeam = state.won ? state.rules.waveTeam : player.team();
                Position coreTarget = state.gameOver && !state.rules.pvp && corePanTeam.data().lastCore != null ? corePanTeam.data().lastCore : null;
                Core.camera.position.lerpDelta(coreTarget != null ? coreTarget : player, Core.settings.getBool("smoothcamera") ? 0.08f : 1f);
            }

            if(panCam){
                Core.camera.position.x += Mathf.clamp((Core.input.mouseX() - Core.graphics.getWidth() / 2f) * panScale, -1, 1) * camSpeed;
                Core.camera.position.y += Mathf.clamp((Core.input.mouseY() - Core.graphics.getHeight() / 2f) * panScale, -1, 1) * camSpeed;
            }
        }

        shouldShoot = !locked;
        Tile cursor = tileAt(Core.input.mouseX(), Core.input.mouseY());

        if(!locked && block == null && !scene.hasField() &&
                //disable command mode when player unit can boost and command mode binding is the same
                !(!player.dead() && player.unit().type.canBoost && keybinds.get(Binding.command_mode).key == keybinds.get(Binding.boost).key)){
            if(settings.getBool("commandmodehold")){
                commandMode = input.keyDown(Binding.command_mode);
            }else if(input.keyTap(Binding.command_mode)){
                commandMode = !commandMode;
            }
        }else{
            commandMode = false;
        }

        //validate commanding units
        selectedUnits.removeAll(u -> !u.isCommandable() || !u.isValid());

        if(commandMode && input.keyTap(Binding.select_all_units) && !scene.hasField() && !scene.hasDialog()){
            selectedUnits.clear();
            commandBuildings.clear();
            for(var unit : player.team().data().units){
                if(unit.isCommandable()){
                    selectedUnits.add(unit);
                }
            }
        }

        if(commandMode && input.keyTap(Binding.select_all_unit_factories) && !scene.hasField() && !scene.hasDialog()){
            selectedUnits.clear();
            commandBuildings.clear();
            for(var build : player.team().data().buildings){
                if(build.block.commandable){
                    commandBuildings.add(build);
                }
            }
        }

        if(!scene.hasMouse() && !locked){
            // FINISHME: Move this into its own method, its huge
            Unit sl;
            if(Core.input.keyDown(Binding.tile_actions_menu_modifier) && Core.input.keyTap(Binding.select) && cursor != null && ((sl = selectedUnit(true)) == null || sl instanceof BlockUnitUnit)){ // Tile actions / alt click menu
                int itemHeight = 30;
                Table table = new Table(Tex.buttonTrans);
                table.setWidth(400);
                table.margin(10);
                table.fill();
                table.touchable = Touchable.enabled; // This is needed
                table.defaults().height(itemHeight).padTop(5).fillX();
                try {
                    table.add(cursor.block().localizedName + ": (" + cursor.x + ", " + cursor.y + ")").height(itemHeight).left().growX().fillY().padTop(-5);
                } catch (Exception e) { ui.chatfrag.addMessage(e.getMessage(), null, Color.scarlet, "", e.getMessage()); }

                table.row().fill();
                table.button("@client.log", () -> { // Tile Logs
                    TileRecords.INSTANCE.show(cursor);
                    table.remove();
                });

                table.row().fill();
                table.button("@client.autotransfer", () -> { // Auto transfer
                    AutoTransfer.enabled ^= true;
                    settings.put("autotransfer", AutoTransfer.enabled);
                    new Toast(1).add(bundle.get("client.autotransfer") + ": " + bundle.get(AutoTransfer.enabled ? "mod.enabled" : "mod.disabled"));
                    table.remove();
                }).disabled(b -> state.rules.pvp && ClientUtils.io());

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
                    dialog.cont.add(new TextButton("@client.path.record")).growX().get().clicked(() -> {
                        Navigation.startRecording(); dialog.hide();});
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

            // if(mode != placing && ((input.ctrl() || input.shift()) && Core.input.keyTap(Binding.select)) && block == null && state.rules.possessionAllowed){
            // if(((input.keyDown(Binding.control) || input.alt()) && Core.input.keyTap(Binding.select) && state.rules.possessionAllowed) && block == null){ // Hmm?
                // Unit on = selectedUnit(true);
            // TODO: Merge recheck: I have no clue if I merged this correctly, what even does this code do
            if((input.keyDown(Binding.control) || input.shift()) && input.keyTap(Binding.select)){
                Unit on = selectedUnit(true);
                var build = selectedControlBuild();
                boolean hidingAirUnits = ClientVars.hidingAirUnits;
                Vec2 mouseWorld;
                if(on != null){
                    // FINISHME: This belongs in its own method, its also very messy
                    if (input.keyDown(Binding.control) && on.isAI() && state.rules.possessionAllowed) { // Ctrl + click: control unit
                        Call.unitControl(player, on);
                        shouldShoot = false;
                        recentRespawnTimer = 1f;
                    } else if ((input.keyDown(Binding.control) || input.shift()) && on.isPlayer()) {
                        Navigation.follow(new AssistPath(on.getPlayer(),
                                input.shift() && input.alt() ? AssistPath.Type.FreeMove :
                                input.keyDown(Binding.control) && input.alt() ? AssistPath.Type.BuildPath :
                                input.keyDown(Binding.control) ? AssistPath.Type.Cursor : AssistPath.Type.Regular,
                                Core.settings.getBool("circleassist")));
                        shouldShoot = false;
                    }else if(on.controller() instanceof LogicAI ai && ai.controller != null) { // Alt + click logic unit: spectate processor
                        Spectate.INSTANCE.spectate(ai.controller);
                        shouldShoot = false;
                    }
                }else if((on = Units.closestOverlap((mouseWorld = Core.input.mouseWorld()).x, mouseWorld.y, tilesize * 8f,
                        u -> (!u.isFlying() || !hidingAirUnits) && mouseWorld.within(u, u.hitSize))) != null && on.controller() instanceof LogicAI ai && ai.controller != null){
                    // This condition is meant to catch logic-controlled units of any team
                    Spectate.INSTANCE.spectate(ai.controller);
                    shouldShoot = false;
                }else if(build != null && input.keyDown(Binding.control)){
                    Call.buildingControlSelect(player, build);
                    recentRespawnTimer = 1f;
                }
            }
        }

        if(!player.dead() && !state.isPaused() && !scene.hasField() && !locked){
            updateMovement(player.unit());

            if(Core.input.keyTap(Binding.respawn) && !scene.hasDialog()){
                controlledType = null;
                recentRespawnTimer = 1f;
                Call.unitClear(player);
            }
        }

        if(Core.input.keyRelease(Binding.select)){
            player.shooting = false;
        }

        if(state.isGame() && !scene.hasDialog() && !scene.hasField()){
            if(Core.input.keyTap(Binding.minimap)) ui.minimapfrag.toggle();
            if(Core.input.keyTap(Binding.planet_map) && state.isCampaign()) ui.planet.toggle();
            if(Core.input.keyTap(Binding.research) && state.isCampaign()) ui.research.toggle();
        }

        if(state.isMenu() || Core.scene.hasDialog()) return;

        if(input.keyTap(Binding.reset_camera) && scene.getKeyboardFocus() == null && (cursor == null || cursor.build == null || !(cursor.build.block.rotate && cursor.build.block.quickRotate && cursor.build.interactable(player.team()))) && !input.alt()){
            panning = false;
            Spectate.INSTANCE.setPos(null);
        }

        //zoom camera
        if((!Core.scene.hasScroll() || Core.input.keyDown(Binding.diagonal_placement)) && !ui.chatfrag.shown() && !ui.consolefrag.shown() && Math.abs(Core.input.axisTap(Binding.zoom)) > 0
            && !Core.input.keyDown(Binding.rotateplaced) && (Core.input.keyDown(Binding.diagonal_placement) ||
                !keybinds.get(Binding.zoom).equals(keybinds.get(Binding.rotate)) || ((!player.isBuilder() || !isPlacing() || !block.rotate) && selectPlans.isEmpty()))){
            renderer.scaleCamera(Core.input.axisTap(Binding.zoom));
        }

        if(Core.input.keyTap(Binding.select) && !Core.scene.hasMouse()){
            Tile selected = world.tileWorld(input.mouseWorldX(), input.mouseWorldY());
            if(selected != null){
                Call.tileTap(player, selected);
            }
        }

        if(player.dead() || locked){
            cursorType = SystemCursor.arrow;
            if(!Core.scene.hasMouse()){
                Core.graphics.cursor(cursorType);
            }
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

        if(isPlacing() /*&& player.isBuilder()*/){
            cursorType = SystemCursor.hand;
            selectScale = Mathf.lerpDelta(selectScale, 1f, 0.2f);
        }else{
            selectScale = 0f;
        }

        if(!Core.input.keyDown(Binding.diagonal_placement) && Math.abs((int)Core.input.axisTap(Binding.rotate)) > 0){
            rotation = Mathf.mod(rotation + (int)Core.input.axisTap(Binding.rotate), 4);

            if(splan != null){
                splan.rotation = Mathf.mod(splan.rotation + (int)Core.input.axisTap(Binding.rotate), 4);
            }

            if(isPlacing() && mode == placing){
                updateLine(selectX, selectY);
            }else if(!selectPlans.isEmpty() && !ui.chatfrag.shown()){
                rotatePlans(selectPlans, Mathf.sign(Core.input.axisTap(Binding.rotate)));
            }
        }

        if(cursor != null){
            if(cursor.build != null){
                cursorType = cursor.build.getCursor();
            }

            if((isPlacing() /*&& player.isBuilder()*/) || !selectPlans.isEmpty()){
                cursorType = SystemCursor.hand;
            }

            if(!isPlacing() && canMine(cursor)){
                cursorType = ui.drillCursor;
            }

            if(commandMode && selectedUnits.any() && ((cursor.build != null && !cursor.build.inFogTo(player.team()) && cursor.build.team != player.team()) || (selectedEnemyUnit(input.mouseWorldX(), input.mouseWorldY()) != null))){
                cursorType = ui.targetCursor;
            }

            if(getPlan(cursor.x, cursor.y) != null && mode == none){
                cursorType = SystemCursor.hand;
            }

            if(canTapPlayer(Core.input.mouseWorld().x, Core.input.mouseWorld().y)){
                cursorType = ui.unloadCursor;
            }

            if(!ui.chatfrag.shown() && cursor.build != null && cursor.interactable(player.team()) && !isPlacing() && Math.abs(Core.input.axisTap(Binding.rotate)) > 0 && Core.input.keyDown(Binding.rotateplaced) && cursor.block().rotate && cursor.block().quickRotate){
                Call.rotateBlock(player, cursor.build, Core.input.axisTap(Binding.rotate) > 0);
            }
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

        selectPlans.clear();
        selectPlans.addAll(schematics.toPlans(schem, schematicX, schematicY));
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

        table.button(Icon.paste, Styles.clearNonei, () -> {
            ui.schematics.show();
        }).tooltip("@schematics");

        table.button(Icon.book, Styles.clearNonei, () -> {
            ui.database.show();
        }).tooltip("@database");

        table.button(Icon.map, Styles.clearNonei, () -> {
            if (state.isCampaign() && !Vars.net.client()) ui.planet.show();
            else MarkerDialog.INSTANCE.show();
        }).tooltip(t -> t.background(Styles.black6).margin(4f).label(() -> state.isCampaign() ? "@planetmap" : "Map Markers"));

        table.button(Icon.tree, Styles.clearNonei, () -> {
            ui.research.show();
        }).visible(() -> state.isCampaign()).tooltip("@research");
    }

    void pollInput(){
        if(scene.hasField()) return;

        Tile selected = tileAt(Core.input.mouseX(), Core.input.mouseY());
        int cursorX = tileX(Core.input.mouseX());
        int cursorY = tileY(Core.input.mouseY());
        int rawCursorX = World.toTile(Core.input.mouseWorld().x), rawCursorY = World.toTile(Core.input.mouseWorld().y);

        //automatically pause building if the current build queue is empty
        if(Core.settings.getBool("buildautopause") && isBuilding && !player.unit().isBuildingIgnoreNetworking()){
            isBuilding = false;
            buildWasAutoPaused = true;
        }

        if(!selectPlans.isEmpty()){
            int shiftX = rawCursorX - schematicX, shiftY = rawCursorY - schematicY;

            selectPlans.each(s -> {
                s.x += shiftX;
                s.y += shiftY;
            });

            schematicX += shiftX;
            schematicY += shiftY;
        }

        if(Core.input.keyTap(Binding.deselect) && !isPlacing() && !commandMode){
            player.unit().mineTile = null;
        }

        if(Core.input.keyTap(Binding.clear_building)){
            if(!Core.input.shift()){
                Player.persistPlans.clear();
                player.unit().clearBuilding();
            }
            else frozenPlans.clear();
        }

        if((Core.input.keyTap(Binding.schematic_select) || Core.input.keyTap(Binding.rebuild_select)) && !Core.scene.hasKeyboard() && mode != breaking){
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
            if(!Core.input.shift()) {
                lastSchematic = null;
                selectPlans.clear();
            }
        }

        if( !Core.scene.hasKeyboard() && selectX == -1 && selectY == -1 && schemX != -1 && schemY != -1){
            if(Core.input.keyRelease(Binding.schematic_select)){
                lastSchematic = schematics.create(schemX, schemY, rawCursorX, rawCursorY);
                useSchematic(lastSchematic);
                if(selectPlans.isEmpty()){
                    lastSchematic = null;
                }
                schemX = -1;
                schemY = -1;
            }else if(input.keyRelease(Binding.rebuild_select)){
                //TODO rebuild!!!

                NormalizeResult result = Placement.normalizeArea(schemX, schemY, rawCursorX, rawCursorY, rotation, false, 999999999);
                Tmp.r1.set(result.x * tilesize, result.y * tilesize, (result.x2 - result.x) * tilesize, (result.y2 - result.y) * tilesize);

                Iterator<BlockPlan> broken = player.team().data().plans.iterator();
                while(broken.hasNext()){
                    BlockPlan plan = broken.next();
                    Block block = content.block(plan.block);
                    if(block.bounds(plan.x, plan.y, Tmp.r2).overlaps(Tmp.r1)){
                        player.unit().addBuild(new BuildPlan(plan.x, plan.y, plan.rotation, content.block(plan.block), plan.config));
                    }
                }

                schemX = -1;
                schemY = -1;
            }
        }

        if(!selectPlans.isEmpty()){
            if(Core.input.keyTap(Binding.schematic_flip_x) && !input.shift()){ // Don't rotate when shift is held, if shift is held navigate instead.
                flipPlans(selectPlans, true);
            }

            if(Core.input.keyTap(Binding.schematic_flip_y)){
                flipPlans(selectPlans, false);
            }
        }

        if(splan != null){
            float offset = ((splan.block.size + 2) % 2) * tilesize / 2f;
            int x = (int)((Core.input.mouseWorld().x + offset) / tilesize);
            int y = (int)((Core.input.mouseWorld().y + offset) / tilesize);
            if (splan.x != x || splan.y != y) splanMoved = true;
            splan.x = x;
            splan.y = y;
        }

        if(block == null || mode != placing){
            linePlans.clear();
        }

        if(Core.input.keyTap(Binding.pause_building)){
            if (Core.input.shift()) isFreezeQueueing = !isFreezeQueueing;
            else if (Core.input.ctrl()) {
                Seq<BuildPlan> temp = frozenPlans.copy();
                flushPlans(temp, false, false, true);
            }
            else {
                isBuilding = !isBuilding;
                buildWasAutoPaused = false;
    
                if(isBuilding){
                    player.shooting = false;
                }
            }
        }

        if((cursorX != lastLineX || cursorY != lastLineY) && isPlacing() && mode == placing){
            updateLine(selectX, selectY);
            lastLineX = cursorX;
            lastLineY = cursorY;
        }

        //select some units
        if(Core.input.keyRelease(Binding.select) && commandRect){
            selectUnitsRect();
        }

        if(Core.input.keyTap(Binding.select) && !Core.scene.hasMouse()){
            tappedOne = false;
            BuildPlan plan = getPlan(cursorX, cursorY);

            if(Core.input.keyDown(Binding.break_block)){
                mode = none;
            }else if(selectPlans.any()){
                flushPlans(selectPlans, isFreezeQueueing, input.ctrl(), isFreezeQueueing);
            }else if(!selectPlans.isEmpty()){
                flushPlans(selectPlans);
            }else if(isPlacing()){
                selectX = cursorX;
                selectY = cursorY;
                lastLineX = cursorX;
                lastLineY = cursorY;
                mode = placing;
                updateLine(selectX, selectY);
            }else if(plan != null && !plan.breaking && mode == none && !plan.initialized){
                splan = plan;
            }else if(plan != null && plan.breaking){
                deleting = true;
            }else if(commandMode){
                commandRect = true;
                commandRectX = input.mouseWorldX();
                commandRectY = input.mouseWorldY();
            }else if(!checkConfigTap() && selected != null){
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
        }else if(Core.input.keyTap(Binding.deselect) && !selectPlans.isEmpty()){
            selectPlans.clear();
            lastSchematic = null;
        }else if(Core.input.keyTap(Binding.break_block) && !Core.scene.hasMouse() && !commandMode/*&& player.isBuilder()*/){
            //is recalculated because setting the mode to breaking removes potential multiblock cursor offset
            deleting = false;
            mode = Core.input.shift() ? freezing : Core.input.ctrl() ? dequeue : breaking;
            selectX = tileX(Core.input.mouseX());
            selectY = tileY(Core.input.mouseY());
            schemX = rawCursorX;
            schemY = rawCursorY;
        }

        if(Core.input.keyDown(Binding.select) && mode == none && !isPlacing() && deleting){
            var plan = getPlan(cursorX, cursorY);
            if(plan != null && plan.breaking){
                player.unit().plans().remove(plan);
                frozenPlans.remove(plan);
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

        if(mode == breaking || mode == freezing){
            mode = Core.input.shift() ?  freezing : Core.input.ctrl() ? dequeue : breaking;
        }

        if(Core.input.keyRelease(Binding.break_block) && Core.input.keyDown(Binding.schematic_select) && mode == breaking){
            lastSchematic = schematics.create(schemX, schemY, rawCursorX, rawCursorY);
            schemX = -1;
            schemY = -1;
        }

        if(Core.input.keyRelease(Binding.break_block) || Core.input.keyRelease(Binding.select)){

            if(mode == placing && block != null){ //touch up while placing, place everything in selection
                // Why do we even need reversed build plans - SBytes 17/08/2022
//                if(input.keyDown(Binding.boost)){
//                    flushPlansReverse(linePlans);
//                }else{
                    flushPlans(linePlans, isFreezeQueueing, input.ctrl(), isFreezeQueueing);
//                }

                linePlans.clear();
                Events.fire(new LineConfirmEvent());
            }else if(mode == breaking){ //touch up while breaking, break everything in selection
                removeSelection(selectX, selectY, cursorX, cursorY, false, /*!Core.input.keyDown(Binding.schematic_select) ? maxLength :*/ Vars.maxSchematicSize, isFreezeQueueing);
                
                if(lastSchematic != null){
                    useSchematic(lastSchematic);
                    lastSchematic = null;
                }
            }else if(mode == freezing){
                freezeSelection(selectX, selectY, cursorX, cursorY, Vars.maxSchematicSize);
            } else if (mode == dequeue) {
                removeSelectionPlans(selectX, selectY, cursorX, cursorY, Vars.maxSchematicSize);
            }
            selectX = -1;
            selectY = -1;

            tryDropItems(selected == null ? null : selected.build, Core.input.mouseWorld().x, Core.input.mouseWorld().y);

            if(splan != null){
                if(getPlan(splan.x, splan.y, splan.block.size, splan) != null){
                    player.unit().plans().remove(splan, true);
                }
                if(!splanMoved) player.unit().addBuild(splan, false); // Add the plan to the top of the queue
                splan = null;
                splanMoved = false;
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
    public boolean tap(float x, float y, int count, KeyCode button){
        if(scene.hasMouse() || !commandMode) return false;

        tappedOne = true;

        //click: select a single unit
        if(button == KeyCode.mouseLeft){
            if(count >= 2){
                selectTypedUnits();
            }else{
                tapCommandUnit();
            }

        }

        return super.tap(x, y, count, button);
    }

    @Override
    public boolean touchDown(float x, float y, int pointer, KeyCode button){
        if(scene.hasMouse() || !commandMode) return false;

        if(button == KeyCode.mouseRight){
            commandTap(x, y);
        }

        return super.touchDown(x, y, pointer, button);
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
            lastSchematic = null;
            droppingItem = false;
            mode = none;
            block = null;
            splan = null;
            selectPlans.clear();
        }
    }

    protected void updateMovement(Unit unit){ // Heavily modified to support navigation
        boolean omni = unit.type.omniMovement;

        float speed = unit.speed();
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
            boolean aimCursor = omni && player.shooting && unit.type.hasWeapons() && unit.type.faceTarget && !boosted;

            if(aimCursor){
                unit.lookAt(mouseAngle);
            }else{
                unit.lookAt(unit.prefRotation());
            }

//            unit.movePref(movement); Client replaces this with the line below
            if (Core.settings.getBool("zerodrift") && movement.epsilonEquals(0, 0)) unit.vel().setZero();
            else if(Core.settings.getBool("decreasedrift") && unit.vel().len() > 3.5 && movement.epsilonEquals(0, 0))
                unit.vel().set(unit.vel().scl(0.95f));
            else unit.moveAt(movement);

            unit.aim(Core.input.mouseWorld());

            player.boosting = unit.type.canBoost && Core.settings.getBool("autoboost") ^ input.keyDown(Binding.boost); // If auto-boost, invert the behavior of the boost key

            if ((!Core.input.keyDown(Binding.select) || block != null) && shouldShoot) AutoShootKt.autoShoot();
        } else if (Navigation.currentlyFollowing instanceof MinePath mp && mp.getNewGame() && !movement.isZero()) Navigation.stopFollowing(); // Stop automatic mining on player move
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
    }
}
