package mindustry.ui.fragments;

import arc.*;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.ImageButton.*;
import arc.scene.ui.TextButton.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.client.*;
import mindustry.client.navigation.*;
import mindustry.client.navigation.AssistPath.*;
import mindustry.client.utils.*;
import mindustry.content.*;
import mindustry.game.EventType.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.net.*;
import mindustry.net.Packets.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;

import static mindustry.Vars.*;

public class PlayerListFragment{
    public Table content = new Table().marginRight(13f).marginLeft(13f);
    private boolean visible = false;
    private TextField search;
    private final Seq<Player> players = new Seq<>();
    private Func<Player, String> playerName = p -> "";
    private Func<Player, String> playerClipboard = p -> "";
    private boolean showTeams;
    private int lastMod = -2;
    private final TextureRegionDrawable adminIcon = new TextureRegionDrawable(Fonts.getLargeIcon("admin")),
    hammerIcon = new TextureRegionDrawable(Fonts.getLargeIcon("hammer")),
    kickIcon = new TextureRegionDrawable(Fonts.getLargeIcon("exit")),
    traceIcon = new TextureRegionDrawable(Fonts.getLargeIcon("zoom")),
    teamIcon = new TextureRegionDrawable(Fonts.getLargeIcon("redo")),
    assistIcon = new TextureRegionDrawable(Fonts.getLargeIcon("copy")),
    muteIcon = new TextureRegionDrawable(Fonts.getLargeIcon("lock")),
    blockBuildingIcon = new TextureRegionDrawable(Fonts.getLargeIcon("cancel")),
    moveIcon = new TextureRegionDrawable(Fonts.getLargeIcon("move")),
    clientIcon = new TextureRegionDrawable(Fonts.getLargeIcon("wrench"));

    public void build(Group parent){
        content.name = "players";

        parent.fill(cont -> {
            cont.name = "playerlist";
            cont.visible(() -> visible);
            cont.update(() -> {
                if(!visible) return;
                if(!state.isGame()){
                    visible = false;
                    return;
                }

                showTeams = state.rules.pvp ||
                    Core.settings.getBool("alwaysshowteams") ||
                    (player != null && Groups.player.find(p -> p.team() != player.team()) != null);

                int mod = Core.input.shift() ? 0 : Core.input.ctrl() ? 1 : Core.input.alt() ? 2 : -1;
                if (mod != lastMod) { // Update modifier key based on what is held.
                    lastMod = mod;
                    // Set defaults in case they are not overwritten
                    playerName = p -> p.coloredName() + (Core.settings.getBool("showuserid") ? " [accent](#" + p.id + ")" : "");
                    playerClipboard = Player::plainName;

                    if (Core.input.shift()) {
                        playerName = playerClipboard = p -> String.valueOf(p.id);
                    } else if (Core.input.ctrl()) {
                        playerName = playerClipboard = p -> "Groups.player.getByID(" + p.id + ")";
                    } else if (Core.input.alt()) {
                        var idMapper = Server.current.playerIDCopy;
                        if (idMapper != null) { // Server specific id support (i.e. io player codes and such)
                            playerName = p -> p.coloredName() + "[accent] | " + idMapper.get(p);
                            playerClipboard = idMapper;
                        }
                        // Client/host on a steam server with admin
                        if (steam && ui.join.lastHost == null && player.admin && Groups.player.contains(p -> p.ip().startsWith("steam:") || p.trace != null && p.trace.ip.startsWith("steam:"))) {
                            playerName = p -> p.coloredName() + (playerClipboard.get(p) != null ? "[accent] | " + playerClipboard.get(p) : "");
                            playerClipboard = p -> p.trace != null && p.trace.ip.startsWith("steam:") ? p.trace.uuid : p.ip().startsWith("steam:") ? p.uuid() : null;
                        }
                    }
                    rebuild();
                }
            });

            cont.table(Tex.buttonTrans, pane -> {
                pane.label(this::formatLabel);
                pane.row();

                search = pane.field(null, text -> rebuild()).grow().pad(8).name("search").maxTextLength(maxNameLength).get();
                search.setMessageText(Core.bundle.get("players.search"));

                pane.row();
                pane.pane(content).grow().scrollX(false);
                pane.row();

                pane.table(menu -> {
                    menu.defaults().pad(5).growX().height(50f).fillY();
                    menu.name = "menu";

                    menu.button("@server.bans", ui.bans::show).disabled(b -> net.client()).get().getLabel().setWrap(false);
                    menu.button("@server.admins", ui.admins::show).disabled(b -> net.client()).get().getLabel().setWrap(false);
                    menu.button("@close", this::toggle).get().getLabel().setWrap(false);
                }).margin(0f).pad(10f).growX();

            }).touchable(Touchable.enabled).margin(14f).minWidth(500f);
        });

        Events.on(UnitChangeEventClient.class, e -> {
            if(e.oldUnit != null && e.newUnit != null && (e.oldUnit.type.id != e.newUnit.type.id || e.newUnit.type == UnitTypes.block)){
                if(visible) rebuild();
            }
        });
        Events.on(PlayerJoin.class, e -> {
            if(visible) rebuild();
        });
        Events.on(WorldLoadEvent.class, e -> Timer.schedule(() -> {
            if(visible) rebuild();
        }, .5f, 1f , 10)); // delay so client has time to get all the players. Certain things such as server id are requested from the server.
    }

