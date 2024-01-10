package mindustry.client.ui;

import arc.*;
import arc.files.*;
import arc.graphics.*;
import arc.graphics.gl.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.actions.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import arc.util.serialization.*;
import mindustry.client.*;
import mindustry.client.communication.*;
import mindustry.client.navigation.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;

import java.util.function.*;
import java.util.regex.*;

import static mindustry.Vars.*;

// Inspired by eeve-lyn's schematic-browser mod
// hhh, I hate coding ui.
public class SchematicBrowserDialog extends BaseDialog {
    private static final float tagh = 42f;
    private final SchematicRepositoriesDialog repositoriesDialog = new SchematicRepositoriesDialog();
    public final Seq<String> repositoryLinks = new Seq<>(), hiddenRepositories = new Seq<>(), unloadedRepositories = new Seq<>(), unfetchedRepositories = new Seq<>();
    public final ObjectMap<String, Seq<Schematic>> loadedRepositories = new ObjectMap<>();
    private Schematic firstSchematic;
    private String nameSearch = "", descSearch = "";
    private TextField nameSearchField, descSearchField;
    private Runnable rebuildPane = () -> {}, rebuildTags = () -> {};
    private final Pattern ignoreSymbols = Pattern.compile("[`~!@#$%^&*()\\-_=+{}|;:'\",<.>/?]");
    private final Seq<String> tags = new Seq<>(), selectedTags = new Seq<>();
    private final ItemSeq reusableItemSeq = new ItemSeq();

    public SchematicBrowserDialog(){
        super("@client.schematic.browser");

        shouldPause = true;
        addCloseButton();
        buttons.button("@schematics", Icon.copy, this::hideBrowser);
        buttons.button("@client.schematic.browser.repo", Icon.host, repositoriesDialog::show);
        buttons.button("@client.schematic.browser.fetch", Icon.refresh, () -> fetch(repositoryLinks));
        makeButtonOverlay();

        getSettings();
        unloadedRepositories.addAll(repositoryLinks);
        loadRepositories();

        shown(this::setup);
        onResize(this::setup);
    }

    @Override
    public void hide() {
        if(!isShown()) return;
        setOrigin(Align.center);
        setClip(false);
        setTransform(true);

        hide(Actions.sequence(Actions.fadeOut(0.4f, Interp.fade), Actions.run(() -> { // Nuke previews to save ram FINISHME: Nuke the schematics as well and reload them on dialog open. Ideally, we should do that across all threads similar to how we load saves
            var previews = Reflect.<OrderedMap<Schematic, FrameBuffer>>get(schematics, "previews");
            var removed = new Queue<FrameBuffer>();
            for (var schems : loadedRepositories.values()) {
                for (var schem : schems) {
                    var rem = previews.remove(schem);
                    if (rem != null) removed.add(rem);
                }
            }
            Core.app.post(() -> disposeBuffers(removed)); // Start removing next frame as the process above may already take a few ms on slow cpus or in large repositories
        })));
    }

    /** Disposes a list of FrameBuffers over the course of multiple frames to not cause lag. */
    void disposeBuffers(Queue<FrameBuffer> todo) {
        var start = Time.nanos();
        while (!todo.isEmpty()) {
            if (Time.millisSinceNanos(start) >= 5) {
                Log.debug("Couldn't finish disposing buffers in time, resuming next frame. @ remain", todo.size);
                Core.app.post(() -> disposeBuffers(todo));
                return;
            }
            todo.removeFirst().dispose();
        }
        Log.debug("Finished disposing of FrameBuffers");
    }

    void setup(){
        Time.mark();
        nameSearch = "";
        descSearch = "";

        cont.top();
        cont.clear();

        buildSearch();
        buildTags();
        buildResults();

        Log.info("Rebuilt Schematic Browser in @ms", Time.elapsed());
    }

    void buildSearch() {
        cont.table(t -> {
            t.table(s -> {
                s.setWidth(t.getWidth() / 2);
                s.left();
                s.image(Icon.zoom);
                nameSearchField = s.field(nameSearch, res -> {
                    nameSearch = res;
                    rebuildPane.run();
                }).growX().get();
                nameSearchField.setMessageText("@schematic.search");
            }).growX();
            t.table(s -> {
                s.setWidth(t.getWidth() / 2);
                s.left();
                s.image(Icon.edit);
                descSearchField = s.field(descSearch, res -> {
                    descSearch = res;
                    rebuildPane.run();
                }).growX().get();
                descSearchField.setMessageText("@schematic.searchdescription");
            }).growX().padLeft(4);
        }).fillX().padBottom(4);
        cont.row();
    }

    void buildTags() {
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
    }

