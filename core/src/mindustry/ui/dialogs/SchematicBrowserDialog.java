package mindustry.ui.dialogs;

import arc.Core;
import arc.files.Fi;
import arc.files.ZipFi;
import arc.graphics.Color;
import arc.graphics.Texture;
import arc.scene.ui.*;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Http;
import arc.util.Log;
import arc.util.Scaling;
import kotlin.Unit;
import mindustry.client.Main;
import mindustry.client.communication.SchematicTransmission;
import mindustry.client.navigation.clientThread;
import mindustry.game.Schematic;
import mindustry.game.Schematics;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.graphics.Pal;
import mindustry.ui.Styles;

import java.util.HashMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static mindustry.Vars.*;

// Inspired by eeve-lyn's schematic-browser mod
// hhh, I hate coding ui.
public class SchematicBrowserDialog extends BaseDialog {
    private static final float tagh = 42f;
    private final SchematicRepositoriesDialog repositoriesDialog = new SchematicRepositoriesDialog();
    public final Seq<String> links = new Seq<>();
    public final HashMap<String, Seq<Schematic>> browserSchematics = new HashMap<>(); // FINISHME: Optimize loading large repositories with 1000+ schematics
    private Schematic firstSchematic;
    private String search = "";
    private TextField searchField;
    private Runnable rebuildPane = () -> {}, rebuildTags = () -> {};
    private Pattern ignoreSymbols = Pattern.compile("[`~!@#$%^&*()\\-_=+{}|;:'\",<.>/?]");

    public SchematicBrowserDialog(){
        super("@schematic.browser");
        Core.assets.load("sprites/schematic-background.png", Texture.class).loaded = t -> t.setWrap(Texture.TextureWrap.repeat);

        shouldPause = true;
        addCloseButton();
        buttons.button("@schematic.browser.repo", Icon.host, this.repositoriesDialog::show);
        buttons.button("@schematic", Icon.copy, this::hideBrowser);
        makeButtonOverlay();
        readRepositories();
        shown(this::setup);
        onResize(this::setup);
    }

    void setup(){
        read();
        search = "";

        cont.top();
        cont.clear();

        cont.table(s -> {
            s.left();
            s.image(Icon.zoom);
            searchField = s.field(search, res -> {
                search = res;
                rebuildPane.run();
            }).growX().get();
            searchField.setMessageText("@schematic.search");
        }).fillX().padBottom(4);
        cont.row();

        cont.pane(t -> {
            t.top();
            rebuildPane = () -> {
                t.clear();
                firstSchematic = null;
                for (String link : browserSchematics.keySet()) {
                    setupRepoUi(t, ignoreSymbols.matcher(search.toLowerCase()).replaceAll(""), link);
                }
            };
            rebuildPane.run();
        }).grow().scrollX(false);
    }

