package mindustry.ui.fragments;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.ImageButton.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.Vars;
import mindustry.client.*;
import mindustry.client.navigation.*;
import mindustry.client.utils.*;
import mindustry.content.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.net.*;
import mindustry.net.Packets.*;
import mindustry.ui.*;

import static mindustry.Vars.*;

public class PlayerListFragment{
    public Table content = new Table().marginRight(13f).marginLeft(13f);
    private boolean visible = false;
    private final Interval timer = new Interval();
    private TextField search;
    private final Seq<Player> players = new Seq<>();

    public void build(Group parent){
        content.name = "players";

        parent.fill(cont -> {
            cont.name = "playerlist";
            cont.visible(() -> visible);
            cont.update(() -> {
                if(!state.isGame()){
                    visible = false;
                    return;
                }

                if(visible && timer.get(60) /*&& !Core.input.keyDown(KeyCode.mouseLeft) && !(Core.scene.hit(Core.input.mouseX(), Core.input.mouseY(), true) instanceof Image || Core.scene.hit(Core.input.mouseX(), Core.input.mouseY(), true) instanceof ImageButton)*/){
                    rebuild();
                }
            });

            cont.table(Tex.buttonTrans, pane -> {
                pane.label(() -> Core.bundle.format("players" + (Groups.player.size() == 1 && (ui.join.lastHost == null || ui.join.lastHost.playerLimit <= 0) ? ".single" : ""), Groups.player.size() + " (" + Groups.player.count(p -> p.fooUser || p.isLocal()) + Iconc.wrench + ") " + (ui.join.lastHost != null && ui.join.lastHost.playerLimit > 0 ? " / " + ui.join.lastHost.playerLimit : "")));
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

            }).touchable(Touchable.enabled).margin(14f).minWidth(400f);
        });

        rebuild();
    }

