package mindustry.ui.fragments;

import arc.*;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.scene.*;
import arc.scene.actions.*;
import arc.scene.event.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.ImageButton.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import kotlin.collections.*;
import mindustry.*;
import mindustry.annotations.Annotations.*;
import mindustry.client.*;
import mindustry.client.antigrief.*;
import mindustry.client.navigation.*;
import mindustry.client.ui.*;
import mindustry.client.utils.*;
import mindustry.content.*;
import mindustry.core.GameState.State;
import mindustry.core.*;
import mindustry.ctype.*;
import mindustry.entities.abilities.*;
import mindustry.game.EventType.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.input.*;
import mindustry.net.Packets.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.blocks.environment.*;
import mindustry.world.blocks.storage.*;
import mindustry.world.blocks.storage.CoreBlock.*;
import mindustry.world.meta.*;

import java.lang.reflect.*;

import static mindustry.Vars.*;
import static mindustry.client.ClientVars.*;
import static mindustry.gen.Tex.*;

public class HudFragment{
    private static final float dsize = 78f, pauseHeight = 36f;

    public PlacementFragment blockfrag = new PlacementFragment();
    public CoreItemsDisplay coreItems = new CoreItemsDisplay();
    public boolean shown = true;

    private ImageButton flip;

    private String hudText = "";
    private boolean showHudText;

    private Table lastUnlockTable;
    private Table lastUnlockLayout;
    private long lastToast;

    // Foo's additions
    private long lastWarn, lastWarnClick;

    private Seq<Block> blocksOut = new Seq<>();

    private void addBlockSelection(Table cont){
        Table blockSelection = new Table();
        var pane = new ScrollPane(blockSelection, Styles.smallPane);
        pane.setFadeScrollBars(false);
        Planet[] last = {state.rules.planet};
        pane.update(() -> {
            if(pane.hasScroll()){
                Element result = Core.scene.getHoverElement();
                if(result == null || !result.isDescendantOf(pane)){
                    Core.scene.setScrollFocus(null);
                }
            }

            if(state.rules.planet != last[0]){
                last[0] = state.rules.planet;
                rebuildBlockSelection(blockSelection, "");
            }
        });

        cont.table(search -> {
            search.image(Icon.zoom).padRight(8);
            search.field("", text -> rebuildBlockSelection(blockSelection, text)).growX()
            .name("editor/search").maxTextLength(maxNameLength).get().setMessageText("@players.search");
        }).growX().pad(-2).padLeft(6f);
        cont.row();
        cont.add(pane).expandY().top().left();

        rebuildBlockSelection(blockSelection, "");
    }

    private void rebuildBlockSelection(Table blockSelection, String searchText){
        blockSelection.clear();

        blocksOut.clear();
        blocksOut.addAll(Vars.content.blocks());
        blocksOut.sort((b1, b2) -> {
            int synth = Boolean.compare(b1.synthetic(), b2.synthetic());
            if(synth != 0) return synth;
            int ore = Boolean.compare(b1 instanceof OverlayFloor && b1 != Blocks.removeOre, b2 instanceof OverlayFloor && b2 != Blocks.removeOre);
            if(ore != 0) return ore;
            return Integer.compare(b1.id, b2.id);
        });

        int i = 0;

        for(Block block : blocksOut){
            TextureRegion region = block.uiIcon;

            if(!Core.atlas.isFound(region)
            || (!block.inEditor && !(block instanceof RemoveWall) && !(block instanceof RemoveOre))
            || !block.isOnPlanet(state.rules.planet)
            || block.buildVisibility == BuildVisibility.debugOnly
            || (!searchText.isEmpty() && !block.localizedName.toLowerCase().contains(searchText.toLowerCase()))
            ) continue;

            ImageButton button = new ImageButton(Tex.whiteui, Styles.clearNoneTogglei);
            button.getStyle().imageUp = new TextureRegionDrawable(region);
            button.clicked(() -> control.input.block = block);
            button.resizeImage(8 * 4f);
            button.update(() -> button.setChecked(control.input.block == block));
            blockSelection.add(button).size(48f).tooltip(block.localizedName);

            if(++i % 6 == 0){
                blockSelection.row();
            }
        }

        if(i == 0){
            blockSelection.add("@none.found").padLeft(54f).padTop(10f);
        }
    }

