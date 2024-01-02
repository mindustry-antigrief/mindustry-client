package mindustry.ui.dialogs;

import arc.*;
import arc.files.*;
import arc.graphics.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import kotlin.Unit;
import mindustry.client.*;
import mindustry.client.communication.*;
import mindustry.client.navigation.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.ui.*;

import java.util.function.*;
import java.util.regex.*;

import static mindustry.Vars.*;

// Inspired by eeve-lyn's schematic-browser mod
// hhh, I hate coding ui.
public class SchematicBrowserDialog extends BaseDialog {
    private static final float tagh = 42f;
    private final SchematicRepositoriesDialog repositoriesDialog = new SchematicRepositoriesDialog();
    public final Seq<String> repositoryLinks = new Seq<>(), hiddenRepositories = new Seq<>(), unloadedRepositories = new Seq<>(), unfetchedRepositories = new Seq<>();
    public final ObjectMap<String, Seq<Schematic>> loadedRepositories = new ObjectMap<>(); // FINISHME: Optimize loading large repositories with 1000+ schematics
    private Schematic firstSchematic;
    private String search = "";
    private TextField searchField;
    private Runnable rebuildPane = () -> {}, rebuildTags = () -> {};
    private final Pattern ignoreSymbols = Pattern.compile("[`~!@#$%^&*()\\-_=+{}|;:'\",<.>/?]");
    private final Seq<String> tags = new Seq<>(), selectedTags = new Seq<>();

    public SchematicBrowserDialog(){
        super("@schematic.browser");
        Core.assets.load("sprites/schematic-background.png", Texture.class).loaded = t -> t.setWrap(Texture.TextureWrap.repeat);

        shouldPause = true;
        addCloseButton();
        buttons.button("@schematic", Icon.copy, this::hideBrowser);
        buttons.button("@schematic.browser.repo", Icon.host, this.repositoriesDialog::show);
        buttons.button("@schematic.browser.fetch", Icon.refresh, () -> fetch(loadedRepositories.keys().toSeq()));
        makeButtonOverlay();

        getSettings();
        unloadedRepositories.addAll(repositoryLinks);
        loadRepositories();

        shown(this::setup);
        onResize(this::setup);
    }

    void setup(){
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

        cont.table(in -> {
            in.left();
            in.add("@schematic.tags").padRight(4);

            //tags (no scroll pane visible)
            in.pane(Styles.noBarPane, t -> {
                rebuildTags = () -> {
                    t.clearChildren();
                    t.left();

                    t.defaults().pad(2).height(tagh);
                    for(var tag : tags){
                        t.button(tag, Styles.togglet, () -> {
                            if(selectedTags.contains(tag)){
                                selectedTags.remove(tag);
                            }else{
                                selectedTags.add(tag);
                            }
                            rebuildPane.run();
                        }).checked(selectedTags.contains(tag)).with(c -> c.getLabel().setWrap(false));
                    }
                };
                rebuildTags.run();
            }).fillX().height(tagh).scrollY(false);

            in.button(Icon.pencilSmall, this::showAllTags).size(tagh).pad(2).tooltip("@schematic.edittags");
        }).height(tagh).fillX();
        cont.row();

        cont.pane(t -> {
            t.top();
            rebuildPane = () -> {
                t.clear();
                firstSchematic = null;
                for (String repo : loadedRepositories.keys()) {
                    if (hiddenRepositories.contains(repo)) continue;
                    setupRepoUi(t, ignoreSymbols.matcher(search.toLowerCase()).replaceAll(""), repo);
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
            for(Schematic s : loadedRepositories.get(repo)){
                if(max != 0 && i > max) break; // Avoid meltdown on large repositories

                if(selectedTags.any() && !s.labels.containsAll(selectedTags)) continue;  // Tags
                if(!search.isEmpty() && !(ignoreSymbols.matcher(s.name().toLowerCase()).replaceAll("").contains(searchString)
                        || (Core.settings.getBool("schematicsearchdesc") && ignoreSymbols.matcher(s.description().toLowerCase()).replaceAll("").contains(searchString)))
                ) continue; // Search
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
                            ui.schematics.checkTags(s);
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
                if(!searchString.isEmpty() || selectedTags.any()){
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
                    clientThread.post(() -> Main.INSTANCE.send(new SchematicTransmission(s), () -> {
                        Core.app.post(() -> ui.showInfoToast(Core.bundle.get("client.finisheduploading"), 2f));
                        return Unit.INSTANCE;
                    }));
                }).marginLeft(12f).get().setDisabled(() -> !state.isPlaying());
            });
        });

        dialog.addCloseButton();
        dialog.show();
    }