    void setupRepoUi(Table table, String searchString, String repo){
        int cols = Math.max((int)(Core.graphics.getWidth() / Scl.scl(230)), 1);

        table.add(repo).center().color(Pal.accent);
        table.row();
        table.image().growX().padTop(10).height(3).color(Pal.accent).center();
        table.row();
        table.table(t -> {
            int i = 0;
            final int max = Core.settings.getInt("maxschematicslisted");
            for(Schematic s : browserSchematics.get(repo)){
                if(max != 0 && i > max) break; // Avoid meltdown on large repositories

                if(!search.isEmpty() && !(ignoreSymbols.matcher(s.name().toLowerCase()).replaceAll("").contains(searchString)
                        || (Core.settings.getBool("schematicsearchdesc") && ignoreSymbols.matcher(s.description().toLowerCase()).replaceAll("").contains(searchString)))
                ) continue;
                if(firstSchematic == null) firstSchematic = s;

                Button[] sel = {null};
                sel[0] = t.button(b -> {
                    b.top();
                    b.margin(0f);
                    b.table(buttons -> {
                        buttons.center();
                        buttons.defaults().size(50f);

                        ImageButton.ImageButtonStyle style = Styles.emptyi;

                        buttons.button(Icon.info, style, () -> showInfo(s)).tooltip("@info.title");
                        buttons.button(Icon.upload, style, () -> showExport(s)).tooltip("@editor.export");
                        buttons.button(Icon.download, style, () -> {
                            ui.showInfoFade("@schematic.saved");
                            schematics.add(s);
                        }).tooltip("@schematic.browser.download");
                    }).growX().height(50f);
                    b.row();
                    b.stack(new SchematicsDialog.SchematicImage(s).setScaling(Scaling.fit), new Table(n -> {
                        n.top();
                        n.table(Styles.black3, c -> {
                            Label label = c.add(s.name()).style(Styles.outlineLabel).top().growX().maxWidth(200f - 8f)
                                    .update(l -> l.setText((!player.team().rules().infiniteResources && !state.rules.infiniteResources && player.core() != null && !player.core().items.has(s.requirements()) ? "[#dd5656]" : "") + s.name())).get();
                            label.setEllipsis(true);
                            label.setAlignment(Align.center);
                        }).growX().margin(1).pad(4).maxWidth(Scl.scl(200f - 8f)).padBottom(0);
                    })).size(200f);
                }, () -> {
                    if(sel[0].childrenPressed()) return;
                    if(state.isMenu()){
                        showInfo(s);
                    }else{
                        if(!(state.rules.schematicsAllowed || Core.settings.getBool("forceallowschematics"))){
                            ui.showInfo("@schematic.disabled");
                        }else{
                            control.input.useSchematic(s);
                            hide();
                        }
                    }
                }).pad(4).style(Styles.flati).get();

                sel[0].getStyle().up = Tex.pane;

                if(++i % cols == 0){
                    t.row();
                }
            }

            if(i==0){
                if(!searchString.isEmpty()){
                    t.add("@none.found");
                }else{
                    t.add("@none").color(Color.lightGray);
                }
            }
        });
        table.row();
    }

    public void showInfo(Schematic schematic){
        ui.schematics.info.show(schematic);
    }

    public void showExport(Schematic s){
        BaseDialog dialog = new BaseDialog("@editor.export");
        dialog.cont.pane(p -> {
            p.margin(10f);
            p.table(Tex.button, t -> {
                TextButton.TextButtonStyle style = Styles.flatt;
                t.defaults().size(280f, 60f).left();
                if(steam && !s.hasSteamID()){
                    t.button("@schematic.shareworkshop", Icon.book, style,
                            () -> platform.publish(s)).marginLeft(12f);
                    t.row();
                    dialog.hide();
                }
                t.button("@schematic.copy", Icon.copy, style, () -> {
                    dialog.hide();
                    ui.showInfoFade("@copied");
                    Core.app.setClipboardText(schematics.writeBase64(s));
                }).marginLeft(12f);
                t.row();
                t.button("@schematic.exportfile", Icon.export, style, () -> {
                    dialog.hide();
                    platform.export(s.name(), schematicExtension, file -> Schematics.write(s, file));
                }).marginLeft(12f);
                t.row();
                t.button("@schematic.chatshare", Icon.bookOpen, style, () -> {
                    if (!state.isPlaying()) return;
                    dialog.hide();
                    clientThread.post(() -> {
                        Main.INSTANCE.send(new SchematicTransmission(s), () -> {
                            Core.app.post(() -> {
                                ui.showInfoToast(Core.bundle.get("client.finisheduploading"), 2f);
                            });
                            return Unit.INSTANCE;
                        });
                    });
                }).marginLeft(12f).get().setDisabled(() -> !state.isPlaying());
            });
        });

        dialog.addCloseButton();
        dialog.show();
    }

    void hideBrowser(){
        ui.schematics.show();
        this.hide();
    }

    void readRepositories(){
        links.clear();
        String setting = Core.settings.getString("schematicrepositories","");
        if (setting.isEmpty()) return;
        links.add(setting.split(";"));
    }

    void read(){
        for (String link : links) {
            String fileName = link.replace("/","") + ".zip";
            Fi filePath = schematicRepoDirectory.child(fileName);
            if (!filePath.exists()) return;
            final Seq<Schematic> schems = browserSchematics.get(link) != null ? browserSchematics.get(link) : new Seq<>();
            new ZipFi(filePath).walk(f -> {
                try {
                    if (f.extEquals("msch")) schems.add(Schematics.read(f));
                } catch (Throwable e) {
                    Log.err("Error parsing schematic repository " + link + ".", e);
                }
            });
            browserSchematics.put(link, schems);
        }
    }