    public void build(Group parent){

        //warn about guardian/boss waves
        Events.on(WaveEvent.class, e -> {
            int max = 10;
            int winWave = state.rules.winWave > 0 ? state.rules.winWave : Integer.MAX_VALUE;
            outer:
            for(int i = state.wave - 1; i <= Math.min(state.wave + max, winWave - 2); i++){
                for(SpawnGroup group : state.rules.spawns){
                    if(group.effect == StatusEffects.boss && group.getSpawned(i) > 0){
                        int diff = (i + 2) - state.wave;

                        //increments at which to warn about incoming guardian
                        if(diff == 1 || diff == 2 || diff == 5 || diff == 10){
                            showToast(Icon.warning, group.type.emoji() + " " + Core.bundle.format("wave.guardianwarn" + (diff == 1 ? ".one" : ""), diff));
                        }

                        break outer;
                    }
                }
            }
        });

        Events.on(SectorCaptureEvent.class, e -> {
            if(e.sector.isBeingPlayed()){
                ui.announce("@sector.capture.current", 5f);
            }else{
                showToast(Core.bundle.format("sector.capture", e.sector.name()));
            }
        });

        Events.on(SectorLoseEvent.class, e -> {
            showToast(Icon.warning, Core.bundle.format("sector.lost", e.sector.name()));
        });

        Events.on(SectorInvasionEvent.class, e -> {
            showToast(Icon.warning, Core.bundle.format("sector.attacked", e.sector.name()));
        });

        Events.on(ResetEvent.class, e -> {
            coreItems.resetUsed();
            coreItems.clear();
        });

        //paused table
        parent.fill(t -> {
            t.name = "paused";
            t.top().visible(() -> state.isPaused() && shown && !netServer.isWaitingForPlayers()).touchable = Touchable.disabled;
            t.table(Styles.black6, top -> top.label(() -> state.gameOver && state.isCampaign() ? "@sector.curlost" : "@paused")
                .style(Styles.outlineLabel).pad(8f)).height(pauseHeight).growX();
            //.padLeft(dsize * 5 + 4f) to prevent alpha overlap on left
        });

        //"waiting for players"
        parent.fill(t -> {
            t.name = "waiting";
            t.visible(() -> netServer.isWaitingForPlayers() && state.isPaused() && shown).touchable = Touchable.disabled;
            t.table(Styles.black6, top -> top.add("@waiting.players").style(Styles.outlineLabel).pad(18f));
        });

        //minimap + position
        parent.fill(t -> {
            t.visible(() -> shown && Core.settings.getBool(("minimap"))); // FINISHME: Only hide minimap when doing so, use a collapser to shrink it maybe? Idk
            t.name = "minimap/position";
            //tile hud
            t.add(new TileInfoFragment()).name("tilehud").top();
            //minimap
            t.add(new Minimap()).name("minimap").top();
            t.row();
            //position
            t.label(() -> player.tileX() + ", " + player.tileY() + "\n" + "[coral]" + World.toTile(Core.input.mouseWorldX()) + ", " + World.toTile(Core.input.mouseWorldY()))
                .tooltip("Player Position\n[coral]Cursor Position")
                .visible(() -> Core.settings.getBool("position"))
                .style(Styles.outlineLabel)
                .name("position").top().right().labelAlign(Align.right)
                .colspan(2);
            t.top().right();
        });

        ui.hints.build(parent);

        //menu at top left
        parent.fill(cont -> {
            cont.name = "overlaymarker";
            cont.top().left();

            if(mobile){
                //for better inset visuals
                cont.rect((x, y, w, h) -> {
                    if(Core.scene.marginTop > 0){
                        Tex.paneRight.draw(x, y, w, Core.scene.marginTop);
                    }
                }).fillX().row();

                cont.table(select -> {
                    select.name = "mobile buttons";
                    select.left();
                    select.defaults().size(dsize).left();

                    ImageButtonStyle style = Styles.cleari;

                    select.button(Icon.menu, style, ui.paused::show).name("menu");
                    flip = select.button(Icon.upOpen, style, this::toggleMenus).get();
                    flip.name = "flip";

                    select.button(Icon.paste, style, ui.schematics::show)
                    .name("schematics");

                    select.button(Icon.pause, style, () -> {
                        if(net.active()){
                            ui.listfrag.toggle();
                        }else{
                            state.set(state.isPaused() ? State.playing : State.paused);
                        }
                    }).name("pause").update(i -> {
                        if(net.active()){
                            i.getStyle().imageUp = Icon.players;
                        }else{
                            i.setDisabled(false);
                            i.getStyle().imageUp = state.isPaused() ? Icon.play : Icon.pause;
                        }
                    });

                    select.button(Icon.chat, style,() -> {
                        if(net.active() && mobile){
                            if(ui.chatfrag.shown()){
                                ui.chatfrag.hide();
                            }else{
                                ui.chatfrag.toggle();
                            }
                        }else if(state.isCampaign()){
                            ui.research.show();
                        }else{
                            ui.database.show();
                        }
                    }).name("chat").update(i -> {
                        if(net.active() && mobile){
                            i.getStyle().imageUp = Icon.chat;
                        }else if(state.isCampaign()){
                            i.getStyle().imageUp = Icon.tree;
                        }else{
                            i.getStyle().imageUp = Icon.book;
                        }
                    });

                    select.image().color(Pal.gray).width(4f).fillY();
                });

                cont.row();
                cont.image().height(4f).color(Pal.gray).fillX();
                cont.row();
            }

            cont.update(() -> {
                if(Core.input.keyTap(Binding.toggle_menus) && !ui.chatfrag.shown() && !Core.scene.hasDialog() && !Core.scene.hasField()){
                    Core.settings.getBoolOnce("ui-hidden", () -> {
                        ui.announce(Core.bundle.format("showui",  Core.keybinds.get(Binding.toggle_menus).key.toString(), 11));
                    });
                    toggleMenus();
                }
            });

            Table wavesMain, editorMain;

            cont.stack(wavesMain = new Table(), editorMain = new Table()).height(wavesMain.getPrefHeight())
            .name("waves/editor");

            wavesMain.visible(() -> shown && !state.isEditor());
            wavesMain.top().left().name = "waves";

            wavesMain.table(s -> {
                //wave info button with text
                s.add(makeStatusTable()).grow().name("status");

                var rightStyle = new ImageButtonStyle(){{
                    up = wavepane;
                    over = wavepane;
                    disabled = wavepane;
                }};

                // button to skip wave
                s.button(Icon.play, rightStyle, 30f, () -> {
                    if(!canSkipWave()) new Toast(1f).add("You tried and that's all that matters.");
                    else if(net.client() && Server.current.adminui()){
                        Call.adminRequest(player, AdminAction.wave, null);
                    }else{
                        logic.skipWave();
                    }
                }).growY().fillX().right().width(40f).name("skip").get().toBack();
            }).width(dsize * 5 + 4f).name("statustable");

            if(Core.settings.getBool("activemodesdisplay", true)){
                //Active modes display
                wavesMain.row();
                wavesMain.table(Tex.wavepane, st -> {
                    var a = 0.5f;
                    //i dont think there is anything better
                    modeIcon(st, () -> showingTurrets, () -> showingTurrets ^= true, Icon.turret.tint(1, 0.33f, 0.33f, a), "Showing Turrets", Binding.show_turret_ranges);
                    modeIcon(st, () -> showingAllyTurrets, () -> showingAllyTurrets ^= true, Icon.turret.tint(0.67f, 1, 0.67f, a), "Showing Ally Turrets", Binding.show_turret_ranges, "Alt");
                    if(Core.settings.getBool("allowinvturrets"))
                        modeIcon(st, () -> showingInvTurrets, () -> showingInvTurrets ^= true, Icon.turret.tint(1, 0.67f, 0.33f, a), "Inverting Ground/Air", Binding.show_turret_ranges, "Ctrl");
                    modeIcon(st, () -> hidingUnits, () -> hidingUnits ^= true, new SlashTextureRegionDrawable(Icon.units.getRegion(), new Color(1f, 1f, 1f, a)), "Hiding Units", Binding.invisible_units);
                    modeIcon(st, () -> hidingAirUnits, () -> hidingAirUnits ^= true, new SlashTextureRegionDrawable(Icon.planeOutline.getRegion(), new Color(1f, 1f, 1f, a)), "Hiding Air Units", Binding.invisible_units, "Shift");
                    modeIcon(st, () -> hidingBlocks, () -> hidingBlocks ^= true, new SlashTextureRegionDrawable(Icon.layers.getRegion(), new Color(1f, 1f, 1f, a)), "Hiding Blocks", Binding.hide_blocks);
                    modeIcon(st, () -> hidingPlans, () -> hidingPlans ^= true, new SlashTextureRegionDrawable(Icon.effect.getRegion(), new Color(0.5f, 0.5f, 0.5f, a)), "Hiding Plans", Binding.hide_blocks, "Shift");
                    modeIcon(st, () -> showingMassDrivers, () -> showingMassDrivers ^= true, new TextureRegionDrawable(Blocks.massDriver.region), "Showing Massdriver Links", Binding.show_massdriver_configs);
                    modeIcon(st, () -> showingOverdrives, () -> showingOverdrives ^= true, new TextureRegionDrawable(Blocks.overdriveProjector.region), "Showing Overdrive Ranges", Binding.show_turret_ranges);
                    modeIcon(st, () -> Core.settings.getBool("showdomes"), () -> Core.settings.put("showdomes", !Core.settings.getBool("showdomes")), Icon.commandRally, "Showing Dome Ranges", Binding.show_reactor_and_dome_ranges);
                    st.row();
                    modeIcon(st, () -> !Vars.control.input.isBuilding, () -> Vars.control.input.isBuilding ^= true, Icon.pause.tint(1, 0.33f, 0.33f, a), "Paused Building", Binding.pause_building);
                    modeIcon(st, () -> control.input.isFreezeQueueing, () -> control.input.isFreezeQueueing ^= true, Icon.pause.tint(0.33f, 0.33f, 1, a), "Freeze Queuing", Binding.pause_building, "Shift");
                    modeIcon(st, () -> Core.settings.getBool("autotarget"), () -> Core.settings.put("autotarget", !Core.settings.getBool("autotarget")), Icon.modeAttack.tint(1f, 0.33f, 0.33f, a), "Auto Target", Binding.toggle_auto_target);
                    modeIcon(st, () -> AutoTransfer.enabled, () -> AutoTransfer.enabled ^= true, Icon.resize.tint(1, 0.33f, 1, a), "Auto Transfer", Binding.toggle_auto_target, "Shift");
                    modeIcon(st, () -> dispatchingBuildPlans, () -> dispatchingBuildPlans ^= true, Icon.tree.tint(1, 1, 1, a), "Sending Build Plans", Binding.send_build_queue);
                    modeIcon(st, () -> Navigation.currentlyFollowing != null, Navigation::stopFollowing, Icon.android.tint(Color.cyan.cpy().a(a)), "Navigating", Binding.stop_following_path);
                }).marginTop(3).marginBottom(3).growX().get();
            }

            wavesMain.row();

            // Power bar + payload + status effects display
            var powerInfo = Core.settings.getBool("powerinfo", true);
            var powPayStat = wavesMain.table(Tex.wavepane, st -> {
                if (powerInfo) {
                    PowerInfo.getBars(st);
                    st.row();
                }
                addInfoTable(st.table().growX().get());
            }).marginTop(6).marginBottom(3).growX().get();
            powPayStat.visible(() -> powerInfo || !player.dead() && (player.unit() instanceof Payloadc p && p.payloadUsed() > 0 || player.unit().statusBits() != null && !player.unit().statusBits().isEmpty()));

            editorMain.name = "editor";

            editorMain.table(Tex.buttonEdge4, t -> {
                t.name = "teams";
                t.top().table(teams -> {
                    teams.left();
                    int i = 0;
                    for(Team team : Team.baseTeams){
                        ImageButton button = teams.button(Tex.whiteui, Styles.clearNoneTogglei, 38f, () -> Call.setPlayerTeamEditor(player, team))
                        .size(50f).margin(6f).get();
                        button.getImageCell().grow();
                        button.getStyle().imageUpColor = team.color;
                        button.update(() -> button.setChecked(player.team() == team));

                        if(++i % 6 == 0){
                            teams.row();
                        }
                    }
                }).top().left();

                t.row();

                t.table(control.input::buildPlacementUI).growX().left().with(in -> in.left()).row();

                //hovering item display
                t.table(h -> {
                    Runnable rebuild = () -> {
                        h.clear();
                        h.left();

                        Displayable hover = blockfrag.hovered();
                        UnlockableContent toDisplay = control.input.block;

                        if(toDisplay == null && hover != null){
                            if(hover instanceof Building b){
                                toDisplay = b.block;
                            }else if(hover instanceof Tile tile){
                                toDisplay =
                                    tile.block().itemDrop != null ? tile.block() :
                                    tile.overlay().itemDrop != null || tile.wallDrop() != null ? tile.overlay() :
                                    tile.floor();
                            }else if(hover instanceof Unit u){
                                toDisplay = u.type;
                            }
                        }

                        if(toDisplay != null){
                            h.image(toDisplay.uiIcon).scaling(Scaling.fit).size(8 * 4);
                            h.add(toDisplay.localizedName).ellipsis(true).left().growX().padLeft(5);
                        }
                    };

                    Object[] hovering = {null};
                    h.update(() -> {
                        Object nextHover = control.input.block != null ? control.input.block : blockfrag.hovered();
                        if(nextHover != hovering[0]){
                            hovering[0] = nextHover;
                            rebuild.run();
                        }
                    });
                }).growX().left().minHeight(36f).row();

                t.table(blocks -> {
                    addBlockSelection(blocks);
                }).fillX().left();
            }).width(dsize * 5 + 4f);
            if(mobile){
                editorMain.row().spacerY(() -> {
                    if(control.input instanceof MobileInput mob){
                        if(Core.graphics.isPortrait()) return Core.graphics.getHeight() / 2f / Scl.scl(1f);
                        if(mob.hasSchematic()) return 156f;
                        if(mob.showCancel()) return 50f;
                    }
                    return 0f;
                });
            }
            editorMain.visible(() -> shown && state.isEditor());

            //fps display
            cont.table(info -> {
                info.name = "fps/ping";
                info.touchable = Touchable.disabled;
                info.top().left().margin(4).visible(() -> Core.settings.getBool("fps") && shown);
                IntFormat fps = new IntFormat("fps");
                IntFormat ping = new IntFormat("ping");
                IntFormat tps = new IntFormat("tps");
                IntFormat mem = new IntFormat("memory");
                IntFormat memnative = new IntFormat("memory2");
                IntFormat players = new IntFormat("client.players");
                IntFormat plans = new IntFormat("client.plans");

                if(android){
                    info.label(() -> memnative.get((int)(Core.app.getJavaHeap() / 1024 / 1024), (int)(Core.app.getNativeHeap() / 1024 / 1024))).left().style(Styles.outlineLabel).name("memory2");
                }else{
                    Object[] memBean = {null};
                    Method[] getNonHeapUsage = {null};
                    try{
                        if(Core.settings.getBool("offheapmemorydisplay")){ // FINISHME: This is very much a work in progress
                            memBean[0] = Class.forName("java.lang.management.ManagementFactory").getMethod("getMemoryMXBean").invoke(null);
                            getNonHeapUsage[0] = Class.forName("java.lang.management.MemoryMXBean").getMethod("getNonHeapMemoryUsage");
                        }
                    } catch (ClassNotFoundException | InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
                        Log.err("Failed to get memBean", e);
                    }
                    info.label(() -> {
                        try {
                            return mem.get((int)(Core.app.getJavaHeap() / 1024 / 1024), (int)((Runtime.getRuntime().maxMemory() - Core.app.getJavaHeap()) / 1024 / 1024)) + (memBean[0] == null ? "" : " | Off-heap: " + Reflect.<Long>invoke(getNonHeapUsage[0].invoke(memBean[0]), "getUsed") / 1024 / 1024  + " mb");
                        } catch (IllegalAccessException | InvocationTargetException e) {
                            throw new RuntimeException(e);
                        }
                    }).left().style(Styles.outlineLabel).name("memory");
                }
                info.row();

                info.label(() -> fps.get(Core.graphics.getFramesPerSecond())).left().style(Styles.outlineLabel).name("fps");
                info.row();

                info.label(() -> plans.get(player.dead() ? 0 : player.unit().plans.size, ClientVars.frozenPlans.size)).left() // Buildplan count
                .style(Styles.outlineLabel).name("plans");
                info.row();

                info.label(() -> "Rate Limit: " + ClientVars.ratelimitRemaining).left().style(Styles.outlineLabel).row();

                info.label(() -> players.get(Groups.player.size(), ui.join.lastHost == null ? 0 : ui.join.lastHost.playerLimit)).visible(net::active).left() // Player count
                .style(Styles.outlineLabel).name("players");
                info.row();

                info.label(() -> ping.get(netClient.getPing())).visible(net::client).left()
                .style(Styles.outlineLabel).name("ping");
                info.row();

                info.label(() -> tps.get(state.serverTps == -1 ? 60 : state.serverTps)).visible(net::client).left().style(Styles.outlineLabel).name("tps");
            }).top().left();
        });

        //core info
        parent.fill(t -> {
            t.top();

            if(Core.settings.getBool("macnotch")){
                t.margin(macNotchHeight);
            }

            t.visible(() -> shown);

            t.name = "coreinfo";

            t.collapser(v -> v.add().height(pauseHeight), () -> state.isPaused() && !netServer.isWaitingForPlayers()).row();

            t.table(c -> {
                //core items
                c.top().collapser(coreItems, () -> Core.settings.getBool("coreitems") && shown).fillX().row();

                float notifDuration = 240f;
                float[] coreAttackTime = {0};

                Events.on(TeamCoreDamage.class, event -> {
                    if (Time.timeSinceMillis(lastWarn) > 30_000) { // Prevent chat flooding
                        NetClient.findCoords(ui.chatfrag.addMsg(Strings.format("[scarlet]Core under attack: (@, @)", event.core.x, event.core.y)));
                    }
                    lastWarn = Time.millis(); // Reset timer so that it sends 30s after the last core damage rather than every 30s FINISHME: Better way to do this?
                    coreAttackTime[0] = notifDuration;
                    ClientVars.lastCorePos.set(event.core.x, event.core.y);
                });

                //'core is under attack' table
                c.collapser(top -> top.background(Styles.black6).add("@coreattack").pad(8)
                .update(label -> label.color.set(Color.orange).lerp(Color.scarlet, Mathf.absin(Time.time, 2f, 1f))), true,
                () -> {
                    if(!shown || state.isPaused()) return false;
                    if(state.isMenu() || !player.team().data().hasCore()){
                        coreAttackTime[0] = 0f;
                        return false;
                    }

                    return (coreAttackTime[0] -= Time.delta) > 0;
                })
                .touchable(Touchable.disabled)
                .fillX()
                .get().clicked(() -> {
                    if (Time.timeSinceMillis(lastWarnClick) < 400)  Navigation.navigateTo(ClientVars.lastCorePos.cpy().scl(tilesize));
                    else Spectate.INSTANCE.spectate(ClientVars.lastCorePos.cpy().scl(tilesize));
                    lastWarnClick = Time.millis();
                });
            }).row();

            var bossb = new StringBuilder();
            var bossText = Core.bundle.get("guardian");
            int maxBosses = 6;

            t.table(v -> v.margin(10f)
            .add(new Bar(() -> {
                bossb.setLength(0);
                for(int i = 0; i < Math.min(state.teams.bosses.size, maxBosses); i++){
                    bossb.append(state.teams.bosses.get(i).type.emoji());
                }
                if(state.teams.bosses.size > maxBosses){
                    bossb.append("[accent]+[]");
                }
                bossb.append(" ");
                bossb.append(bossText);
                return bossb;
            }, () -> Pal.health, () -> {
                if(state.boss() == null) return 0f;
                float max = 0f, val = 0f;
                for(var boss : state.teams.bosses){
                    max += boss.maxHealth;
                    val += boss.health;
                }
                return max == 0f ? 0f : val / max;
            }).blink(Color.white).outline(new Color(0, 0, 0, 0.6f), 7f)).grow())
            .fillX().width(320f).height(60f).name("boss").visible(() -> state.rules.waves && state.boss() != null && !(mobile && Core.graphics.isPortrait())).padTop(7).row();

            t.table(Styles.black3, p -> p.margin(4).label(() -> hudText).style(Styles.outlineLabel)).touchable(Touchable.disabled).with(p -> p.visible(() -> {
                p.color.a = Mathf.lerpDelta(p.color.a, Mathf.num(showHudText), 0.2f);
                if(state.isMenu()){
                    p.color.a = 0f;
                    showHudText = false;
                }

                return p.color.a >= 0.001f;
            }));
        });

        //spawner warning
        parent.fill(t -> {
            t.bottom();
            t.name = "nearpoint";
            t.touchable = Touchable.disabled;
            t.table(Styles.black3, c ->
                c.add("@nearpoint")
                .update(l -> l.setColor(Tmp.c1.set(Color.white).lerp(Color.scarlet, Mathf.absin(Time.time, 10f, 1f))))
                .labelAlign(Align.bottom | Align.center, Align.center)
            ).margin(6).update(u ->
                u.color.a = Mathf.lerpDelta(u.color.a, Mathf.num(spawner.playerNear()), 0.1f)
            ).get().color.a = 0f;
        });

        //'saving' indicator
        parent.fill(t -> {
            t.name = "saving";
            t.bottom().visible(() -> control.saves.isSaving());
            t.add("@saving").style(Styles.outlineLabel);
        });

        //TODO DEBUG: rate table
        if(false)
            parent.fill(t -> {
                t.bottom().left();
                t.table(Styles.black6, c -> {
                    Bits used = new Bits(content.items().size);

                    Runnable rebuild = () -> {
                        c.clearChildren();

                        for(Item item : content.items()){
                            if(state.rules.sector != null && state.rules.sector.info.getExport(item) >= 1){
                                c.image(item.uiIcon);
                                c.label(() -> (int)state.rules.sector.info.getExport(item) + " /s").color(Color.lightGray);
                                c.row();
                            }
                        }
                    };

                    c.update(() -> {
                        boolean wrong = false;
                        for(Item item : content.items()){
                            boolean has = state.rules.sector != null && state.rules.sector.info.getExport(item) >= 1;
                            if(used.get(item.id) != has){
                                used.set(item.id, has);
                                wrong = true;
                            }
                        }
                        if(wrong){
                            rebuild.run();
                        }
                    });
                }).visible(() -> state.isCampaign() && content.items().contains(i -> state.rules.sector != null && state.rules.sector.info.getExport(i) > 0));
            });

        blockfrag.build(parent);
    }