    void checkTags(Schematic s){
        for(var tag : s.labels){
            if(!tags.contains(tag)){
                tags.add(tag);
            }
        }
    }

    void rebuildAll(){
        tags.clear();
        selectedTags.clear();
        for (var repo : loadedRepositories.keys()){
            if (hiddenRepositories.contains(repo)) continue;
            for (Schematic s : loadedRepositories.get(repo)) {
                checkTags(s);
            }
        }
        rebuildTags.run();
        rebuildPane.run();
    }

    void tagsChanged(){
        rebuildTags.run();
        if(selectedTags.any()){
            rebuildPane.run();
        }
    }

    void showAllTags(){
        var dialog = new BaseDialog("@schematic.edittags");
        dialog.addCloseButton();
        Runnable[] rebuild = {null};
        dialog.cont.pane(p -> {
            rebuild[0] = () -> {
                p.clearChildren();
                p.margin(12f).defaults().fillX().left();

                float sum = 0f;
                Table current = new Table().left();

                for(var tag : tags){

                    var next = new Table(n -> {
                        n.table(Tex.pane, move -> {
                            move.margin(2);

                            //move up
                            move.button(Icon.upOpen, Styles.emptyi, () -> {
                                int idx = tags.indexOf(tag);
                                if(idx > 0){
                                    if(Core.input.shift()){
                                        tags.insert(0, tags.remove(idx));
                                    } else {
                                        tags.swap(idx, idx - 1);
                                    }
                                    tagsChanged();
                                    rebuild[0].run();
                                }
                            }).tooltip("@editor.moveup").row();
                            //move down
                            move.button(Icon.downOpen, Styles.emptyi, () -> {
                                int idx = tags.indexOf(tag);
                                if(idx < tags.size - 1){
                                    if(Core.input.shift()){
                                        tags.insert(tags.size - 1, tags.remove(idx));
                                    } else {
                                        tags.swap(idx, idx + 1);
                                    }
                                    tagsChanged();
                                    rebuild[0].run();
                                }
                            }).tooltip("@editor.movedown");
                        }).fillY().margin(6f);

                        n.table(Tex.whiteui, t -> {
                            t.setColor(Pal.gray);
                            t.add(tag).left().row();
                            t.add(Core.bundle.format("schematic.tagged", schematics.all().count(s -> s.labels.contains(tag)))).left()
                                    .update(b -> b.setColor(b.hasMouse() ? Pal.accent : Color.lightGray)).get().clicked(() -> {
                                        dialog.hide();
                                        selectedTags.clear().add(tag);
                                        rebuildTags.run();
                                        rebuildPane.run();
                                    });
                        }).growX().fillY().margin(8f);

                        n.table(Tex.pane, b -> {
                            b.margin(2);

                            //rename tag
                            b.button(Icon.pencil, Styles.emptyi, () -> {
                                ui.showTextInput("@schematic.renametag", "@name", tag, result -> {
                                    //same tag, nothing was renamed
                                    if(result.equals(tag)) return;

                                    if(tags.contains(result)){
                                        ui.showInfo("@schematic.tagexists");
                                    }else{
                                        for(Schematic s : schematics.all()){
                                            if(s.labels.any()){
                                                s.labels.replace(tag, result);
                                                s.save();
                                            }
                                        }
                                        selectedTags.replace(tag, result);
                                        tags.replace(tag, result);
                                        tagsChanged();
                                        rebuild[0].run();
                                    }
                                });
                            }).tooltip("@schematic.renametag").row();
                            //delete tag
                            b.button(Icon.trash, Styles.emptyi, () -> {
                                ui.showConfirm("@schematic.tagdelconfirm", () -> {
                                    for(Schematic s : schematics.all()){
                                        if(s.labels.any()){
                                            s.labels.remove(tag);
                                            s.save();
                                        }
                                    }
                                    selectedTags.remove(tag);
                                    tags.remove(tag);
                                    tagsChanged();
                                    rebuildPane.run();
                                    rebuild[0].run();
                                });
                            }).tooltip("@save.delete");
                        }).fillY().margin(6f);
                    });

                    next.pack();
                    float w = next.getPrefWidth() + Scl.scl(6f);

                    if(w + sum >= Core.graphics.getWidth() * (Core.graphics.isPortrait() ? 1f : 0.8f)){
                        p.add(current).row();
                        current = new Table();
                        current.left();
                        current.add(next).minWidth(240).pad(4);
                        sum = 0;
                    }else{
                        current.add(next).minWidth(240).pad(4);
                    }

                    sum += w;
                }

                if(sum > 0){
                    p.add(current).row();
                }
            };

            resized(true, rebuild[0]);
        }).scrollX(false);
        dialog.show();
    }

