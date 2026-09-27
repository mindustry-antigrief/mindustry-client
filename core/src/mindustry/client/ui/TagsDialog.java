package mindustry.client.ui;

import arc.*;
import arc.func.*;
import arc.graphics.*;
import arc.input.*;
import arc.scene.event.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;

/** Interface for editing/displaying all tags. */
public class TagsDialog extends BaseDialog{
    private static final float tagh = 42f;
    public record TagCountResult(String text, boolean isEmpty){}
    public record HeaderButton(String text, Drawable icon, Cons<Runnable> action){}
    private final Seq<String> tags, selectedTags;
    private Runnable tagsChanged = () -> {}, selectionChanged = () -> {}, pruneTags = () -> {};
    private Func<String, TagCountResult> countFormat;
    private Cons2<String, Runnable> renameHandler, deleteHandler;

    private final Seq<HeaderButton> extraButtons = new Seq<>();

    public TagsDialog(Seq<String> tags, Seq<String> selectedTags){
        super("@schematic.edittags");
        this.tags = tags;
        this.selectedTags = selectedTags;
        setMovable(false);
        addCloseButton();
    }

    public TagsDialog tagsChanged(Runnable runnable){
        this.tagsChanged = runnable;
        return this;
    }
    public TagsDialog selectionChanged(Runnable runnable){
        this.selectionChanged = runnable;
        return this;
    }
    public TagsDialog pruneTags(Runnable runnable){
        this.pruneTags = runnable;
        return this;
    }
    public TagsDialog rename(Cons2<String, Runnable> runnable){
        this.renameHandler = runnable;
        return this;
    }
    public TagsDialog delete(Cons2<String, Runnable> runnable){
        this.deleteHandler = runnable;
        return this;
    }
    public TagsDialog count(Func<String, TagCountResult> format){
        this.countFormat = format;
        return this;
    }
    public TagsDialog addButton(String text, Drawable icon, Cons<Runnable> action){
        extraButtons.add(new HeaderButton(text, icon, action));
        return this;
    }

    @Override
    public Dialog show(){
        cont.clear();
        Runnable[] rebuild = {null};
        Seq<Table> tagTables = new Seq<>(); //tracks tag tables and their coords/size

        cont.pane(p -> {
            rebuild[0] = () -> {
                p.clearChildren();
                p.margin(12f).defaults().fillX().left();
                tagTables.clear();

                //toolbar
                p.table(t -> {
                    t.left().defaults().fillX().height(tagh).pad(2);
                    t.button("@client.schematic.cleartags", Icon.refresh, () -> {
                        selectedTags.clear();
                        selectionChanged.run();
                        rebuild[0].run();
                    }).wrapLabel(false).get().getLabelCell().padLeft(5);

                    t.button("@client.schematic.prunetags", Icon.trash, () -> {
                        pruneTags.run();
                        rebuild[0].run();
                    }).wrapLabel(false).get().getLabelCell().padLeft(5);

                    for(HeaderButton btn : extraButtons){
                        t.button(btn.text, btn.icon, () -> btn.action.get(rebuild[0]))
                        .wrapLabel(false).get().getLabelCell().padLeft(5);
                    }
                });
                p.row();

                float sum = 0f;
                Table current = new Table().left();

                for(String tag : tags){
                    float si = 40f;

                    //the tag card
                    Table next = new Table(Tex.whiteui, n -> {
                        n.setColor(Pal.gray);
                        n.margin(5f);

                        //move tag
                        n.table(t -> {
                            t.button(Icon.move, Styles.emptyi, () -> {
                            }).maxWidth(si).growY().tooltip("@editor.holddrag").get().addListener(new InputListener(){
                                int dragPointer = -1;

                                @Override
                                public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
                                    if(button != KeyCode.mouseLeft || pointer > 0) return false;
                                    dragPointer = pointer;
                                    return true;
                                }

                                @Override
                                public void touchDragged(InputEvent event, float x, float y, int pointer){
                                    if(pointer != dragPointer) return;

                                    for(int i = 0; i < tagTables.size; i++){
                                        var table = tagTables.get(i);

                                        if(table.userObject.equals(tag)) continue;

                                        table.stageToLocalCoordinates(Tmp.v1.set(event.stageX, event.stageY));
                                        if(Tmp.v1.x >= 0 && Tmp.v1.x <= table.getWidth() && Tmp.v1.y >= 0 && Tmp.v1.y <= table.getHeight()){
                                            int src = tags.indexOf(tag);
                                            int dst = tags.indexOf((String)table.userObject);

                                            if(src != -1 && dst != -1 && src != dst){
                                                tags.swap(src, dst);
                                                tagsChanged.run();
                                                rebuild[0].run();
                                            }
                                            break;
                                        }
                                    }
                                }

                                @Override
                                public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button){
                                    if(pointer != dragPointer) return;
                                    dragPointer = -1;
                                }
                            });
                        }).fillY().padRight(5);

                        n.table(t -> {
                            t.add(tag).left().row();

                            if(countFormat != null){
                                TagCountResult res = countFormat.get(tag);
                                t.add(res.text).left()
                                .update(b -> {
                                    if(b.hasMouse()) b.setColor(Pal.accent);
                                    else if(selectedTags.contains(tag)) b.setColor(Color.lime);
                                    else if(res.isEmpty) b.setColor(Color.red);
                                    else b.setColor(Color.lightGray);
                                })
                                .get().clicked(() -> {
                                    if(!selectedTags.contains(tag)) selectedTags.add(tag);
                                    else selectedTags.remove(tag);
                                    selectionChanged.run();
                                });
                            }
                        }).growX().fillY();

                        //tag actions
                        if(renameHandler != null || deleteHandler != null){
                            n.table(b -> {
                                b.margin(2);

                                if(renameHandler != null){
                                    b.button(Icon.pencil, Styles.emptyi, () -> renameHandler.get(tag, rebuild[0]))
                                    .size(si).tooltip("@schematic.renametag").row();
                                }

                                if(deleteHandler != null){
                                    b.button(Icon.trash, Styles.emptyi, () -> deleteHandler.get(tag, rebuild[0]))
                                    .size(si).tooltip("@save.delete");
                                }
                            }).fillY();
                        }
                    });

                    next.userObject = tag;
                    next.pack();
                    tagTables.add(next);
                    float w = next.getPrefWidth() + Scl.scl(6f);

                    if(w * 2f + sum >= Core.graphics.getWidth() * 0.9f){
                        p.add(current).row();
                        current = new Table().left();
                        sum = 0f;
                    }

                    current.add(next).minWidth(210).pad(4);
                    sum += w;
                }

                if(sum > 0){
                    p.add(current).row();
                }
            };

            resized(true, rebuild[0]);
        }).scrollX(false);

        return super.show();
    }
}