    public void modeIcon(Table table, Boolp cond, Runnable toggle, Drawable icon, String text, Binding binding){
        modeIcon(table, cond, toggle, icon, text, binding, null);
    }

    public void modeIcon(Table table, Boolp cond, Runnable toggle, Drawable icon, String text, Binding binding, String modifier){
        var tooltipText = modifier != null
            ? Strings.format("@ [yellow](@ + @)", text, modifier, Core.keybinds.get(binding).key.toString())
            : Strings.format("@ [yellow](@)", text, Core.keybinds.get(binding).key.toString());
        var clicklayer = new Label("");
        clicklayer.clicked(toggle);
        Color gray = new Color(0.4f, 0.4f, 0.4f, 0.4f);
        var disabledIcon = icon instanceof SlashTextureRegionDrawable s ? new SlashTextureRegionDrawable(s.getRegion(), gray) {{
            slashColor = gray;
            slashColorBack = gray;
        }}
            : icon instanceof TextureRegionDrawable d ? d.tint(gray)
            : null;
        table.stack(
            new Image(icon).visible(cond),
            new Image(disabledIcon).visible(() -> !cond.get()),
            clicklayer
        ).size(25f).padRight(8f).padBottom(2f)
        .tooltip(t ->
            t.background(Styles.black6).margin(4f).add(tooltipText).style(Styles.outlineLabel)
        );
    };