    public void rebuild(){
        content.clear();
        boolean adminui = net.server() || Server.current.adminui();

        float h = 80f;
        float bs = h / 2;
        float width = 700f + (Server.current.freeze.canRun() ? 20f : 0) + (Server.current.mute.canRun() ? 20f : 0);
        boolean found = false;

        players.clear();
        Groups.player.copy(players);

        var target = Spectate.INSTANCE.getPos() instanceof Player p ? p :
            Navigation.currentlyFollowing instanceof AssistPath p && p.getAssisting() != null ? p.getAssisting() :
            Navigation.currentlyFollowing instanceof UnAssistPath p ? p.target :
            null;
        players.sort(
            Structs.comps(Structs.comparingBool(p -> p != target),
            Structs.comps(Structs.comparing(Player::team),
            Structs.comps(Structs.comparingBool(p -> !p.admin),
            Structs.comparingBool(p -> !(p.fooUser || p.isLocal()))
            ))));
        if(search.getText().length() > 0) {
            players.retainAll(p -> Strings.stripColors(p.name().toLowerCase()).contains(search.getText().toLowerCase()));
        }

        var style = new ImageButtonStyle(){{
            down = Styles.none;
            up = Styles.none;
            imageCheckedColor = Pal.accent;
            imageDownColor = Pal.accent;
            imageUpColor = Color.white;
            imageOverColor = Color.lightGray;
        }};

        var ustyle = new ImageButtonStyle(){{
            down = Styles.none;
            up = Styles.none;
            imageDownColor = Pal.accent;
            imageUpColor = Color.white;
            imageOverColor = Color.lightGray;
        }};

        var nameStyle = new TextButtonStyle(){{
            down = Styles.none;
            up = Styles.none;
            font =  Fonts.outline;
        }};

        for(var user : players){
            found = true;
            NetConnection connection = user.con;

            if(connection == null && net.server() && !user.isLocal()) return;


            Table button = new Table(){
                @Override
                public void draw(){
                    super.draw();
                    Draw.color(showTeams && !Core.settings.getBool("playerliststyle") ? user.team().color : Pal.gray); // FINISHME: The always outline units & always show teams settings interfere with each other.
                    Fill.crect(x, y, width, 4);
                    Draw.reset();
                }
            };
            button.left();
            button.margin(5).marginBottom(10);

            ClickListener listener = new ClickListener();
            Table iconTable = new Table(){
                @Override
                public void draw(){
                    super.draw();
                    if(Core.settings.getBool("playerliststyle")){
                        //Square always
                        Draw.color(user.team().color);
                        Draw.alpha(parentAlpha);
                        Lines.stroke(Scl.scl(4f));
                        Lines.rect(x, y, width, height);
                        if(listener.isOver()){
                            //Fill in when hovered
                            Draw.alpha(parentAlpha * 0.2f);
                            Fill.crect(x, y, width, height);
                        }
                        Draw.reset();
                    } else {
                        if(listener.isOver()){
                            //Square only when hovered
                            Draw.color(Pal.accent);
                            Draw.alpha(parentAlpha);
                            Lines.stroke(Scl.scl(4f));
                            Lines.rect(x, y, width, height);
                            Draw.reset();
                        }
                    }
                }
            };

            iconTable.addListener(listener);
            iconTable.addListener(new HandCursorListener());
            iconTable.margin(8);
            iconTable.add(new Image(user.icon()).setScaling(Scaling.bounded)).grow();
            iconTable.name = user.name();
            iconTable.touchable = user == player ? Touchable.disabled : Touchable.enabled;

            iconTable.tapped(() -> {
                if(!user.dead()){
                    Spectate.INSTANCE.spectate(user, Core.input.shift());
                    ui.showInfoToast(Core.bundle.format("viewplayer", user.coloredName()), 1.5f);
                    Core.app.post(this::rebuild);
                }
            });

            button.add(iconTable).size(h);

            Button nameButton = new Button(nameStyle);
            nameButton.clicked(() -> {
                var clip = playerClipboard.get(user);
                if (clip != null && !clip.isEmpty()) {
                    Core.app.setClipboardText(clip);
                    ui.showInfoToast("@client.copy", 1.5f);
                } else ui.showInfoToast("@client.nocopy", 1.5f);
            });

            nameButton.add(new Label(playerName.get(user), new Label.LabelStyle(Fonts.outline, Color.white)));

            if(user.fooUser || (user.isLocal() && Core.settings.getBool("displayasuser"))){
                nameButton.add(new Image(clientIcon).setScaling(Scaling.fit))
                    .size(h/5).scaling(Scaling.fit).padLeft(4f).padBottom(18f).tooltip("@client.clientuser");
            }

            button.add(nameButton).width(400).pad(10);

            button.add().growX();
            if(net.server()){
                button.button(adminIcon, style, () -> {
                    String id = user.uuid();

                    if(user.admin){
                        ui.showConfirm("@confirm", Core.bundle.format("confirmunadmin", user.name()), () -> {
                            netServer.admins.unAdminPlayer(id);
                            user.admin = false;
                            rebuild();
                        });
                    }else{
                        ui.showConfirm("@confirm", Core.bundle.format("confirmadmin", user.name()), () -> {
                            netServer.admins.adminPlayer(id, user.usid());
                            user.admin = true;
                            rebuild();
                        });
                    }
                    }).update(b -> b.setChecked(user.admin))
                    .checked(user.admin)
                    .tooltip("@player.admin").get().resizeImage(h/2.2f);
            }

            button.table(t -> {
                t.defaults().size(bs);

                if(user.admin && net.client()){
                    t.image(adminIcon);
                }

                if(adminui && (!user.admin || user.isLocal())){
                    if(!user.isLocal()){
                        t.button(hammerIcon, ustyle,
                            () -> ui.showConfirm("@confirm", Core.bundle.format("confirmban", user.name()),
                                () -> Server.current.handleBan(user))
                        ).tooltip("@player.ban").get().resizeImage(h/2.2f);

                        t.button(kickIcon, ustyle,
                            () -> ui.showConfirm("@confirm", Core.bundle.format("confirmkick", user.name()),
                                () -> Call.adminRequest(user, AdminAction.kick, null))
                        ).tooltip("@player.kick").get().resizeImage(h/2.2f);

                        t.button(traceIcon, ustyle,
                            () -> Call.adminRequest(user, AdminAction.trace, null)
                        ).tooltip("@player.trace").get().resizeImage(h/2.2f);
                    }

                    t.button(teamIcon, ustyle, () -> {
                        var teamSelect = new BaseDialog(Core.bundle.get("player.team") + ": " + user.name);
                        teamSelect.setFillParent(false);

                        var group = new ButtonGroup<>();

                        int i = 0;

                        for(Team team : Team.baseTeams){
                            var b = new ImageButton(Tex.whiteui, Styles.clearNoneTogglei);
                            b.margin(4f);
                            b.getImageCell().grow();
                            b.getStyle().imageUpColor = team.color;
                            b.clicked(() -> {
                                Call.adminRequest(user, AdminAction.switchTeam, team);
                                teamSelect.hide();
                            });
                            teamSelect.cont.add(b).size(50f).checked(a -> user.team() == team).group(group);

                            if(i++ % 3 == 2) teamSelect.cont.row();
                        }

                        teamSelect.addCloseButton();
                        teamSelect.show();
                    }).tooltip("@player.team").get().resizeImage(h/2.2f);

                    // Server-integrated buttons
                    if(Server.current.freeze.canRun()){
                        t.button(new TextureRegionDrawable(StatusEffects.freezing.uiIcon).tint(Color.cyan), ustyle, () ->
                        Server.current.handleFreeze(user)
                        ).tooltip("@client.freeze");
                    }

                    if(user == player && Server.current.mute.canRun()){ // If the player is local, the mute button won't be drawn on a new row, otherwise it will be drawn on a new row
                        t.button(new TextureRegionDrawable(StatusEffects.disarmed.uiIcon).tint(Color.gray), ustyle, () ->
                        Server.current.handleMute(user)
                        ).tooltip("@client.modmute");
                    }

                    t.row();
                }

                if(!adminui && !user.admin && net.client() && Groups.player.size() >= 3 && player.team() == user.team()){
                    button.button(hammerIcon, ustyle,
                        () -> ui.showTextInput("@votekick.reason", Core.bundle.format("votekick.reason.message", user.name()), "", reason -> {
                            Call.sendChatMessage("/votekick #" + user.id() + " " + reason);
                            if(Server.io.b() && (user.trace != null || user.serverID != null))
                                ui.showConfirm("@confirm", "Do you want to rollback this player's actions?", () ->
                                    Call.sendChatMessage(Strings.format("/rollback @ 5", user.trace != null ? user.trace.uuid : user.serverID))
                                );
                        })).size(h/2).tooltip("@player.kick").get().resizeImage(h/2.2f);
                }
                if(user != player){
                    t.button(muteIcon, ustyle, // Mute player
                        () -> ClientUtils.toggleMutePlayer(user)
                    ).size(h / 2).tooltip("@client.mute").get().resizeImage(h/2.2f);

                    t.button(assistIcon, ustyle, // Assist/copy
                        () -> Navigation.follow(new AssistPath(user,
                            Core.input.shift() ? Type.FreeMove :
                            Core.input.ctrl() ? Type.Cursor :
                            Core.input.alt() ? Type.BuildPath :
                            Type.Regular, Core.settings.getBool("circleassist")))
                    ).size(h / 2).tooltip("@client.assist").get().resizeImage(h/2.2f);

                    t.button(blockBuildingIcon, ustyle, // Unassist/block
                        () -> Navigation.follow(new UnAssistPath(user, !Core.input.shift()))
                    ).size(h / 2).tooltip("@client.unassist").get().resizeImage(h/2.3f);

                    t.button(moveIcon, ustyle, // Goto
                        () -> Navigation.navigateTo(user)
                    ).size(h / 2).tooltip("@client.goto").get().resizeImage(h/2.2f);

                    if(Server.current.mute.canRun()){ // Server-integrated mute
                        t.button(new TextureRegionDrawable(StatusEffects.disarmed.uiIcon).tint(Color.gray), ustyle, () ->
                        Server.current.handleMute(user)
                        ).tooltip("@client.modmute");
                    }
                }
            }).height(bs);

            var cell = content.add(button);
            if(!Core.settings.getBool("playerliststyle")){
                cell.padBottom(-6);
            }
            cell.width(width).maxHeight(h + 14);
            content.row();
        }

        if(!found){
            content.add(Core.bundle.format("players.notfound")).padBottom(6).width(600f).maxHeight(h + 14);
        }

        content.marginBottom(5);
    }

    public void toggle(){
        visible = !visible;
        if(visible){
            rebuild();
        }else{
            Core.scene.setKeyboardFocus(null);
            search.clearText();
            playerName = p -> p.coloredName() + (Core.settings.getBool("showuserid") ? " [accent](#" + p.id + ")" : "");
            playerClipboard = Player::plainName;
        }
    }

    public boolean shown(){
        return visible;
    }

    private String formatLabel(){
        return Core.bundle.format("players" + (Groups.player.size() == 1 && (ui.join.lastHost == null || ui.join.lastHost.playerLimit <= 0) ? ".single" : ""),
            Groups.player.size() + " (" + Groups.player.count(p -> p.fooUser || p.isLocal()) + Iconc.wrench + ") " +
                (ui.join.lastHost != null && ui.join.lastHost.playerLimit > 0 ? " / " + ui.join.lastHost.playerLimit : ""));
    }
}