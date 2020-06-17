package mindustry.ui.fragments;

import arc.*;
import arc.func.*;
import arc.graphics.g2d.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import mindustry.core.GameState.*;
import mindustry.entities.traits.BuilderTrait.*;
import mindustry.entities.type.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.net.*;
import mindustry.net.Packets.*;
import mindustry.ui.*;

import java.util.*;

import static mindustry.Vars.*;

public class PlayerListFragment extends Fragment{
    private boolean visible = false;
    private Table content = new Table().marginRight(13f).marginLeft(13f);
    private Interval timer = new Interval();
    private TextField sField;

    @Override
    public void build(Group parent){
        parent.fill(cont -> {
            cont.visible(() -> visible);
            cont.update(() -> {
                if(!(net.active() && !state.is(State.menu))){
                    visible = false;
                    return;
                }

                if(visible && timer.get(20)){
                    rebuild();
                    content.pack();
                    content.act(Core.graphics.getDeltaTime());
                    //TODO hack
                    Core.scene.act(0f);
                }
            });

            cont.table(Tex.buttonTrans, pane -> {
                pane.label(() -> Core.bundle.format(playerGroup.size() == 1 ? "players.single" : "players", playerGroup.size()));
                pane.row();
                sField = pane.addField(null, text -> {
                    rebuild();
                }).grow().pad(8).get();
                sField.setMaxLength(maxNameLength);
                sField.setMessageText(Core.bundle.format("players.search"));
                pane.row();
                pane.pane(content).grow().get().setScrollingDisabled(true, false);
                pane.row();

                pane.table(menu -> {
                    menu.defaults().growX().height(50f).fillY();

                    menu.addButton("$server.bans", ui.bans::show).disabled(b -> net.client());
                    menu.addButton("$server.admins", ui.admins::show).disabled(b -> net.client());
                    menu.addButton("$close", this::toggle);
                }).margin(0f).pad(10f).growX();

            }).touchable(Touchable.enabled).margin(14f);
        });

        rebuild();
    }

    public void rebuild(){
        content.clear();

        float h = 74f;

        playerGroup.all().sort(Structs.comparing(Unit::getTeam));
        playerGroup.all().each(user -> {
            NetConnection connection = user.con;

            if(connection == null && net.server() && !user.isLocal) return;
            if(sField.getText().length() > 0 && !user.name.toLowerCase().contains(sField.getText().toLowerCase()) && !Strings.stripColors(user.name.toLowerCase()).contains(sField.getText().toLowerCase())) return;

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
            table.add(new Image(user.getIconRegion()).setScaling(Scaling.none)).grow();

            button.add(table).size(h);
            button.labelWrap("[#" + user.color.toString().toUpperCase() + "]" + user.name).width(170f).pad(10);
            button.add().grow();

            button.addImage(Icon.admin).visible(() -> user.isAdmin && !(!user.isLocal && net.server())).padRight(5).get().updateVisibility();

            if((net.server() || player.isAdmin) && !user.isLocal && (!user.isAdmin || net.server())){
                button.add().growY();

                float bs = (h) / 2f;

                button.table(t -> {
                    t.defaults().size(bs);

                    t.addImageButton(Icon.hammer, Styles.clearPartiali,
                            () -> ui.showConfirm("$confirm", "$confirmban", () -> Call.onAdminRequest(user, AdminAction.ban)));
                    t.addImageButton(Icon.cancel, Styles.clearPartiali,
                            () -> ui.showConfirm("$confirm", "$confirmkick", () -> Call.onAdminRequest(user, AdminAction.kick)));

                    t.row();

                    t.addImageButton(Icon.admin, Styles.clearTogglePartiali, () -> {
                        if(net.client()) return;

                        String id = user.uuid;

                        if(netServer.admins.isAdmin(id, connection.address)){
                            ui.showConfirm("$confirm", "$confirmunadmin", () -> netServer.admins.unAdminPlayer(id));
                        }else{
                            ui.showConfirm("$confirm", "$confirmadmin", () -> netServer.admins.adminPlayer(id, user.usid));
                        }
                    })
                            .update(b -> b.setChecked(user.isAdmin))
                            .disabled(b -> net.client())
                            .touchable(() -> net.client() ? Touchable.disabled : Touchable.enabled)
                            .checked(user.isAdmin);

                    t.addImageButton(Icon.zoom, Styles.clearPartiali, () -> Call.onAdminRequest(user, AdminAction.trace));

                }).padRight(12).size(bs + 10f, bs);
            }else if(!user.isLocal && !user.isAdmin && net.client() && playerGroup.size() >= 3 && player.getTeam() == user.getTeam()){ //votekick
                button.add().growY();

                button.addImageButton(Icon.hammer, Styles.clearPartiali,
                        () -> ui.showConfirm("$confirm", "$confirmvotekick", () -> Call.sendChatMessage("/votekick " + user.name))).size(h);

            }
            button.addImageButton(Icon.zoom, Styles.clearPartiali,
            () -> stalking = user);

            button.addImageButton(Icon.copy, Styles.clearPartiali,
            () -> following = user);

            button.addImageButton(Icon.defense, Styles.clearPartiali,
            () -> breakingFollowing = !breakingFollowing);

            button.addImageButton(Icon.undo, Styles.clearPartiali,
            () -> ui.showTextInput("Undo", "Number of actions to undo", 3, "5", true, str ->
                {
                    try{
                        int num = Integer.parseInt(str);
                        int inc = 0;
                        for(int i = 0; i <= num; i += 1){
                            if(user.log.size == 0){
                                break;
                            }
                            player.buildQueue().addLast(user.log.pop().undoRequest());
                        }
//                        for(BuildLogItem req : user.log){
//                            inc += 1;
//                            player.buildQueue().addLast(req.undoRequest());
//                            if(inc >= num){
//                                break;
//                            }
//                        }
                    }catch(NumberFormatException ignored){ }
                }));

            content.add(button).padBottom(-6).width(350f).maxHeight(h + 14);
            content.row();
            content.addImage().height(4f).color(state.rules.pvp ? user.getTeam().color : Pal.gray).growX();
            content.row();
        });

        if(sField.getText().length() > 0 && !playerGroup.all().contains(user -> user.name.toLowerCase().contains(sField.getText().toLowerCase()))) {
            content.add(Core.bundle.format("players.notfound")).padBottom(6).width(350f).maxHeight(h + 14);
        }

        content.marginBottom(5);
    }

    public void toggle(){
        visible = !visible;
        if(visible){
            rebuild();
        }else{
            Core.scene.setKeyboardFocus(null);
            sField.clearText();
        }
    }

}