    @Remote(targets = Loc.both, forward = true, called = Loc.both)
    public static void setPlayerTeamEditor(Player player, Team team){
        if(state.isEditor() && player != null){
            player.team(team);
        }
    }

    public void setHudText(String text){
        showHudText = true;
        hudText = text;
    }

    public void toggleHudText(boolean shown){
        showHudText = shown;
    }

    private void scheduleToast(Runnable run){
        long duration = (int)(3.5 * 1000);
        long since = Time.timeSinceMillis(lastToast);
        if(since > duration){
            lastToast = Time.millis();
            run.run();
        }else{
            Time.runTask((duration - since) / 1000f * 60f, run);
            lastToast += duration;
        }
    }

    public boolean hasToast(){
        return Time.timeSinceMillis(lastToast) < 3.5f * 1000f;
    }

    public void showToast(String text){
        showToast(Icon.ok, text);
    }

    public void showToast(Drawable icon, String text){
        showToast(icon, -1, text);
    }

    public void showToast(Drawable icon, float size, String text){
        if(state.isMenu()) return;

        scheduleToast(() -> {
            Sounds.message.play();

            Table table = new Table(Tex.button);
            table.update(() -> {
                if(state.isMenu() || !ui.hudfrag.shown){
                    table.remove();
                }
            });
            table.margin(12);
            var cell = table.image(icon).pad(3);
            if(size > 0) cell.size(size);
            table.add(text).wrap().width(280f).get().setAlignment(Align.center, Align.center);
            table.pack();

            //create container table which will align and move
            Table container = Core.scene.table();
            container.top().add(table);
            container.setTranslation(0, table.getPrefHeight());
            container.actions(Actions.translateBy(0, -table.getPrefHeight(), 1f, Interp.fade), Actions.delay(2.5f),
            //nesting actions() calls is necessary so the right prefHeight() is used
            Actions.run(() -> container.actions(Actions.translateBy(0, table.getPrefHeight(), 1f, Interp.fade), Actions.remove())));
        });
    }