    public void rebuild(){
        content.clear();

        float h = 74f;
        boolean found = false;

        players.clear();
        Groups.player.copy(players);

        var target = Spectate.INSTANCE.getPos() instanceof Player p ? p :
            Navigation.currentlyFollowing instanceof AssistPath p && p.getAssisting() != null ? p.getAssisting() :
            Navigation.currentlyFollowing instanceof UnAssistPath p ? p.target :
            null;
        players.sort(Structs.comps(Structs.comparingBool(p -> p != target), Structs.comps(Structs.comparing(Player::team), Structs.comps(Structs.comparingBool(p -> !p.admin), Structs.comparingBool(p -> !(p.fooUser || p.isLocal()))))));
        if(search.getText().length() > 0) players.filter(p -> Strings.stripColors(p.name().toLowerCase()).contains(search.getText().toLowerCase()));

        for(var user : players){
            found = true;
            NetConnection connection = user.con;

            if(connection == null && net.server() && !user.isLocal()) return;

            Table button = new Table();
            button.left();
            button.margin(5).marginBottom(10);

            Table table = new Table(){
                @Override
                public void draw(){
                    super.draw();
                    Draw.color(Pal.gray);
                    Draw.alpha(parentAlpha);
                    Lines.stroke(Scl.scl(4f));
                    Lines.rect(x, y, width, height);
                    Draw.reset();
                }
            };
            table.margin(8);
            table.add(new Image(user.icon()).setScaling(Scaling.bounded)).grow();
            table.name = user.name();

            button.add(table).size(h);
            button.button( // This is by far the worst line of code I have ever written, its split so its not 500+ chars but still jesus
                Core.input.shift() ? String.valueOf(user.id) :
                    Core.input.ctrl() ? "Groups.player.getByID(" + user.id + ")" :
                    "[#" + user.color().toString().toUpperCase() + "]" + user.name() + (Core.settings.getBool("showuserid") ? " [accent](#" + user.id + ")" : ""),
                Styles.nonetdef, () ->
                Core.app.setClipboardText(Core.input.shift() ? String.valueOf(user.id) :
                    Core.input.ctrl() ? "Groups.player.getByID(" + user.id + ")" :
                    Strings.stripColors(user.name))
            ).wrap().width(400).growY().pad(10);

            if (user.admin && !(!user.isLocal() && net.server())) button.image(Icon.admin).padRight(7.5f);
            if (user.fooUser || (user.isLocal() && Core.settings.getBool("displayasuser"))) button.image(Icon.wrench).padRight(7.5f).tooltip("@client.clientuser");

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

            if((net.server() || player.admin) && !user.isLocal() && (!user.admin || net.server())){
                button.add().growY();

                float bs = (h) / 2f;

                button.table(t -> {
                    t.defaults().size(bs);

                    t.button(Icon.hammer, ustyle,
                    () -> ui.showConfirm("@confirm", Core.bundle.format("confirmban", user.name()), () -> Call.adminRequest(user, AdminAction.ban)));
                    t.button(Icon.cancel, ustyle,
                    () -> ui.showConfirm("@confirm", Core.bundle.format("confirmkick", user.name()), () -> Call.adminRequest(user, AdminAction.kick)));

                    t.row();

                    t.button(Icon.admin, style, () -> {
                        if(net.client()) return;

                        String id = user.uuid();

                        if(user.admin){
                            ui.showConfirm("@confirm", Core.bundle.format("confirmunadmin",  user.name()), () -> {
                                netServer.admins.unAdminPlayer(id);
                                user.admin = false;
                            });
                        }else{
                            ui.showConfirm("@confirm", Core.bundle.format("confirmadmin",  user.name()), () -> {
                                netServer.admins.adminPlayer(id, user.usid());
                                user.admin = true;
                            });
                        }
                    }).update(b -> b.setChecked(user.admin))
                        .disabled(b -> net.client())
                        .touchable(() -> net.client() ? Touchable.disabled : Touchable.enabled)
                        .checked(user.admin);

                    t.button(Icon.zoom, ustyle, () -> Call.adminRequest(user, AdminAction.trace));

                }).padRight(12).size(bs + 10f, bs);
            }else if(!user.isLocal() && !user.admin && net.client() && Groups.player.size() >= 3 && player.team() == user.team()){ //votekick
                button.add().growY();

                button.button(Icon.hammer, ustyle,
                () -> {
                    ui.showConfirm("@confirm", Core.bundle.format("confirmvotekick",  user.name()), () -> {
                        Call.sendChatMessage("/votekick #" + user.id());
                    });
                }).size(h/2);
            }
            if (user != player) {
                button.button(Icon.lock, ustyle, // Mute player
                        () -> ClientUtils.toggleMutePlayer(user)).size(h / 2).tooltip("@client.mute");
                button.button(Icon.copy, ustyle, // Assist/copy
                        () -> Navigation.follow(new AssistPath(user,
                                Core.input.shift() ? AssistPath.Type.FreeMove :
                                Core.input.ctrl() ? AssistPath.Type.Cursor :
                                Core.input.alt() ? AssistPath.Type.BuildPath :
                                                    AssistPath.Type.Regular, Core.settings.getBool("circleassist"))
                        )).size(h / 2).tooltip("@client.assist");
                button.button(Icon.cancel, ustyle, // Unassist/block
                        () -> Navigation.follow(new UnAssistPath(user, !Core.input.shift()))).size(h / 2).tooltip("@client.unassist");
                button.button(Icon.move, ustyle, // Goto
                    () -> Navigation.navigateTo(user)).size(h / 2).tooltip("@client.goto");
                button.button(Icon.zoom, ustyle, // Spectate/stalk
                    () -> Spectate.INSTANCE.spectate(user, Core.input.shift())).tooltip("@client.spectate");
            }

            if (ClientUtils.io() && ClientVars.rank >= 4 || ClientUtils.phoenix() && ClientVars.rank >= 9) { // Apprentice+ on io, Colonel+ on phoenix
                button.button(new TextureRegionDrawable(StatusEffects.freezing.uiIcon), ustyle,
                    () -> ui.showConfirm("@confirm", Core.bundle.format("client.confirmfreeze", user.name()), () -> {
                        if (ClientVars.rank == 4 && ClientUtils.io()) Call.serverPacketReliable("freeze_by_id", String.valueOf(user.id));
                        else Call.sendChatMessage("/freeze " + user.id); // Freeze command preferred since it actually has a response (io doesn't let apprentice run it for some reason)
                    })
                ).tooltip("@client.freeze");
            }

            content.add(button).padBottom(-6).width(750).maxHeight(h + 14);
            content.row();
            content.image().height(4f).color(state.rules.pvp ? user.team().color : Pal.gray).growX();
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
        }
    }

    public boolean shown(){
        return visible;
    }

}