    void buildResults() {
        Table[] t = {null}; // Peak java
        t[0] = new Table() {
            @Override
            public void setCullingArea(Rect cullingArea) {
                super.setCullingArea(cullingArea);
                t[0].getChildren().<Table>each(c -> c instanceof Table, c -> {
                    var area = t[0].getCullingArea();
                    c.getCullingArea().setSize(area.width, area.height) // Set the size (NOT scaled to child coordinates which it should be if either scale isn't 1)
                            .setPosition(c.parentToLocalCoordinates(area.getPosition(Tmp.v1))); // Set the position (scaled correctly)
                });
            }
        };
        t[0].top();
        rebuildPane = () -> {
            t[0].clear();
            firstSchematic = null;
            String filteredNameSearch = ignoreSymbols.matcher(nameSearch.toLowerCase()).replaceAll("");
            String filteredDescSearch = ignoreSymbols.matcher(descSearch.toLowerCase()).replaceAll("");
            for (String repo : loadedRepositories.keys().toSeq().sort()) {
                if (hiddenRepositories.contains(repo)) continue;
                buildRepo(t[0], repo, filteredNameSearch, filteredDescSearch);
            }
        };
        rebuildPane.run();
        cont.pane(t[0]).grow().scrollX(false);
    }

    void buildRepo(Table table, String repo, String nameSearchString, String descSearchString){
        int cols = Math.max((int)(Core.graphics.getWidth() / Scl.scl(230)), 1);

        table.add(repo).center().color(Pal.accent);
        table.row();
        table.image().growX().padTop(10).height(3).color(Pal.accent).center();
        table.row();
        table.table(t -> {
            t.setCullingArea(new Rect()); // Make sure this isn't null for later

            int[] i = {0};
            final int max = Core.settings.getInt("maxschematicslisted");
            for(Schematic s : loadedRepositories.get(repo)){
                if(max != 0 && i[0] > max) break; // Avoid meltdown on large repositories

                if(selectedTags.any() && !s.labels.containsAll(selectedTags)) continue;  // Tags
                if((!nameSearchString.isEmpty() || !descSearchString.isEmpty()) &&
                        (nameSearchString.isEmpty() || !ignoreSymbols.matcher(s.name().toLowerCase()).replaceAll("").contains(nameSearchString)) &&
                        (descSearchString.isEmpty() || !ignoreSymbols.matcher(s.description().toLowerCase()).replaceAll("").contains(descSearchString))
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

                        buttons.button(Icon.info, style, () -> ui.schematics.showInfo(s)).tooltip("@info.title");
                        buttons.button(Icon.upload, style, () -> showExport(s)).tooltip("@editor.export");
                        buttons.button(Icon.download, style, () -> {
                            ui.showInfoFade("@schematic.saved");
                            schematics.add(s, Core.settings.getBool("schematicbrowserimporttags"));
                            Reflect.invoke(ui.schematics, "checkTags", new Object[]{s}, Schematic.class); // Vars.ui.schematics.checkTags(s)
                        }).tooltip("@client.schematic.browser.download");
                    }).growX().height(50f);
                    b.row();
                    b.stack(new SchematicsDialog.SchematicImage(s).setScaling(Scaling.fit), new Table(n -> {
                        n.top();
                        n.table(Styles.black3, c -> {
                            Label label = c.add("").style(Styles.outlineLabel).top().growX().maxWidth(200f - 8f)
                                .update(l -> {
                                    var txt = l.getText(); // Update the stringBuilder directly
                                    if (txt.length() == 0 || (Core.graphics.getFrameId() + i[0]) % 60 == 0) { // update() is run every frame even when the element is culled out, the solution is to only update a portion every frame FINISHME: Do we want to hack this and update the text in the draw() method which is only called when the element isn't culled?
                                        txt.setLength(0);
                                        if (!player.team().rules().infiniteResources && !state.rules.infiniteResources && player.core() != null && !player.core().items.has(s.requirements(reusableItemSeq))) txt.append("[#dd5656]");
                                        txt.append(s.name());
                                        reusableItemSeq.clear();
                                    }
                                }).get();
                            label.runUpdate(); // Update the text instantly
                            label.setEllipsis(true);
                            label.setAlignment(Align.center);
                        }).growX().margin(1).pad(4).maxWidth(Scl.scl(200f - 8f)).padBottom(0);
                    })).size(200f);
                }, () -> {
                    if(sel[0].childrenPressed()) return;
                    if(state.isMenu()){
                        ui.schematics.showInfo(s);
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

                if(++i[0] % cols == 0){
                    t.row();
                }
            }

            if(i[0]==0){
                if(!nameSearchString.isEmpty() || selectedTags.any()){
                    t.add("@none.found");
                }else{
                    t.add("@none").color(Color.lightGray);
                }
            }
        });
        table.row();
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
                    clientThread.post(() -> Main.INSTANCE.send(new SchematicTransmission(s), () -> Core.app.post(() ->
                        ui.showInfoToast(Core.bundle.get("client.finisheduploading"), 2f)
                    )));
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

    public void rebuildResults(){
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
        hide();
    }

    void getSettings(){
        repositoryLinks.clear();
        repositoryLinks.add(Core.settings.getString("schematicrepositories","bend-n/design-it").split(";"));

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
//                    ui.showErrorMessage(Core.bundle.format("client.schematic.browser.fail.parse", link, f.name())); // FINISHME: Find a better way to do this, currently it spams the screen with error messages
                }
            });
            schems.sort();
            loadedRepositories.get(link, () -> new Seq<>(schems.size)).clear().add(schems);
        }
        unloadedRepositories.clear();
    }

    void fetch(Seq<String> repos){
        Log.debug("Fetching schematics from repos: @", repos);
        ui.showInfoFade("@client.schematic.browser.fetching", 2f);
        for (String link : repos){
            Http.get(ghApi + "/repos/" + link, res -> handleBranch(link, res), e -> handleFetchError(link, e));
        }
    }

    void handleFetchError(String link, Throwable e){
        Core.app.post(() -> {
            Log.err("Schematic repository " + link + " could not be reached. " + e);
            ui.showErrorMessage(Core.bundle.format("client.schematic.browser.fail.fetch", link));
        });
    }

    void handleBranch(String link, Http.HttpResponse response){
        var json = new JsonReader().parse(response.getResultAsString());
        var branch = json.getString("default_branch");
        Http.get(ghApi + "/repos/" + link + "/zipball/" + branch, res -> handleRedirect(link, res), e -> handleFetchError(link, e));
    }

    void handleRedirect(String link, Http.HttpResponse response){
        if (response.getHeader("Location") != null) {
            Http.get(response.getHeader("Location"), r -> handleRepo(link, r), e -> handleFetchError(link, e));
        } else handleRepo(link, response);
    }

    void handleRepo(String link, Http.HttpResponse response){
        String fileName = link.replace("/","") + ".zip";
        Fi filePath = schematicRepoDirectory.child(fileName);
        filePath.writeBytes(response.getResult());
        Core.app.post(() ->{
            unfetchedRepositories.remove(link);
            unloadedRepositories.add(link);
            ui.showInfoFade(Core.bundle.format("client.schematic.browser.fetched", link), 2f);

            if (unfetchedRepositories.size == 0) {
                loadRepositories();
                rebuildResults();
            }
        });
    }

    @Override
    public Dialog show() {
        super.show();

        if (Core.app.isDesktop() && nameSearchField != null) {
            Core.scene.setKeyboardFocus(nameSearchField);
        }

        return this;
    }

    protected static class SchematicRepositoriesDialog extends BaseDialog {
        public Table repoTable = new Table();
        private final Pattern pattern = Pattern.compile("(https?://)?github\\.com/");
        private boolean refetch = false;
        private boolean rebuild = false;

        public SchematicRepositoriesDialog(){
            super("@client.schematic.browser.repo");

            buttons.defaults().size(width, 64f);
            buttons.button("@back", Icon.left, this::close);
            buttons.button("@client.schematic.browser.add", Icon.add, this::addRepo);
            makeButtonOverlay();
            addCloseListener();
            shown(this::setup);
            onResize(this::setup);
        }

        void setup(){
            rebuild();
            cont.pane( t -> {
               t.defaults().pad(5f);
               t.pane(p -> p.add(repoTable)).growX();
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
                })).padRight(20f).tooltip("@client.schematic.browser.edit");
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
                }).padRight(20f).tooltip("@client.schematic.browser.togglevisibility");
                table.add(new Label(link)).right();
                repoTable.add(table);
                repoTable.row();
            }
        }

        void editRepo(String link, Consumer<String> onClose){
            BaseDialog dialog = new BaseDialog("@client.schematic.browser.edit");
            TextField linkInput = new TextField(link);
            linkInput.setMessageText("author/repository");
            linkInput.setValidator( l -> !l.isEmpty());
            dialog.addCloseListener();
            dialog.cont.add(linkInput).width(400f);
            dialog.cont.row();
            dialog.cont.table(t -> {
                t.defaults().width(194f).pad(3f);
                t.button("@client.schematic.browser.add", () -> {
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
                ui.schematicBrowser.rebuildResults();
                rebuild = false;
            }
            Core.settings.put("schematicrepositories", ui.schematicBrowser.repositoryLinks.toString(";"));
            Core.settings.put("hiddenschematicrepositories", ui.schematicBrowser.hiddenRepositories.toString(";"));
            hide();
        }
    }
}