    /** Show unlock notification for a new recipe. */
    public void showUnlock(UnlockableContent content){
        //some content may not have icons... yet
        //also don't play in the tutorial to prevent confusion
        if(state.isMenu()) return;

        Sounds.message.play();

        //if there's currently no unlock notification...
        if(lastUnlockTable == null){
            scheduleToast(() -> {
                Table table = new Table(Tex.button);
                table.update(() -> {
                    if(state.isMenu()){
                        table.remove();
                        lastUnlockLayout = null;
                        lastUnlockTable = null;
                    }
                });
                table.margin(12);

                Table in = new Table();

                //create texture stack for displaying
                Image image = new Image(content.uiIcon);
                image.setScaling(Scaling.fit);

                in.add(image).size(8 * 6).pad(2);

                //add to table
                table.add(in).padRight(8);
                table.add("@unlocked");
                table.pack();

                //create container table which will align and move
                Table container = Core.scene.table();
                container.top().add(table);
                container.setTranslation(0, table.getPrefHeight());
                container.actions(Actions.translateBy(0, -table.getPrefHeight(), 1f, Interp.fade), Actions.delay(2.5f),
                //nesting actions() calls is necessary so the right prefHeight() is used
                Actions.run(() -> container.actions(Actions.translateBy(0, table.getPrefHeight(), 1f, Interp.fade), Actions.run(() -> {
                    lastUnlockTable = null;
                    lastUnlockLayout = null;
                }), Actions.remove())));

                lastUnlockTable = container;
                lastUnlockLayout = in;
            });
        }else{
            //max column size
            int col = 3;
            //max amount of elements minus extra 'plus'
            int cap = col * col - 1;

            //get old elements
            Seq<Element> elements = new Seq<>(lastUnlockLayout.getChildren());
            int esize = elements.size;

            //...if it's already reached the cap, ignore everything
            if(esize > cap) return;

            //get size of each element
            float size = 48f / Math.min(elements.size + 1, col);

            lastUnlockLayout.clearChildren();
            lastUnlockLayout.defaults().size(size).pad(2);

            for(int i = 0; i < esize; i++){
                lastUnlockLayout.add(elements.get(i));

                if(i % col == col - 1){
                    lastUnlockLayout.row();
                }
            }

            //if there's space, add it
            if(esize < cap){

                Image image = new Image(content.uiIcon);
                image.setScaling(Scaling.fit);

                lastUnlockLayout.add(image);
            }else{ //else, add a specific icon to denote no more space
                lastUnlockLayout.image(Icon.add);
            }

            lastUnlockLayout.pack();
        }
    }