    void fetch(){
        for (String link : links){
            Http.get(ghApi + "/repos/" + link + "/zipball/main", res -> handleRedirect(link, res),
                    e -> Log.info("Schematic repository " + link + " could not be reached. " + e));
        }
    }

    void handleRedirect(String link, Http.HttpResponse res){
        if (res.getHeader("Location") != null) {
            Http.get(res.getHeader("Location"), r -> handleRepo(link, r),
                    e -> Log.info("Schematic repository " + link + " could not be reached. " + e));
        } else handleRepo(link, res);
    }

    void handleRepo(String link, Http.HttpResponse res){
        String fileName = link.replace("/","") + ".zip";
        Fi filePath = schematicRepoDirectory.child(fileName);
        filePath.writeBytes(res.getResult());
        final Seq<Schematic> schems = browserSchematics.get(link) != null ? browserSchematics.get(link) : new Seq<>();
        new ZipFi(filePath).walk(f -> {
            try {
                if (f.extEquals("msch")) schems.add(Schematics.read(f));
            } catch (Throwable e) {
                Log.err("Error parsing schematic repository " + link + ".", e);
            }
        });
        browserSchematics.put(link, schems);
    }

    @Override
    public Dialog show() {
        super.show();

        if (Core.app.isDesktop() && searchField != null) {
            Core.scene.setKeyboardFocus(searchField);
        }

        return this;
    }

    protected class SchematicRepositoriesDialog extends BaseDialog {
        private String search = "";
        private TextField searchField;
        public Table repos = new Table();
        private final String linkRegex = "(https?://)?github\\.com/?";

        private boolean changed = false;

        public SchematicRepositoriesDialog(){
            super("@schematic.browser.repo");

            buttons.defaults().size(width, 64f);
            buttons.button("@back", Icon.left, this::close).size(width, 64f);
            buttons.button("@schematic.browser.add", () -> editLink("", l -> ui.schematicsBrowser.links.add(l)));
            makeButtonOverlay();
            addCloseListener();
            shown(this::setup);
            onResize(this::setup);
        }

        void setup(){
            cont.top();
            cont.clear();

            cont.table(s -> {
                s.left();
                s.image(Icon.zoom);
                searchField = s.field(search, res -> {
                    search = res;
                    rebuild();
                }).width(350).pad(5).get();
                searchField.setMessageText("@schematic.browser.search");
            }).fillX().padBottom(4);

            cont.row();

            rebuild();
            cont.pane( t -> {
               t.defaults().pad(5f);
               t.pane ( p -> p.add(repos)).growX();
               t.row();
            });
        }

        void rebuild(){
            repos.clear();
            repos.defaults().width(450).pad(5f).left();
            for (var i = 0; i < ui.schematicsBrowser.links.size; i++) {
                String link = ui.schematicsBrowser.links.get(i);
                Table table = new Table();
                table.button(Icon.cancel, Styles.settingTogglei, 16f, () -> {
                    ui.schematicsBrowser.links.remove(link);
                    changed = true;
                    rebuild();
                }).padRight(20f).tooltip("@save.delete");
                int finalI = i;
                table.button(Icon.edit, Styles.settingTogglei, 16f, () -> {
                    editLink(link, l -> ui.schematicsBrowser.links.set(finalI, l));
                }).padRight(10f).tooltip("@schematic.browser.edit");
                table.labelWrap(link).right();
                repos.add(table);
                repos.row();
            }
        }

        void editLink(String link, Consumer<String> onClose){
            BaseDialog dialog = new BaseDialog("@schematic.browser.edit");
            TextField linkInput = new TextField(link);
            linkInput.setValidator( l -> !l.isEmpty());
            dialog.cont.add(linkInput).width(400f);
            dialog.cont.row();
            dialog.closeOnBack(() -> {
                String text = linkInput.getText().replaceAll(linkRegex, "").toLowerCase();
                onClose.accept(text);
                changed = true;
                rebuild();
            });
            dialog.show();
        }

        void close(){
            if (changed) {
                ui.schematicsBrowser.fetch();
                Core.settings.put("schematicrepositories", ui.schematicsBrowser.links.toString(";"));
            }
            ui.schematicsBrowser.show();
            this.hide();
        }
    }
}
