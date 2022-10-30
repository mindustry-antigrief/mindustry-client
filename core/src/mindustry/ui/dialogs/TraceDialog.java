package mindustry.ui.dialogs;

import arc.*;
import arc.scene.ui.layout.*;
import mindustry.gen.*;
import mindustry.net.*;
import mindustry.net.Administration.*;
import mindustry.ui.*;

public class TraceDialog extends BaseDialog{

    public TraceDialog(){
        super("@trace");

        setFillParent(false);
    }

    public void show(Player player, TraceInfo info){
        show(player, info, false);
    }

    public void show(Player player, TraceInfo info, boolean offline){
        cont.clear();
        buttons.clear();
        addCloseButton();

        buttons.button("JS Ban (Requires /js)", () -> {
            Call.sendChatMessage("/js Vars.netServer.admins.banPlayerID(" + info.uuid + ")");
            Call.sendChatMessage("/js Vars.netServer.admins.banPlayerIP(" + info.ip + ")");
        }).width(420);
        if(!offline){
            buttons.button("Ban (Won't work if they leave before pressed)", () -> Call.adminRequest(player, Packets.AdminAction.ban)).width(420);
        }

        Table table = new Table(Tex.clear);
        table.margin(14);
        table.defaults().pad(1);

        table.defaults().left().expandX();
        table.button(Core.bundle.format("trace.playername", player.name), Styles.nonetdef, () -> Core.app.setClipboardText(player.name)).wrapLabel(false);
        table.row();
        table.button(Core.bundle.format("trace.ip", info.ip), Styles.nonetdef, () -> Core.app.setClipboardText(info.ip)).wrapLabel(false);
        table.row();
        table.button(Core.bundle.format("trace.id", info.uuid), Styles.nonetdef, () -> Core.app.setClipboardText(info.uuid)).wrapLabel(false);
        table.row();
        table.add(Core.bundle.format("trace.modclient", info.modded));
        table.row();
        table.add(Core.bundle.format("trace.mobile", info.mobile));
        table.row();
        table.add(Core.bundle.format("trace.times.joined", info.timesJoined));
        table.row();
        table.add(Core.bundle.format("trace.times.kicked", info.timesKicked));
        table.row();

        table.add().pad(5);
        table.row();

        cont.add(table);

        show();
    }
}