    /** @deprecated see {@link CoreBuild#beginLaunch(CoreBlock)} */
    @Deprecated
    public void showLaunch(){
        float margin = 30f;

        Image image = new Image();
        image.color.a = 0f;
        image.touchable = Touchable.disabled;
        image.setFillParent(true);
        image.actions(Actions.delay((coreLandDuration - margin) / 60f), Actions.fadeIn(margin / 60f, Interp.pow2In), Actions.delay(6f / 60f), Actions.remove());
        image.update(() -> {
            image.toFront();
            ui.loadfrag.toFront();
            if(state.isMenu()){
                image.remove();
            }
        });
        Core.scene.add(image);
    }

    /** @deprecated see {@link CoreBuild#beginLaunch(CoreBlock)} */
    @Deprecated
    public void showLand(){
        Image image = new Image();
        image.color.a = 1f;
        image.touchable = Touchable.disabled;
        image.setFillParent(true);
        image.actions(Actions.fadeOut(35f / 60f), Actions.remove());
        image.update(() -> {
            image.toFront();
            ui.loadfrag.toFront();
            if(state.isMenu()){
                image.remove();
            }
        });
        Core.scene.add(image);
    }

    private void toggleMenus(){
        if(flip != null){
            flip.getStyle().imageUp = shown ? Icon.downOpen : Icon.upOpen;
        }

        shown = !shown;
    }

