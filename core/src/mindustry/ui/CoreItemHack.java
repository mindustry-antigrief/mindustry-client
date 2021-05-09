package mindustry.ui;

import arc.scene.ui.Label;
import arc.scene.ui.layout.Table;
import arc.scene.ui.layout.Cell;
import arc.struct.Seq;
import arc.util.Log;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Items;
import mindustry.content.UnitTypes;
import mindustry.core.UI;
import mindustry.game.Team;
import mindustry.game.Teams;
import mindustry.type.Item;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.modules.ItemModule;

import static mindustry.Vars.content;
import static mindustry.Vars.state;

//code from https://github.com/shugen002/Mindustry/blob/master/core/src/mindustry/ui/OtherCoreItemDisplay.java
public class CoreItemHack extends Table {
    private Seq<Teams.TeamData> teams = null;
    private Seq<CoreBlock.CoreBuild> teamcores = null;

    public CoreItemHack() {

        rebuild();
    }

    void rebuild() {
        clear();
        background(Styles.black6);
        update(() -> {
            if (teams != state.teams.getActive()) {
                rebuild();
                return;
            }
            int a = 0;
            for (Teams.TeamData team : teams) {
                if (team.hasCore()) {
                    if (a >= teamcores.size) {
                        rebuild();
                        return;
                    }
                    teamcores.set(a, team.core());
                    a++;
                }
            }
            if (a != teamcores.size) {
                rebuild();
                return;
            }

        });
        label(() -> "");
        teams = Vars.state.teams.getActive();
        for (Teams.TeamData team : teams) {
            if (team.hasCore()) {
                label(() -> "[#" + team.team.color + "]" + team.team.localized());
            }
        }
        row();
        image(Blocks.coreNucleus.icon(Cicon.small));
        for (Teams.TeamData team : teams) {
            if (team.hasCore()) {
                label(() -> {
                    return UI.formatAmount(team.cores.size);
                }).padRight(1);
            }
        }
        row();
        image(UnitTypes.gamma.icon(Cicon.small));
        for (Teams.TeamData team : teams) {
            if (team.hasCore()) {
                label(() -> {
                    return UI.formatAmount(team.units.size);
                }).padRight(1);
            }
        }
        row();
        teamcores = new Seq<CoreBlock.CoreBuild>();
        for (Teams.TeamData team : teams) {
            if (team.hasCore()) {
                teamcores.add(team.core());
            }
        }

        for (Item item : getOrder()) {
            image(item.icon(Cicon.small)).padRight(3).left();
            for (int i = 0; i < teamcores.size; i++) {
                int finalI = i;
                label(() -> {
                    int num = 0;
                    try {
                        num = teamcores.get(finalI).items.get(item);
                    }catch (Exception e){
                        Log.err(e);
                    }
                    finally {
                        return num + "";
                    }
                }).get().setFontScale(0.8f);
            }
            row();
        }
        ;
    }


    private Seq<Item> getOrder() {
        return new Seq<Item>(new Item[]{
                Items.copper,
                Items.lead,
                Items.titanium,
                Items.thorium,
                Items.graphite,
                Items.silicon,
                Items.metaglass,
                Items.plastanium,
                Items.phaseFabric,
                Items.surgeAlloy,
                Items.coal,
                Items.sand,
                Items.scrap,
                Items.sporePod,
                Items.pyratite,
                Items.blastCompound
        });
    }

    public void updateTeamList() {
        teams = null;
    }
}