    void hideBrowser(){
        ui.schematics.show();
        this.hide();
    }

    void getSettings(){
        repositoryLinks.clear();
        repositoryLinks.add(Core.settings.getString("schematicrepositories","MindustryDesignIt/main").split(";"));

        if (Core.settings.getString("hiddenschematicrepositories", "").isEmpty()) return;
        hiddenRepositories.clear();
        hiddenRepositories.addAll(Core.settings.getString("hiddenschematicrepositories").split(";"));
    }

    void loadRepositories(){
        for (String link : unloadedRepositories) {
            if (hiddenRepositories.contains(link)) continue; // Skip loading
            String fileName = link.replace("/","") + ".zip";
            Fi filePath = schematicRepoDirectory.child(fileName);
            if (!filePath.exists()) return;
            final Seq<Schematic> schems = new Seq<>();
            new ZipFi(filePath).walk(f -> {
                try {
                    if (f.extEquals("msch")) {
                        Schematic s = Schematics.read(f);
                        schems.add(s);
                        if (!hiddenRepositories.contains(link)) checkTags(s);
                    }
                } catch (Throwable e) {
                    Log.err("Error parsing schematic " + link + " " + f.name(), e);
                    ui.showErrorMessage(Core.bundle.format("schematic.browser.fail.parse", link, f.name()));
                }
            });
            if (loadedRepositories.get(link) != null) {
                loadedRepositories.get(link).clear();
                loadedRepositories.get(link).add(schems);
            } else {
                loadedRepositories.put(link, schems);
            }
        }
        unloadedRepositories.clear();
    }

    void fetch(Seq<String> repos){
        ui.showInfoFade("@schematic.browser.fetching", 2f);
        for (String link : repos){
            Http.get(ghApi + "/repos/" + link + "/zipball/main", res -> handleRedirect(link, res), e -> Core.app.post(() -> {
                Log.info("Schematic repository " + link + " could not be reached. " + e);
                ui.showErrorMessage(Core.bundle.format("schematic.browser.fail.fetch", link));
            }));
        }
    }

    void handleRedirect(String link, Http.HttpResponse res){
        if (res.getHeader("Location") != null) {
            Http.get(res.getHeader("Location"), r -> handleRepo(link, r), e -> Core.app.post(() -> {
                Log.info("Schematic repository " + link + " could not be reached. " + e);
                ui.showErrorMessage(Core.bundle.format("schematic.browser.fail.fetch", link));
            }));
        } else handleRepo(link, res);
    }

    void handleRepo(String link, Http.HttpResponse res){
        String fileName = link.replace("/","") + ".zip";
        Fi filePath = schematicRepoDirectory.child(fileName);
        filePath.writeBytes(res.getResult());
        Core.app.post(() ->{
            unfetchedRepositories.remove(link);
            unloadedRepositories.add(link);
            ui.showInfoFade(Core.bundle.format("schematic.browser.fetched", link), 2f);

            if (unfetchedRepositories.size == 0) {
                loadRepositories();
                rebuildAll();
            }
        });
    }

    @Override
    public Dialog show() {
        super.show();

        if (Core.app.isDesktop() && searchField != null) {
            Core.scene.setKeyboardFocus(searchField);
        }

        return this;
    }

    protected static class SchematicRepositoriesDialog extends BaseDialog {
        public Table repoTable = new Table();
        private final Pattern pattern = Pattern.compile("(https?://)?github\\.com/");
        private boolean refetch = false;
        private boolean rebuild = false;