    private Table makeStatusTable(){
        Table table = new Table(Tex.wavepane);

        StringBuilder ibuild = new StringBuilder();

        IntFormat
        wavef = new IntFormat("wave"),
        wavefc = new IntFormat("wave.cap"),
        enemyf = new IntFormat("wave.enemy"),
        enemiesf = new IntFormat("wave.enemies"),
        enemycf = new IntFormat("wave.enemycore"),
        enemycsf = new IntFormat("wave.enemycores"),
        waitingf = new IntFormat("wave.waiting", i -> {
            ibuild.setLength(0);
            int m = i/60;
            int s = i % 60;
            if(m > 0){
                ibuild.append(m);
                ibuild.append(":");
                if(s < 10){
                    ibuild.append("0");
                }
            }
            ibuild.append(s);
            return ibuild.toString();
        });

        table.touchable = Touchable.enabled;

        StringBuilder builder = new StringBuilder();

        table.name = "waves";
        table.marginTop(0).marginBottom(4).marginLeft(4);

        class SideBar extends Element{
            public final Floatp amount;
            public final boolean flip;
            public final Boolp flash;
            public float lineWidth = 1; // Width as a percent, 0-1

            float last, blink, value;

            public SideBar(Floatp amount, Boolp flash, boolean flip){
                this.amount = amount;
                this.flip = flip;
                this.flash = flash;

                setColor(Pal.health);
            }

            public SideBar(Floatp amount, Boolp flash, boolean flip, float lineWidth){
                this.amount = amount;
                this.flip = flip;
                this.flash = flash;
                this.lineWidth = lineWidth;

                setColor(Pal.health);
            }

            @Override
            public void draw(){
                float next = amount.get();

                if(Float.isNaN(next) || Float.isInfinite(next)) next = 1f;

                if(next < last && flash.get()){
                    blink = 1f;
                }

                blink = Mathf.lerpDelta(blink, 0f, 0.2f);
                value = Mathf.lerpDelta(value, next, 0.15f);
                last = next;

                if(Float.isNaN(value) || Float.isInfinite(value)) value = 1f;

                drawInner(Pal.darkishGray, 1f);
                drawInner(Tmp.c1.set(color).lerp(Color.white, blink), value);
            }

            void drawInner(Color color, float fract){
                if(fract < 0) return;

                fract = Mathf.clamp(fract);
                if(flip){
                    x += width;
                    width = -width;
                }

                float stroke = width * 0.35f;
                float bh = height/2f;
                Draw.color(color, parentAlpha);

                float f1 = Math.min(fract * 2f, 1f), f2 = (fract - 0.5f) * 2f;

                float bo = -(1f - f1) * (width - stroke);

                Fill.quad(
                x + stroke * (1f - lineWidth), y,
                x + stroke, y,
                x + width + bo, y + bh * f1,
                x + width - stroke * lineWidth + bo, y + bh * f1
                );

                if(f2 > 0){
                    float bx = x + (width - stroke) * (1f - f2);
                    Fill.quad(
                    x + width, y + bh,
                    x + width - stroke * lineWidth, y + bh,
                    bx + stroke * (1f - lineWidth), y + height * fract,
                    bx + stroke, y + height * fract
                    );
                }

                Draw.reset();

                if(flip){
                    width = -width;
                    x -= width;
                }
            }
        }

        table.stack(
        new Element(){
            @Override
            public void draw(){
                Draw.color(Pal.darkerGray, parentAlpha);
                Fill.poly(x + width/2f, y + height/2f, 6, height / Mathf.sqrt3);
                Draw.reset();
                Drawf.shadow(x + width/2f, y + height/2f, height * 1.13f, parentAlpha);
            }
        },
        new Table(t -> {
            float bw = 40f; // Bar width
            float pad = -20;
            t.margin(0);
            t.clicked(() -> {
                if(!player.dead() && mobile){
                    Call.unitClear(player);
                    control.input.recentRespawnTimer = 1f;
                    control.input.controlledType = null;
                }
            });

            float[] maxShield = {0};
            t.stack(
                new Table(tt -> // Health
                    tt.add(new SideBar(() -> player.dead() ? 0f : player.unit().healthf(), () -> true, true))
                    .tooltip(tooltip ->
                        tooltip.background(Styles.black6).margin(4f)
                        .label(() ->
                            !player.dead() && player.unit().shield > 0
                            ? Strings.format("@: (@ + @)/@", Core.bundle.get("stat.health"), Mathf.round(player.unit().health, 0.1f), Mathf.round(player.unit().shield, 0.1f), player.unit().maxHealth)
                            : Strings.format("@: @/@", Core.bundle.get("stat.health"), Mathf.round(player.unit().health, 0.1f), player.unit().maxHealth)
                        ).style(Styles.outlineLabel)
                    )
                    .width(bw).growY().padRight(pad)
                ),
                new Table(tt -> // Shield
                    tt.add(new SideBar(() -> player.dead() ? 0 : player.unit().shield / maxShield[0], () -> true, true, 1/4f))
                    .width(bw).growY().padRight(pad).color(Pal.accent)
                    .visible(() -> {
                        if(player.dead()) return false;
                        var ff = ArraysKt.firstOrNull(player.unit().abilities, a -> a instanceof ForceFieldAbility);

                        maxShield[0] = ff == null ? 0f : ((ForceFieldAbility)ff).max;
                        return maxShield[0] > 0;
                    })
                )
            ).fillY();
            t.image(() -> player.icon()).scaling(Scaling.bounded).grow().maxWidth(54f);
            t.add(new SideBar(() -> player.dead() ? 0f : player.displayAmmo() ? player.unit().ammof() : player.unit().healthf(), () -> !player.displayAmmo(), false)).width(bw).growY().padLeft(pad).update(b -> {
                b.color.set(player.displayAmmo() ? player.dead() || player.unit() instanceof BlockUnitc ? Pal.ammo : player.unit().type.ammoType.color() : Pal.health);
            });

            t.getChildren().get(1).toFront();
        })).size(120f, 80).padRight(4);

        Cell[] lcell = {null};
        boolean[] couldSkip = {true};

        lcell[0] = table.labelWrap(() -> {

//            //update padding depend on whether the button to the right is there
//            boolean can = canSkipWave();
//            if(can != couldSkip[0]){
//                if(canSkipWave()){
//                    lcell[0].padRight(8f);
//                }else{
//                    lcell[0].padRight(-42f);
//                }
//                table.invalidateHierarchy();
//                table.pack();
//                couldSkip[0] = can;
//            }

            builder.setLength(0);

            //mission overrides everything
            if(state.rules.mission != null && state.rules.mission.length() > 0){
                builder.append(state.rules.mission);
                return builder;
            }

            //objectives override mission?
            if(state.rules.objectives.any()){
                boolean first = true;
                for(var obj : state.rules.objectives){
                    if(!obj.qualified() || obj.hidden) continue;

                    String text = obj.text();
                    if(text != null && !text.isEmpty()){
                        if(!first) builder.append("\n[white]");
                        builder.append(text);

                        first = false;
                    }
                }

                //TODO: display standard status when empty objective?
                if(builder.length() > 0){
                    return builder;
                }
            }

            if(!state.rules.waves && state.rules.attackMode){
                int sum = Math.max(state.teams.present.sum(t -> t.team != player.team() ? t.cores.size : 0), 1);
                builder.append(sum > 1 ? enemycsf.get(sum) : enemycf.get(sum));
                return builder;
            }

            if(!state.rules.waves && state.isCampaign()){
                builder.append("[lightgray]").append(Core.bundle.get("sector.curcapture"));
            }

            if(!state.rules.waves){
                return builder;
            }

            if(state.rules.winWave > 1 && state.rules.winWave >= state.wave){
                builder.append(wavefc.get(state.wave, state.rules.winWave));
            }else{
                builder.append(wavef.get(state.wave));
            }
            builder.append("\n");

            if(state.rules.attackMode){
                int sum = Math.max(state.teams.present.sum(t -> t.team != player.team() ? t.cores.size : 0), 1);
                builder.append(sum > 1 ? enemycsf.get(sum) : enemycf.get(sum)).append("\n");
            }

            if(state.enemies > 0){
                if(state.enemies == 1){
                    builder.append(enemyf.get(state.enemies));
                }else{
                    builder.append(enemiesf.get(state.enemies));
                }
                builder.append("\n");
            }

            if(state.rules.waveTimer){
                builder.append((logic.isWaitingWave() ? Core.bundle.get("wave.waveInProgress") : (waitingf.get((int)(state.wavetime/60)))));
            }else if(state.enemies == 0){
                builder.append(Core.bundle.get("waiting"));
            }

            return builder;
        }).growX().pad(8f);

        table.row();

        //TODO nobody reads details anyway.
        /*
        table.clicked(() -> {
            if(state.rules.objectives.any()){
                StringBuilder text = new StringBuilder();

                boolean first = true;
                for(var obj : state.rules.objectives){
                    if(!obj.qualified()) continue;

                    String details = obj.details();
                    if(details != null){
                        if(!first) text.append('\n');
                        text.append(details);

                        first = false;
                    }
                }

                //TODO this, as said before, could be much better.
                ui.showInfo(text.toString());
            }
        });*/

        return table;
    }

