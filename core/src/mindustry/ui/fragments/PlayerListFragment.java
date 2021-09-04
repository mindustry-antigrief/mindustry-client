package mindustry.ui.fragments;

import arc.*;
import arc.graphics.g2d.*;
import arc.input.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.client.*;
import mindustry.client.navigation.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.net.*;
import mindustry.net.Packets.*;
import mindustry.ui.*;

import static mindustry.Vars.*;

public class PlayerListFragment extends Fragment{
    public Table content = new Table().marginRight(13f).marginLeft(13f);
    private boolean visible = false;
    private final Interval timer = new Interval();
    private TextField search;
    private final Seq<Player> players = new Seq<>();

    @Override
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

                if(visible && timer.get(5) && !Core.input.keyDown(KeyCode.mouseLeft)){
                    rebuild();
                    content.pack();
                    content.act(Core.graphics.getDeltaTime());
                    //hacky
                    Core.scene.act(0f);
                }
            });

            cont.table(Tex.buttonTrans, pane -> {
                pane.label(() -> Core.bundle.format("players" + (Groups.player.size() == 1 && (ui.join.lastHost == null || ui.join.lastHost.playerLimit <= 0) ? ".single" : ""), Groups.player.size() + (ui.join.lastHost != null && ui.join.lastHost.playerLimit > 0 ? " / " + ui.join.lastHost.playerLimit : "")));
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

            }).touchable(Touchable.enabled).margin(14f).minWidth(360f);
        });

        rebuild();
    }

    public void rebuild(){
        content.clear();

        float h = 74f;
        boolean found = false;

        players.clear();
        Groups.player.copy(players);

        players.sort(Structs.comps(Structs.comparing(Player::team), Structs.comps(Structs.comparingBool(p -> !p.admin), Structs.comparingBool(p -> !(p.fooUser || p.isLocal())))));
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
            ).wrap().width(400).growY().pad(10).get().add().grow();

            if (user.admin && !(!user.isLocal() && net.server())) button.image(Icon.admin).padRight(7.5f);
            if (user.fooUser || (user.isLocal() && Core.settings.getBool("displayasuser"))) button.image(Icon.wrench).padRight(7.5f);

            if((net.server() || player.admin) && !user.isLocal() && (!user.admin || net.server())){
                button.add().growY();

                float bs = (h) / 2f;

                button.table(t -> {
                    t.defaults().size(bs);

                    t.button(Icon.hammer, Styles.clearPartiali,
                    () -> ui.showConfirm("@confirm", Core.bundle.format("confirmban",  user.name()), () -> Call.adminRequest(user, AdminAction.ban)));
                    t.button(Icon.cancel, Styles.clearPartiali,
                    () -> ui.showConfirm("@confirm", Core.bundle.format("confirmkick",  user.name()), () -> Call.adminRequest(user, AdminAction.kick)));

                    t.row();

                    t.button(Icon.admin, Styles.clearTogglePartiali, () -> {
                        if(net.client()) return;

                        String id = user.uuid();

                        if(netServer.admins.isAdmin(id, connection.address)){
                            ui.showConfirm("@confirm", Core.bundle.format("confirmunadmin",  user.name()), () -> netServer.admins.unAdminPlayer(id));
                        }else{
                            ui.showConfirm("@confirm", Core.bundle.format("confirmadmin",  user.name()), () -> netServer.admins.adminPlayer(id, user.usid()));
                        }
                    }).update(b -> b.setChecked(user.admin))
                        .disabled(b -> net.client())
                        .touchable(() -> net.client() ? Touchable.disabled : Touchable.enabled)
                        .checked(user.admin);

                    t.button(Icon.zoom, Styles.clearPartiali, () -> {
                        Call.adminRequest(user, AdminAction.trace);
                    });

                }).padRight(12).size(bs + 10f, bs);
            }else if(!user.isLocal() && !user.admin && net.client() && Groups.player.size() >= 3 && player.team() == user.team()){ //votekick
                button.add().growY();

                button.button(Icon.hammer, Styles.clearPartiali,
                () -> {
                    ui.showConfirm("@confirm", Core.bundle.format("confirmvotekick",  user.name()), () -> {
                        Call.sendChatMessage("/votekick " + user.name());
                    });
                }).size(h/2);
            }
            if (user != player) {
                button.button(Icon.copy, Styles.clearPartiali, // Assist/copy
                        () -> Navigation.follow(new AssistPath(user))).size(h / 2);
                button.button(Icon.cancel, Styles.clearPartiali, // Unassist/block
                        () -> Navigation.follow(new UnAssistPath(user))).size(h / 2);
                button.button(Icon.move, Styles.clearPartiali, // Goto
                        () -> Navigation.navigateTo(user)).size(h / 2);
                button.button(Icon.zoom, Styles.clearPartiali, // Spectate/stalk
                        () -> Spectate.INSTANCE.spectate(user));
            }

            content.add(button).padBottom(-6).width(700).maxHeight(h + 14);
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