        public SchematicRepositoriesDialog(){
            super("@schematic.browser.repo");

            buttons.defaults().size(width, 64f);
            buttons.button("@back", Icon.left, this::close);
            buttons.button("@schematic.browser.add", Icon.add, this::addRepo);
            makeButtonOverlay();
            addCloseListener();
            shown(this::setup);
            onResize(this::setup);
        }

        void setup(){
            rebuild();
            cont.pane( t -> {
               t.defaults().pad(5f);
               t.pane ( p -> p.add(repoTable)).growX();
            });
        }

        void rebuild(){
            repoTable.clear();
            repoTable.defaults().pad(5f).left();
            for (var i = 0; i < ui.schematicBrowser.repositoryLinks.size; i++) {
                final String link = ui.schematicBrowser.repositoryLinks.get(i);
                Table table = new Table();
                table.button(Icon.cancel, Styles.settingTogglei, 16f, () -> {
                    ui.schematicBrowser.repositoryLinks.remove(link);
                    ui.schematicBrowser.loadedRepositories.remove(link);
                    ui.schematicBrowser.hiddenRepositories.remove(link);
                    ui.schematicBrowser.unfetchedRepositories.remove(link);
                    rebuild = true;
                    rebuild();
                }).padRight(20f).tooltip("@save.delete");
                int finalI = i;
                table.button(Icon.edit, Styles.settingTogglei, 16f, () -> editRepo(link, l -> {
                    ui.schematicBrowser.repositoryLinks.set(finalI, l);
                    ui.schematicBrowser.loadedRepositories.remove(link);
                    ui.schematicBrowser.hiddenRepositories.remove(link);
                    ui.schematicBrowser.unfetchedRepositories.add(l);
                    refetch = true;
                })).padRight(20f).tooltip("@schematic.browser.edit");
                table.button(ui.schematicBrowser.hiddenRepositories.contains(link) ? Icon.eyeOffSmall : Icon.eyeSmall, Styles.settingTogglei, 16f, () -> {
                    if (!ui.schematicBrowser.hiddenRepositories.contains(link)) { // hide, unload to save memory
                        ui.schematicBrowser.loadedRepositories.remove(link);
                        ui.schematicBrowser.hiddenRepositories.add(link);
                    } else { // unhide, fetch and load
                        ui.schematicBrowser.hiddenRepositories.remove(link);
                        ui.schematicBrowser.unloadedRepositories.add(link);
                        ui.schematicBrowser.unfetchedRepositories.add(link);
                        refetch = true;
                    }
                    rebuild = true;
                    rebuild();
                }).padRight(20f).tooltip("@schematic.browser.togglevisibility");
                table.add(new Label(link)).right();
                repoTable.add(table);
                repoTable.row();
            }
        }

        void editRepo(String link, Consumer<String> onClose){
            BaseDialog dialog = new BaseDialog("@schematic.browser.edit");
            TextField linkInput = new TextField(link);
            linkInput.setMessageText("author/repository");
            linkInput.setValidator( l -> !l.isEmpty());
            dialog.addCloseListener();
            dialog.cont.add(linkInput).width(400f);
            dialog.cont.row();
            dialog.cont.table(t -> {
                t.defaults().width(194f).pad(3f);
                t.button("@schematic.browser.add", () -> {
                    String text = pattern.matcher(linkInput.getText().toLowerCase()).replaceAll("");
                    if (!text.equalsIgnoreCase(link)) {
                        onClose.accept(text);
                    }
                    rebuild();
                    dialog.hide();
                });
                t.button("@close", dialog::hide);
            });
            dialog.show();
        }

        void addRepo(){
            editRepo("", l -> {
                ui.schematicBrowser.repositoryLinks.add(l);
                ui.schematicBrowser.unloadedRepositories.add(l);
                ui.schematicBrowser.unfetchedRepositories.add(l);
                refetch = true;
                rebuild = true;
            });
        }

        void close(){
            if (refetch) {
                ui.schematicBrowser.fetch(ui.schematicBrowser.unfetchedRepositories);
                refetch = false;
            }
            if (rebuild) {
                ui.schematicBrowser.loadRepositories();
                ui.schematicBrowser.rebuildAll();
                rebuild = false;
            }
            Core.settings.put("schematicrepositories", ui.schematicBrowser.repositoryLinks.toString(";"));
            Core.settings.put("hiddenschematicrepositories", ui.schematicBrowser.hiddenRepositories.toString(";"));
            this.hide();
        }
    }
}