    /** Displays player payloads and status effects. */
    private void addInfoTable(Table table){
        table.name = "infotable";

        var count = new float[]{-1};
        table.table().update(t -> {
            if(player.unit() instanceof Payloadc payload){
                if(count[0] != payload.payloadUsed()){
                    payload.contentInfo(t, 8 * 2, 275f);
                    count[0] = payload.payloadUsed();
                }
            }else{
                count[0] = -1;
                t.clear();
            }
        }).growX().visible(() -> player.unit() instanceof Payloadc p && p.payloadUsed() > 0);
        table.row();

        Bits statuses = new Bits();

        table.table().update(t -> {
            Bits applied = player.dead() ? null : player.unit().statusBits();
            if(!statuses.equals(applied)){
                t.clear();

                if(applied != null){
                    for(StatusEffect effect : content.statusEffects()){
                        if(applied.get(effect.id) && !effect.isHidden()){
                            t.image(effect.uiIcon).size(iconMed).get()
                            .addListener(new Tooltip(l -> l.label(() ->
                                effect.localizedName + " [lightgray]" + UI.formatTime(player.unit().getDuration(effect))).style(Styles.outlineLabel)));
                        }
                    }

                    statuses.set(applied);
                }else{
                    statuses.clear();
                }
            }
        }).growX();
    }

    private boolean canSkipWave(){
        return state.rules.waves && (state.rules.winWave <= 0 || state.wave < state.rules.winWave) && (net.server() || !net.active() || Server.current.adminui()) /* && state.enemies == 0 && !spawner.isSpawning() */;
    }

}
