package mindustry.input;

import arc.*;
import arc.Graphics.*;
import arc.Graphics.Cursor.*;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.input.*;
import arc.input.KeyCode.*;
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
import mindustry.client.communication.*;
import mindustry.client.navigation.*;
import mindustry.client.navigation.waypoints.*;
import mindustry.client.ui.*;
import mindustry.client.utils.*;
import mindustry.core.*;
import mindustry.entities.*;
import mindustry.entities.units.*;
import mindustry.game.EventType.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;
import mindustry.ui.fragments.*;
import mindustry.world.*;
import mindustry.world.blocks.logic.*;
import mindustry.world.blocks.payloads.*;
import mindustry.world.blocks.storage.*;

import static arc.Core.*;
import static arc.Core.camera;
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
    /** Whether player is currently deleting removal plans. */
    public boolean deleting = false, shouldShoot = false, panning = false, movedPlan = false;
    /** Mouse pan speed. */
    public float panScale = 0.005f, panSpeed = 4.5f, panBoostSpeed = 15f;
    /** Delta time between consecutive clicks. */
    public long selectMillis = 0;
    /** Previously selected tile. */
    public Tile prevSelected;

    /** Most recently selected control group by index */
    public int lastCtrlGroup;
    /** Time of most recent control group selection */
    public long lastCtrlGroupSelectMillis;

    /** Time of most recent payload pickup/drop key press*/
    public long lastPayloadKeyTapMillis;
    /** Time of most recent payload pickup/drop key hold*/
    public long lastPayloadKeyHoldMillis;

    private float buildPlanMouseOffsetX, buildPlanMouseOffsetY;
    private boolean changedCursor, pressedCommandRect;


    // Client Vars
    private long lastShiftZ;
    /** Position where the player started drag-selecting. Overlaps with selectX/Y but is only used by client. */
    public float dragX = Float.NaN, dragY;
    /** Whether the player has provided movement input since some other code set it to false */
    public boolean moved = false;

    @Override
    public void reset(){
        super.reset();
        shouldShoot = false;
        deleting = false;
    }

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
                            str.append("\n").append(bundle.format("client.freezequeueing", Binding.pauseBuilding.value.key.toString()));
                        }
                        if(!isBuilding && !isBuildingLock && !settings.getBool("buildautopause") && !isBuildingIgnoreNetworking()){
                            str.append("\n").append(bundle.format("enablebuilding", Binding.pauseBuilding.value.key.toString()));
                        }else if(isBuildingIgnoreNetworking() || !Player.persistPlans.isEmpty()){
                            str.append("\n")
                                .append(bundle.format(isBuilding ? "pausebuilding" : "resumebuilding", Binding.pauseBuilding.value.key))
                                .append("\n").append(bundle.format("cancelbuilding", Binding.clearBuilding.value.key.toString()))
                                .append("\n").append(bundle.format("selectschematic", Binding.schematicSelect.value.key.toString()));
                        }
                        if(isBuildingIgnoreNetworking() || dispatchingBuildPlans){
                            str.append("\n").append(bundle.format(dispatchingBuildPlans ? "client.stopsendbuildplans" : "client.sendbuildplans", Binding.sendBuildQueue.value.key.toString()));
                        }
                        if(hidingUnits || hidingAirUnits){
                            str.append("\n").append(bundle.format("client.toggleunits", Binding.invisibleUnits.value.key.toString()));
                            str.append("\n").append(bundle.format("client.toggleairunits", Binding.invisibleUnits.value.key.toString()));
                        }
                        if(showingTurrets){
                            str.append("\n").append(bundle.format("client.toggleenemyturrets", Binding.showTurretRanges.value.key.toString()));
                        }
                        if (showingAllyTurrets) {
                            str.append("\n").append(bundle.format("client.toggleallyturrets", Binding.showTurretRanges.value.key.toString()));
                        }
                        if(showingInvTurrets){
                            str.append("\n").append(bundle.format("client.toggleinvturrets", Binding.showTurretRanges.value.key.toString()));
                        }
                        if(showingOverdrives){
                            str.append("\n").append(bundle.format("client.toggleoverdrives", Binding.showTurretRanges.value.key.toString()));
                        }
                        if(showingMassDrivers){
                            str.append("\n").append(bundle.format("client.togglemassdrivers", Binding.showMassdriverConfigs.value.key.toString()));
                        }
                        if(hidingBlocks){
                            str.append("\n").append(bundle.format("client.toggleblocks", Binding.hideBlocks.value.key.toString()));
                        }
                        if (hidingPlans) {
                            str.append("\n").append(bundle.format("client.toggleplans", Binding.hideBlocks.value.key.toString()));
                        }
                        if(Navigation.state == NavigationState.RECORDING){
                            str.append("\n").append(bundle.format("client.waypoint", Binding.placeWaypoint.value.key.toString()));
                        }else if(Navigation.state == NavigationState.FOLLOWING){
                            str.append("\n").append(bundle.format("client.stoppath", Binding.stopFollowingPath.value.key.toString()));
                        }
                        if(panning){
                            str.append("\n").append(bundle.format("client.resetcamera", Binding.resetCamera.value.key.toString()));
                        }

                        if(selectPlans.any()){ // Any selection
                            str.append("\n").append(bundle.format("schematic.flip", Binding.schematicFlipX.value.key.toString(), Binding.schematicFlipY.value.key.toString()));
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
        if(cursorType != SystemCursor.arrow && scene.hasMouse()){
           graphics.cursor(cursorType = SystemCursor.arrow);
        }

        Lines.stroke(1f);
        int cursorX = tileX(Core.input.mouseX());
        int cursorY = tileY(Core.input.mouseY());

        //draw freezing selection
        if(mode == freezing){
            drawFreezeSelection(selectX, selectY, cursorX, cursorY, Vars.maxSchematicSize);
        }
        //draw break selection
        if(mode == breaking){
            drawBreakSelection(selectX, selectY, cursorX, cursorY, /*!(Core.input.keyDown(Binding.schematicSelect) && schemX != -1 && schemY != -1) ? maxLength :*/ Vars.maxSchematicSize, false);
        }
        //draw dequeueing selection
        if (mode == dequeue){
            drawSelection(selectX, selectY, cursorX, cursorY, Vars.maxSchematicSize, Color.gray, Color.white, true);
        }

        if(!Core.scene.hasKeyboard() && mode != breaking && mode != freezing && mode != dequeue){

            if(Core.input.keyDown(Binding.schematicSelect) && schemX != -1 && schemY != -1){
                drawSelection(schemX, schemY, cursorX, cursorY, Vars.maxSchematicSize);
            }else if(Core.input.keyDown(Binding.rebuildSelect)){
                drawRebuildSelection(schemX, schemY, cursorX, cursorY);
            }
        }

        Draw.reset();
    }

    @Override
    public void drawBottom(){
        int cursorX = tileX(input.mouseX());
        int cursorY = tileY(input.mouseY());

        //draw plan being moved
        if(splan != null){
            boolean valid = validPlace(splan.x, splan.y, splan.block, splan.rotation, splan);
            if(splan.block.rotate && splan.block.drawArrow){
                drawArrow(splan.block, splan.x, splan.y, splan.rotation, valid);
            }

            splan.block.drawPlan(splan, allPlans, valid);

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
        var alpha = Core.settings.getInt("schemalpha", 100) / 100f;

        //draw schematic plans
        for(int i = 0; i < size; i++){
            var plan = items[i];
            plan.animScale = 1f;
            drawPlan(plan, plan.cachedValid = validPlace(plan.x, plan.y, plan.block, plan.rotation), alpha);
        }

        //draw schematic plans - over version, cached results
        for(int i = 0; i < size; i++){
            var plan = items[i];
            //use cached value from previous invocation
            drawOverPlan(plan, plan.cachedValid, alpha);
        }

//        if(player.isBuilder()){
            //draw things that may be placed soon
            if(mode == placing && block != null){
                for(int i = 0; i < linePlans.size; i++){
                    var plan = linePlans.get(i);
                    if(i == linePlans.size - 1 && plan.block.rotate && plan.block.drawArrow){
                        drawArrow(block, plan.x, plan.y, plan.rotation);
                    }
                    drawPlan(linePlans.get(i));
                }
                linePlans.each(this::drawOverPlan);
            }else if(isPlacing()){
                int rot = block == null ? rotation : block.planRotation(rotation);
                if(block.rotate && block.drawArrow){
                    drawArrow(block, cursorX, cursorY, rot);
                }
                Draw.color();
                boolean valid = validPlace(cursorX, cursorY, block, rot);
                drawPlan(cursorX, cursorY, block, rot);
                block.drawPlace(cursorX, cursorY, rot, valid);

                if(block.saveConfig){
                    Draw.mixcol(!valid ? Pal.breakInvalid : Color.white, (!valid ? 0.4f : 0.24f) + Mathf.absin(Time.globalTime, 6f, 0.28f));
                    bplan.set(cursorX, cursorY, rot, block);
                    bplan.config = block.lastConfig;
                    block.drawPlanConfig(bplan, allPlans);
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

    private enum JSBindingOption {

        shift(() -> input.shift(), "keybind1shiftcommand", "No JS configured for Shift+@, go to client settings to add a script to run"), // FINISHME: Bundle
        ctrl(() -> input.ctrl(), "keybind1ctrlcommand", "No JS configured for Ctrl+@, go to client settings to add a script to run"),
        alt(() -> input.alt(), "keybind1altcommand", "No JS configured for Alt+@, go to client settings to add a script to run"),
        none(() -> true, "keybind1command", "No JS configured for keybind @, go to client settings to add a script to run"),
        ;

        public final Boolp check;
        public final String settingsKey;
        public final String message;

        JSBindingOption(Boolp check, String settingsKey, String message){
            this.check = check;
            this.settingsKey = settingsKey;
            this.message = message;
        }
    }

    @Override
    public void update(){ // FINISHME v8: This has been cleaned up in v8. We should mirror those changes and ideally have an updateClient() or something for handling client keybinds.
        super.update();

        if(Core.input.keyTap(Binding.playerList) && (scene.getKeyboardFocus() == null || scene.getKeyboardFocus().isDescendantOf(ui.listfrag.content) || scene.getKeyboardFocus().isDescendantOf(ui.minimapfrag.elem))){
            Core.settings.getBoolOnce("playerlist-spectate-moved", () ->
                ui.showOkText("Spectate", "The spectate button has moved. Spectate a player by clicking the image of their unit.\nThis is where it is in vanilla. Use the setting \"Outline player units in player list\" for the old visual style.", () -> {})
            );
            ui.listfrag.toggle();
        }

        conveyorPlaceNormal = input.keyDown(Binding.togglePlacementModifiers);

        if(Navigation.state == NavigationState.RECORDING){
            if(input.keyTap(Binding.placeWaypoint) && scene.getKeyboardFocus() == null){
                Navigation.addWaypointRecording(Pools.obtain(PositionWaypoint.class, PositionWaypoint::new).set(player.x, player.y));
            }
        }

        if(input.keyTap(Binding.runJS) && scene.getKeyboardFocus() == null){
            boolean ran = false;
            for(var opt : JSBindingOption.values()){
                if(opt.check.get() && !ran){
                    if(!settings.getString(opt.settingsKey, "").isEmpty()){
                        ChatFragment.handleClientCommand(Core.settings.getString(opt.settingsKey));
                        ran = true;
                    } else {
                        Vars.player.sendMessage(Strings.format(opt.message, Binding.runJS.value.key.toString()));
                    }
                }
            }
        }

        if(input.keyTap(Binding.invisibleUnits) && scene.getKeyboardFocus() == null){
            if (input.shift()) hidingAirUnits = !hidingAirUnits;
            else hidingUnits = !hidingUnits;
        }

        if(input.keyTap(Binding.showReactorAndDomeRanges)){
            settings.put("showreactors", !settings.getBool("showreactors"));
            settings.put("showdomes", !settings.getBool("showdomes"));
        }

        if(input.keyTap(Binding.showTurretRanges) && scene.getKeyboardFocus() == null){
            if (input.shift()) showingOverdrives = !showingOverdrives;
            else if (input.ctrl() && settings.getBool("allowinvturrets")) showingInvTurrets = !showingInvTurrets;
            else if (input.alt()) showingAllyTurrets = !showingAllyTurrets;
            else showingTurrets = !showingTurrets;
        }

        if(input.keyTap(Binding.showMassdriverConfigs) && scene.getKeyboardFocus() == null){
            showingMassDrivers = !showingMassDrivers;
        }

        if(input.keyTap(Binding.hideBlocks) && scene.getKeyboardFocus() == null){
            if (input.shift()) hidingPlans = !hidingPlans;
            else hidingBlocks = !hidingBlocks;
        }

        if(input.keyTap(Binding.stopFollowingPath) && scene.getKeyboardFocus() == null){
            Navigation.stopFollowing();
        }

        if(input.keyTap(Binding.autoBuild) && scene.getKeyboardFocus() == null && !player.dead()){
            if(input.shift()) {
                var plans = player.unit().plans;
                var arr = plans.toArray(BuildPlan.class); // FINISHME: Add an overload that takes an array param to avoid making a new one every time, make it use arraycopy twice instead of running get() in a loop
                Sort.instance().sort(arr, Structs.comparingFloat(p -> p.dst2(player)));
                plans.clear();
                Structs.each(plans::add, arr);
                new Toast(3).add("@client.sortedplans");
            } else Navigation.follow(new BuildPath());
        }

        if(input.keyTap(Binding.autoRepair) && scene.getKeyboardFocus() == null && (input.shift() || (player != null && player.unit() != null && player.unit().type.canHeal))){
            Navigation.follow(new RepairPath());
        }

        if(input.keyTap(Binding.autoMine) && scene.getKeyboardFocus() == null && (input.shift() || (player != null && player.unit() != null && player.unit().type.mineTier > 0))){
            Navigation.follow(new MinePath());
        }

        if(input.keyTap(Binding.toggleStrictMode) && scene.getKeyboardFocus() == null){
            settings.put("assumeunstrict", !settings.getBool("assumeunstrict"));
        }

        if(input.keyTap(Binding.toggleAutoTarget) && scene.getKeyboardFocus() == null && selectPlans.isEmpty()){
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
        boolean detached = settings.getBool("detach-camera", false);

        if(input.keyTap(Binding.navigateToCursor) && scene.getKeyboardFocus() == null){
            if(selectPlans.any() == input.shift() && !input.ctrl()) Navigation.navigateTo(input.mouseWorld()); // Z to nav to cursor (SHIFT + Z when placing schem)
            else if (selectPlans.isEmpty()){ // SHIFT + Z to view lastSentPos, double tap to nav there, special case for logic viruses as well (does nothing when placing schem)
                if(input.shift()) {
                    if (Time.timeSinceMillis(lastShiftZ) < 400) Navigation.navigateTo(lastSentPos.cpy().scl(tilesize));
                    else Spectate.INSTANCE.spectate(lastSentPos.cpy().scl(tilesize));
                } else if(input.ctrl()) {
                    if (Time.timeSinceMillis(lastShiftZ) < 400) Navigation.navigateTo(lastWarnPos.cpy().scl(tilesize));
                    else Spectate.INSTANCE.spectate(lastWarnPos.cpy().scl(tilesize)); // reusing lastShiftZ should be fine since its a small interval welp
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

        if(!scene.hasField() && !scene.hasDialog()){
            if(input.keyTap(Binding.debugHitboxes)){
                drawDebugHitboxes = !drawDebugHitboxes;
            }

            if(input.keyTap(Binding.detachCamera)){
                settings.put("detach-camera", detached = !detached);
                if(!detached){
                    panning = false;
                }
                spectating = null;
            }

            if(input.keyDown(Binding.pan)){
                panCam = true;
                panning = true;
                spectating = null;
                logicCutscene = false; // Cancel the cutscene
                if(state.gameOver && !state.rules.pvp) followGameEndPan = false;
            }

            if(Core.settings.getBool("returnonmove") && ((!input.keyDown(Binding.freecamModifier) && (Math.abs(Core.input.axis(Binding.moveX)) > 0 || Math.abs(Core.input.axis(Binding.moveY)) > 0)) || input.keyDown(Binding.mouseMove))){
                panning = false;
                spectating = null;
            }
        }

        // FINISHME(v147): Vanilla has some sort of detach keybind now, can we use that instead?
        if(input.keyDown(Binding.freecamModifier) && (input.axis(Binding.moveX) != 0f || input.axis(Binding.moveY) != 0f) && scene.getKeyboardFocus() == null){
            spectating = null;
            panning = true;
            logicCutscene = false; // Cancel the cutscene
            if(state.gameOver && !state.rules.pvp) followGameEndPan = false;
            boolean spectated = Spectate.INSTANCE.getPos() != null;
            Spectate.INSTANCE.setPos(null);
            if(spectated && ui.listfrag.shown()) ui.listfrag.rebuild();
            float speed = Time.delta;
            speed *= camera.width;
            speed /= 75f;
            camera.position.add(input.axis(Binding.moveX) * speed, input.axis(Binding.moveY) * speed);
        }

        panning |= detached;


        if(input.keyDown(Binding.dropPayload) && scene.getKeyboardFocus() == null){
            mode = payloadPlace;
        }
        if(input.keyRelease(Binding.dropPayload) && scene.getKeyboardFocus() == null){
            mode = none;
        }

        if (input.keyDown(Binding.findModifier) && input.keyRelease(Binding.find)) {
            FindDialog.INSTANCE.show();
        }

        if(!locked){
            if(((player.dead() || state.isPaused() || detached) && !ui.chatfrag.shown()) && !scene.hasField() && !scene.hasDialog()){
                if(input.keyDown(Binding.mouseMove)){
                    panCam = true;
                }

                Core.camera.position.add(Tmp.v1.setZero().add(Core.input.axis(Binding.moveX), Core.input.axis(Binding.moveY)).nor().scl(camSpeed));
            }else if((!player.dead() || spectating != null) && !panning){
                //TODO do not pan
                Team corePanTeam = state.won ? state.rules.waveTeam : player.team();
                Position coreTarget = state.gameOver && !state.rules.pvp && corePanTeam.data().lastCore != null ? corePanTeam.data().lastCore : null;
                Position panTarget = coreTarget != null && followGameEndPan ? coreTarget : spectating != null ? spectating : player;

                Core.camera.position.lerpDelta(panTarget, Core.settings.getBool("smoothcamera") ? 0.08f : 1f);
            }

            if(panCam){
                Core.camera.position.x += Mathf.clamp((Core.input.mouseX() - Core.graphics.getWidth() / 2f) * panScale, -1, 1) * camSpeed;
                Core.camera.position.y += Mathf.clamp((Core.input.mouseY() - Core.graphics.getHeight() / 2f) * panScale, -1, 1) * camSpeed;
            }
        }

        shouldShoot = !locked;
        Tile cursor = tileAt(Core.input.mouseX(), Core.input.mouseY());

        if(!locked && block == null && !scene.hasField() && !scene.hasDialog() &&
                //disable command mode when player unit can boost and command mode binding is the same
                !(!player.dead() && player.unit().type.canBoost && Binding.commandMode.value.key == Binding.boost.value.key)){
            if(settings.getBool("commandmodehold")){
                commandMode = input.keyDown(Binding.commandMode);
            }else if(input.keyTap(Binding.commandMode)){
                commandMode = !commandMode;
            }
        }else{
            commandMode = false;
        }

        //validate commanding units
        selectedUnits.removeAll(u -> !u.allowCommand() || !u.isValid() || u.team != player.team());

        if(commandMode && !scene.hasField() && !scene.hasDialog()){
            if(input.keyTap(Binding.selectAllUnits)){
                selectedUnits.clear();
                commandBuildings.clear();
                if(input.keyDown(Binding.selectAcrossScreen)){
                    camera.bounds(Tmp.r1);
                    selectedUnits.set(selectedCommandUnits(Tmp.r1.x, Tmp.r1.y, Tmp.r1.width, Tmp.r1.height).retainAll(u -> input.ctrl() || u.type.controlSelectGlobal));
                }else{
                    for(var unit : player.team().data().units){
                        if(unit.isCommandable() && (unit.type.controlSelectGlobal || input.ctrl())){
                            selectedUnits.add(unit);
                        }
                    }
                }
            }

            if(input.keyTap(Binding.selectAllUnitTransport)){
                selectedUnits.clear();
                commandBuildings.clear();
                if(input.keyDown(Binding.selectAcrossScreen)){
                    camera.bounds(Tmp.r1);
                    selectedUnits.set(selectedCommandUnits(Tmp.r1.x, Tmp.r1.y, Tmp.r1.width, Tmp.r1.height, u -> u instanceof Payloadc));
                }else{
                    for(var unit : player.team().data().units){
                        if(unit.isCommandable() && unit instanceof Payloadc){
                            selectedUnits.add(unit);
                        }
                    }
                }
            }

            if(input.keyTap(Binding.selectAllUnitFactories)){
                selectedUnits.clear();
                commandBuildings.clear();
                for(var build : player.team().data().buildings){
                    if(build.isCommandable()){
                        commandBuildings.add(build);
                    }
                }
                if(input.keyDown(Binding.selectAcrossScreen)){
                    camera.bounds(Tmp.r1);
                    commandBuildings.retainAll(b -> Tmp.r1.overlaps(b.x - (b.hitSize() /2), b.y - (b.hitSize() /2), b.hitSize(), b.hitSize()));
                }
            }

            for(int i = 0; i < controlGroupBindings.length; i++){
                if(input.keyTap(controlGroupBindings[i])){

                    //create control group if it doesn't exist yet
                    if(controlGroups[i] == null) controlGroups[i] = new IntSeq();

                    IntSeq group = controlGroups[i];
                    boolean creating = input.keyDown(Binding.createControlGroup);

                    //clear existing if making a new control group
                    //if any of the control group edit buttons are pressed take the current selection
                    if(creating){
                        group.clear();

                        IntSeq selectedUnitIds = selectedUnits.mapInt(u -> u.id);
                        if(Core.settings.getBool("distinctcontrolgroups", true)){
                            for(IntSeq cg : controlGroups){
                                if(cg != null){
                                    cg.removeAll(selectedUnitIds);
                                }
                            }
                        }
                        group.addAll(selectedUnitIds);
                    }

                    //remove invalid units
                    for(int j = 0; j < group.size; j++){
                        Unit u = Groups.unit.getByID(group.get(j));
                        if(u == null || !u.isCommandable() || !u.isValid()){
                            group.removeIndex(j);
                            j --;
                        }
                    }

                    //replace the selected units with the current control group
                    if(!group.isEmpty() && !creating){
                        selectedUnits.clear();
                        commandBuildings.clear();

                        group.each(id -> {
                            var unit = Groups.unit.getByID(id);
                            if(unit != null){
                                selectedUnits.addAll(unit);
                            }
                        });

                        //double tap to center camera
                        if(lastCtrlGroup == i && Time.timeSinceMillis(lastCtrlGroupSelectMillis) < 400){
                            float totalX = 0, totalY = 0;
                            for(Unit unit : selectedUnits){
                                totalX += unit.x;
                                totalY += unit.y;
                            }
                            panning = true;
                            Core.camera.position.set(totalX / selectedUnits.size, totalY / selectedUnits.size);
                        }
                        lastCtrlGroup = i;
                        lastCtrlGroupSelectMillis = Time.millis();
                    }
                }
            }
        }

        if(!scene.hasMouse() && !locked){
            // FINISHME: Move this into its own method, its huge
            Unit sl;
            if(Core.input.keyDown(Binding.tileActionsMenuModifier) && Core.input.keyTap(Binding.select) && selectPlans.isEmpty() && !selectedBlock() && cursor != null && ((sl = selectedUnit(true)) == null || sl instanceof BlockUnitUnit)){ // Tile actions / alt click menu
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
                }).disabled(b -> state.rules.pvp && Server.io.b());

                table.row().fill();
                table.button("@client.unitpicker.title", () -> { // Unit Picker / Sniper
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

            if((input.keyDown(Binding.control) || input.shift()) && input.keyTap(Binding.select)){
                Unit on = selectedUnit(true);
                var build = selectedControlBuild();
                boolean hidingAirUnits = ClientVars.hidingAirUnits;
                if(on != null){
                    // FINISHME: This belongs in its own method, its also very messy
                    if (input.keyDown(Binding.control) && on.isAI() && state.rules.possessionAllowed) { // Ctrl + click: control unit
                        Call.unitControl(player, on);
                        shouldShoot = false;
                        recentRespawnTimer = 1f;
                        Navigation.stopFollowing();
                    } else if ((input.keyDown(Binding.control) || input.shift()) && on.isPlayer()) {
                        Navigation.follow(new AssistPath(on.getPlayer(),
                                input.shift() && input.alt() ? AssistPath.Type.FreeMove :
                                input.keyDown(Binding.control) && input.alt() ? AssistPath.Type.BuildPath :
                                input.keyDown(Binding.control) ? AssistPath.Type.Cursor : AssistPath.Type.Regular,
                                Core.settings.getBool("circleassist")));
                        shouldShoot = false;
                    }
                }else if(build != null && input.keyDown(Binding.control)){
                    Call.buildingControlSelect(player, build);
                    recentRespawnTimer = 1f;
                }
            }
            if(input.keyTap(Binding.select)){
                if(Core.input.shift()){
                    dragX = Core.input.mouseWorld().x;
                    dragY = Core.input.mouseWorld().y;
                }else{
                    dragX = Float.NaN;
                }
            }
            if(!hidingUnits && input.shift() && input.keyRelease(Binding.select) && !isPlacing()
                && !Float.isNaN(dragX) && Core.input.mouseWorld().dst2(dragX, dragY) < tilesize * tilesize){
                Vec2 mouseWorld = Core.input.mouseWorld();
                Unit on = selectedUnit(true);
                if(on != null && on.controller() instanceof LogicAI ai && ai.controller != null) {
                    Spectate.INSTANCE.spectate(ai.controller);
                    shouldShoot = false;
                } else if((on = Units.closestOverlap(mouseWorld.x, mouseWorld.y, tilesize * 8f,
                    u -> (!u.isFlying() || !hidingAirUnits) && mouseWorld.within(u, u.hitSize))) != null &&
                    on.controller() instanceof LogicAI ai && ai.controller != null){
                    // This condition is meant to catch logic-controlled units of any team
                    Spectate.INSTANCE.spectate(ai.controller);
                    shouldShoot = false;
                }
            }
        }

        if(!player.dead() && !state.isPaused() && !scene.hasField() && !locked){
            updateMovement(player.unit());

            if(Core.input.keyTap(Binding.respawn) && !scene.hasDialog()){
                controlledType = null;
                recentRespawnTimer = 1f;
                var u = player.unit();
                var closest = player.bestCore();
                if(CoreBlock.preferredCoreType == null ||
                    (closest != null && closest.block == CoreBlock.preferredCoreType) ||
                    (!u.spawnedByCore &&
                    ((u.dockedType != null && u.dockedType.coreUnitDock) ||
                    (closest != null && ((CoreBlock)closest.block).unitType != null &&
                        ((CoreBlock)closest.block).unitType.coreUnitDock))
                    )
                ){
                    // Use original spawning mechanism for docking units
                    Call.unitClear(player);
                } else {
                    // Send a packet that supports respawning at a specific block
                    Call.buildingControlSelect(player, closest);
                }
            }
        }

        if(state.isGame() && !scene.hasDialog() && !scene.hasField()){
            if(Core.input.keyTap(Binding.minimap)) ui.minimapfrag.toggle();
            if(Core.input.keyTap(Binding.planetMap) && state.isCampaign()) ui.planet.toggle();
            if(Core.input.keyTap(Binding.research) && state.isCampaign()) ui.research.toggle();
            if(Core.input.keyTap(Binding.schematicMenu)){
                if(Core.input.shift()) ui.toggleSchematicBrowser();
                else ui.toggleSchematicMenu();
            }

            if(Core.input.keyTap(Binding.toggleBlockStatus)){
                Core.settings.put("blockstatus", !Core.settings.getBool("blockstatus"));
            }

            if(Core.input.keyTap(Binding.togglePowerLines)){
                if(Core.settings.getInt("lasersopacity") == 0){
                    Core.settings.put("lasersopacity", Core.settings.getInt("preferredlaseropacity", 100));
                }else{
                    Core.settings.put("preferredlaseropacity", Core.settings.getInt("lasersopacity"));
                    Core.settings.put("lasersopacity", 0);
                }
            }
        }

        if(state.isMenu() || Core.scene.hasDialog()) return;

        if(input.keyTap(Binding.resetCamera) && scene.getKeyboardFocus() == null && (cursor == null || cursor.build == null || !(cursor.build.block.rotate && cursor.build.block.quickRotate && cursor.build.interactable(player.team()))) && !input.alt()){
            panning = false;
            Spectate.INSTANCE.setPos(null); // FINISHME: Vanilla has a spectate feature now
            if(ui.listfrag.shown()) ui.listfrag.rebuild();
        }

        //zoom camera
        if((!Core.scene.hasScroll() || Core.input.keyDown(Binding.diagonalPlacement)) && !ui.chatfrag.shown() && !ui.consolefrag.shown() && Math.abs(Core.input.axisTap(Binding.zoom)) > 0
            && !Core.input.keyDown(Binding.rotatePlaced) && (Core.input.keyDown(Binding.diagonalPlacement) ||
                !Binding.zoom.value.equals(Binding.rotate.value) || ((!player.isBuilder() || !isPlacing() || !block.rotate) && selectPlans.isEmpty()))){
            renderer.scaleCamera(Core.input.axisTap(Binding.zoom));
        }

        if(Core.input.keyTap(Binding.select) && !Core.scene.hasMouse()){
            Tile selected = world.tileWorld(input.mouseWorldX(), input.mouseWorldY());
            if(selected != null){
                Call.tileTap(player, selected);
            }
        }

        if(input.keyTap(Binding.ping) && !Core.scene.hasMouse() && !scene.hasKeyboard()){
            if(input.ctrl()){
                ui.showTextInput("", "@ping.text", Vars.maxPingTextLength, "", result -> Call.pingLocation(Vars.player, input.mouseWorldX(), input.mouseWorldY(), UI.formatIcons(result)));
            }else if(input.shift()){
                //Shift+ping to clear ping
                Call.pingLocation(Vars.player, Float.NaN, Float.NaN, null);
            }else{
                Call.pingLocation(Vars.player, input.mouseWorldX(), input.mouseWorldY(), null);
            }
        }

        if(Core.input.keyRelease(Binding.select) && commandRect){
            selectUnitsRect();
        }

        if(player.dead() || locked){ // FINISHME: Should we comment out the dead check?
            cursorType = ui.chatfrag.hasLit ? SystemCursor.hand : SystemCursor.arrow;
            if(!locked){
                pollInputNoPlayer();
            }
        }else{
            pollInputPlayer();
        }

        if(Core.input.keyRelease(Binding.select)){
            player.shooting = false;
        }

        if(!Core.scene.hasMouse() && !ui.minimapfrag.shown()){
            Core.graphics.cursor(cursorType);
            changedCursor = cursorType != SystemCursor.arrow;
        }else{
            cursorType = SystemCursor.arrow;
            if(changedCursor){
                graphics.cursor(SystemCursor.arrow);
                changedCursor = false;
            }
        }
    }

    @Override
    public void useSchematic(Schematic schem, boolean checkHidden){
        block = null;
        schematicX = tileX(getMouseX());
        schematicY = tileY(getMouseY());

        selectPlans.clear();
        selectPlans.addAll(schematics.toPlans(schem, schematicX, schematicY, checkHidden));
        mode = none;
    }

    @Override
    public boolean isBreaking(){
        return mode == breaking;
    }

    @Override
    public void buildPlacementUI(Table table){
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

    void pollInputNoPlayer(){
        if(Core.input.keyTap(Binding.select) && !Core.scene.hasMouse()){
            tappedOne = false;

            Tile selected = tileAt(Core.input.mouseX(), Core.input.mouseY());

            if(commandMode){
                commandRect = true;
                commandRectX = input.mouseWorldX();
                commandRectY = input.mouseWorldY();
            }else if(selected != null){
                tileTapped(selected.build);
            }
        }
    }

    //player input: for controlling the player unit (will crash if the unit is not present)
    void pollInputPlayer(){
        if(scene.hasField()) return;

        Tile selected = tileAt(Core.input.mouseX(), Core.input.mouseY());
        int cursorX = tileX(Core.input.mouseX());
        int cursorY = tileY(Core.input.mouseY());
        int rawCursorX = World.toTile(Core.input.mouseWorld().x), rawCursorY = World.toTile(Core.input.mouseWorld().y);

        //automatically pause building if the current build queue is empty
        if(Core.settings.getBool("buildautopause") && isBuilding && !isBuildingIgnoreNetworking()){
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

        if(Core.input.keyTap(Binding.deselect) && !ui.minimapfrag.shown() && !isPlacing() && player.unit().plans.isEmpty() && !commandMode){
            player.unit().mineTile = null;
        }

        if(Core.input.keyTap(Binding.clearBuilding)){
            if(Core.input.shift()){
                frozenPlans.clear();
            }else{
                if(!player.dead() && player.unit().plans.isEmpty()){
                    lastSchematic = null;
                    selectPlans.clear();
                }
                Player.persistPlans.clear(); // FINISHME: Persistplans is vanilla now
                if(player.unit() != null) player.unit().clearBuilding(); // As of v8, the player unit is null when dead
            }
        }

        if((Core.input.keyTap(Binding.schematicSelect) || Core.input.keyTap(Binding.rebuildSelect)) && !Core.scene.hasKeyboard() && mode != breaking){
            schemX = rawCursorX;
            schemY = rawCursorY;
        }

        if(/*Core.input.keyTap(Binding.clearBuilding) || */isPlacing()){
            lastSchematic = null;
            selectPlans.clear();
        }

        if(!Core.scene.hasKeyboard() && selectX == -1 && selectY == -1 && schemX != -1 && schemY != -1){
            if(Core.input.keyRelease(Binding.schematicSelect)){
                lastSchematic = schematics.create(schemX, schemY, rawCursorX, rawCursorY);
                useSchematic(lastSchematic);
                if(selectPlans.isEmpty()){
                    lastSchematic = null;
                }
                schemX = -1;
                schemY = -1;
            }else if(input.keyRelease(Binding.rebuildSelect)){

                rebuildArea(schemX, schemY, rawCursorX, rawCursorY);
                schemX = -1;
                schemY = -1;
            }
        }

        if(!selectPlans.isEmpty()){
            if(Core.input.keyTap(Binding.schematicFlipX) && !input.shift()){ // Don't rotate when shift is held, if shift is held navigate instead.
                flipPlans(selectPlans, true);
            }

            if(Core.input.keyTap(Binding.schematicFlipY)){
                flipPlans(selectPlans, false);
            }
        }

        if(splan != null){
            int x = Math.round((Core.input.mouseWorld().x + buildPlanMouseOffsetX) / tilesize);
            int y = Math.round((Core.input.mouseWorld().y + buildPlanMouseOffsetY) / tilesize);
            if(splan.x != x || splan.y != y){
                splan.x = x;
                splan.y = y;
                movedPlan = true;
            }
        }

        if(block == null || mode != placing){
            linePlans.clear();
        }

        if(Core.input.keyTap(Binding.pauseBuilding)){
            if (Core.input.shift()) isFreezeQueueing = !isFreezeQueueing;
            else if (Core.input.ctrl()) {
                temp.set(frozenPlans);
                flushPlans(temp, false, false, true);
                temp.clear();
            } else {
                isBuilding = !isBuilding;
                buildWasAutoPaused = false;

                if(isBuilding){
                    player.shooting = false;
                }
            }
        }

        if(isPlacing() && mode == placing && (cursorX != lastLineX || cursorY != lastLineY || Core.input.keyTap(Binding.diagonalPlacement) || Core.input.keyRelease(Binding.diagonalPlacement))){
            updateLine(selectX, selectY);
            lastLineX = cursorX;
            lastLineY = cursorY;
        }

        if(Core.input.keyRelease(Binding.select) && !Core.scene.hasMouse()){
            BuildPlan plan = getPlan(cursorX, cursorY);

            if(plan != null && !movedPlan){
                //move selected to front
                int index = player.unit().plans.indexOf(plan, true);
                if(index != -1){
                    player.unit().plans.removeIndex(index);
                    player.unit().plans.addFirst(plan);
                }
            }
        }

        if(Core.input.keyTap(Binding.select) && !Core.scene.hasMouse()){
            tappedOne = false;
            BuildPlan plan = getPlan(cursorX, cursorY);

            if(Core.input.keyDown(Binding.breakBlock)){
                mode = none;
            }else if(selectPlans.any()){
                flushPlans(
                    temp.selectFrom(selectPlans, s -> s.block.isVisible() || s.block instanceof CoreBlock),
                    isFreezeQueueing, Core.input.keyDown(Binding.forcePlaceModifier), isFreezeQueueing);
                temp.clear();
                movedPlan = true;
            }else if(isPlacing()){
                selectX = cursorX;
                selectY = cursorY;
                lastLineX = cursorX;
                lastLineY = cursorY;
                mode = placing;
                updateLine(selectX, selectY);
            }else if(plan != null && !plan.breaking && mode == none && !plan.initialized && plan.progress <= 0f){
                splan = plan;
                movedPlan = false;
                buildPlanMouseOffsetX = splan.x * tilesize - Core.input.mouseWorld().x;
                buildPlanMouseOffsetY = splan.y * tilesize - Core.input.mouseWorld().y;
            }else if(plan != null && plan.breaking){
                deleting = true;
            }else if(commandMode){
                commandRect = true;
                commandRectX = input.mouseWorldX();
                commandRectY = input.mouseWorldY();
            }else if(!checkConfigTap() && selected != null && !tryRepairDerelict(selected)){
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
        }else if(Core.input.keyTap(Binding.breakBlock) && !Core.scene.hasMouse() && !commandMode){
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
            if(!overrideLineRotation && !Core.input.keyDown(Binding.diagonalPlacement) && (selectX != cursorX || selectY != cursorY) && ((int)Core.input.axisTap(Binding.rotate) != 0)){
                rotation = ((int)((Angles.angle(selectX, selectY, cursorX, cursorY) + 45) / 90f)) % 4;
                overrideLineRotation = true;
            }
        }else{
            overrideLineRotation = false;
        }

        if(mode == breaking || mode == freezing || mode == dequeue){
            mode = Core.input.shift() ?  freezing : Core.input.ctrl() ? dequeue : breaking;
        }

        if(Core.input.keyRelease(Binding.breakBlock) && Core.input.keyDown(Binding.schematicSelect) && mode == breaking){
            lastSchematic = schematics.create(schemX, schemY, rawCursorX, rawCursorY);
            schemX = -1;
            schemY = -1;
        }

        if(Core.input.keyRelease(Binding.breakBlock) || Core.input.keyRelease(Binding.select)){

            if(mode == placing && block != null){ //touch up while placing, place everything in selection
                // Why do we even need reversed build plans - SBytes 17/08/2022
//                if(input.keyDown(Binding.boost)){
//                    flushPlansReverse(linePlans);
//                }else{
                    flushPlans(linePlans, isFreezeQueueing, input.alt(), isFreezeQueueing);
//                }

                linePlans.clear();
                Events.fire(new LineConfirmEvent());
            }else if(mode == breaking){ //touch up while breaking, break everything in selection
                removeSelection(selectX, selectY, cursorX, cursorY, false, /*!Core.input.keyDown(Binding.schematicSelect) ? maxLength :*/ Vars.maxSchematicSize, isFreezeQueueing);
                if(lastSchematic != null){
                    useSchematic(lastSchematic);
                    lastSchematic = null;
                }
            }else if(mode == freezing){
                freezeSelection(selectX, selectY, cursorX, cursorY, Vars.maxSchematicSize);
            } else if (mode == dequeue) {
                removeSelectionPlans(selectX, selectY, cursorX, cursorY, Vars.maxSchematicSize); // FINISHME: Why not just use removeSelection and ignore the rest? Or make remove selection call this
            }
            selectX = -1;
            selectY = -1;

            tryDropItems(selected == null ? null : selected.build, Core.input.mouseWorld().x, Core.input.mouseWorld().y);

            if(splan != null){
                if(getPlan(splan.x, splan.y, splan.block.size, splan) != null){
                    player.unit().plans().remove(splan, true);
                }

                if(input.ctrl()){
                    inv.hide();
                    config.hideConfig();
                    planConfig.showConfig(splan);
                }else{
                    planConfig.hide();
                }

                splan = null;
            }

            mode = none;
        }


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

        if(!Core.input.keyDown(Binding.diagonalPlacement) && Math.abs((int)Core.input.axisTap(Binding.rotate)) > 0){
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

        Tile cursor = tileAt(Core.input.mouseX(), Core.input.mouseY());

        cursorType = SystemCursor.arrow;

        if(ui.chatfrag.hasLit){ // Sciffed fpp's addition to ensure clickable chat takes priority
            cursorType = SystemCursor.hand;
        }else if(cursor != null){
            if(cursor.build != null && cursor.build.interactable(player.team())){
                cursorType = cursor.build.getCursor();
            }

            if(canRepairDerelict(cursor) && !player.dead() && player.unit().canBuild()){
                cursorType = ui.repairCursor;
            }

            if((isPlacing() /*&& player.isBuilder()*/) || !selectPlans.isEmpty()){
                cursorType = SystemCursor.hand;
            }

            if(!isPlacing() && canMine(cursor)){
                cursorType = ui.drillCursor;
            }

            if(commandMode && selectedUnits.any()){
                boolean canAttack = (cursor.build != null && !cursor.build.inFogTo(player.team()) && cursor.build.team != player.team());

                if(!canAttack){
                    var unit = selectedEnemyUnit(input.mouseWorldX(), input.mouseWorldY());
                    if(unit != null){
                        canAttack = selectedUnits.contains(u -> u.canTarget(unit));
                    }
                }

                if(canAttack){
                    cursorType = ui.targetCursor;
                }

                if(input.keyTap(Binding.commandQueue) && Binding.commandQueue.value.key.type != KeyType.mouse){
                    commandTap(input.mouseX(), input.mouseY(), true);
                }
            }

            if(getPlan(cursor.x, cursor.y) != null && mode == none){
                cursorType = SystemCursor.hand;
            }

            if(canTapPlayer(Core.input.mouseWorld().x, Core.input.mouseWorld().y)){
                cursorType = ui.unloadCursor;
            }

            if(!ui.chatfrag.shown() && cursor.build != null && cursor.interactable(player.team()) && !isPlacing() && Math.abs(Core.input.axisTap(Binding.rotate)) > 0 && Core.input.keyDown(Binding.rotatePlaced) && cursor.block().rotate && cursor.block().quickRotate){
                Call.rotateBlock(player, cursor.build, Core.input.axisTap(Binding.rotate) > 0);
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

        if(button == Binding.commandQueue.value.key){
            commandTap(x, y, true);
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

    @Override
    public void panCamera(Vec2 position){
        if(!locked()){
            panning = true;
            camera.position.set(position);
        }
    }

    protected void updateMovement(Unit unit){ // Heavily modified to support navigation
        boolean omni = unit.type.omniMovement;

        float speed = unit.speed();
        float xa = Core.input.axis(Binding.moveX);
        float ya = Core.input.axis(Binding.moveY);
        if(input.keyDown(Binding.freecamModifier)){
            xa = ya = 0f;
        }
        boolean boosted = (unit instanceof Mechc && unit.isFlying());

        if(settings.getBool("detach-camera")){
            Vec2 targetPos = camera.position;

            movement.set(targetPos).sub(player).limit(speed);

            if(player.within(targetPos, 15f)){
                movement.setZero();
                unit.vel.approachDelta(Vec2.ZERO, unit.speed() * unit.type().accel / 2f);
            }
        }else{
            movement.set(xa, ya).nor().scl(speed);
            if(Core.input.keyDown(Binding.mouseMove)){
                movement.add(input.mouseWorld().sub(player).scl(1f / 25f * speed)).limit(speed);
            }
        }
        if(!moved && !movement.isZero()) moved = true;

        if(!Navigation.isFollowing()){
            float mouseAngle = Angles.mouseAngle(unit.x, unit.y);
            boolean aimCursor = omni && player.shooting && unit.type.hasWeapons() && unit.type.faceTarget && !boosted;

            if(aimCursor) unit.lookAt(mouseAngle);
            else unit.lookAt(unit.prefRotation());

            if (Core.settings.getBool("zerodrift") && movement.epsilonEquals(0, 0)) unit.vel().setZero();
            else if (Core.settings.getBool("decreasedrift") && unit.vel().len() > 3.5 && movement.epsilonEquals(0, 0)) unit.vel().scl(0.95f);
            else unit.movePref(movement);

            unit.aim(Core.input.mouseWorld());

            if(settings.getBool("unitboosthold", true)){
                // If auto-boost, invert the behavior of the boost key
                player.boosting = unit.type.canBoost && Core.settings.getBool("autoboost") ^ input.keyDown(Binding.boost);
            }else if(input.keyTap(Binding.boost)){
                player.boosting = unit.type.canBoost && !player.boosting;
            }

            if ((!Core.input.keyDown(Binding.select) || block != null) && shouldShoot) AutoShootKt.autoShoot();
        }
        unit.controlWeapons(true, player.shooting && !boosted);

        player.mouseX = unit.aimX();
        player.mouseY = unit.aimY();

        //update payload input
        if(unit instanceof Payloadc){
            if(Core.input.keyTap(Binding.pickupCargo)){
                tryPickupPayload();
                lastPayloadKeyTapMillis = Time.millis();
            }

            if(Core.input.keyDown(Binding.pickupCargo)
            && Time.timeSinceMillis(lastPayloadKeyHoldMillis) > 20
            && Time.timeSinceMillis(lastPayloadKeyTapMillis) > 200){
                tryPickupPayload();
                lastPayloadKeyHoldMillis = Time.millis();
            }

            if(Core.input.keyTap(Binding.dropCargo)){
                tryDropPayload();
                lastPayloadKeyTapMillis = Time.millis();
            }

            if(Core.input.keyDown(Binding.dropCargo)
            && Time.timeSinceMillis(lastPayloadKeyHoldMillis) > 20
            && Time.timeSinceMillis(lastPayloadKeyTapMillis) > 200){
                tryDropPayload();
                lastPayloadKeyHoldMillis = Time.millis();
            }
        }
    }

    private boolean isBuildingIgnoreNetworking() {
        return !player.dead() && player.unit().plans.size != 0 && !BuildPlanCommunicationSystem.INSTANCE.isNetworking(player.unit().plans.last());
    }
}
