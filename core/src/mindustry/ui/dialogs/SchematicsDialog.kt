package mindustry.ui.dialogs

import arc.Core
import arc.files.Fi
import arc.func.Cons
import arc.graphics.Color
import arc.graphics.Texture
import arc.graphics.g2d.Draw
import arc.graphics.g2d.Lines
import arc.graphics.g2d.TextureRegion
import arc.scene.style.TextureRegionDrawable
import arc.scene.ui.Button
import arc.scene.ui.Image
import arc.scene.ui.TextButton
import arc.scene.ui.layout.Scl
import arc.scene.ui.layout.Table
import arc.util.Align
import arc.util.Scaling
import mindustry.Vars
import mindustry.core.GameState
import mindustry.game.Schematic
import mindustry.game.Schematics
import mindustry.gen.Icon
import mindustry.gen.Tex
import mindustry.graphics.Pal
import mindustry.ui.Cicon
import mindustry.ui.Styles
import mindustry.world.blocks.storage.CoreBlock

class SchematicsDialog : FloatingDialog("\$schematics") {
    private val info = SchematicInfoDialog()
    private var search = ""
    fun setup() {
        search = ""
        val rebuildPane = arrayOf<Runnable?>(null)
        cont.top()
        cont.clear()
        cont.table { s: Table ->
            s.left()
            s.addImage(Icon.zoom)
            s.addField(search) { res: String ->
                search = res
                rebuildPane[0]!!.run()
            }.growX()
        }.fillX().padBottom(4f)
        cont.row()
        cont.pane { t: Table ->
            t.top()
            t.margin(20f)
            rebuildPane[0] = Runnable {
                t.clear()
                var i = 0
                if (!Vars.schematics.all().contains { s: Schematic -> search.isEmpty() || s.name().toLowerCase().contains(search.toLowerCase()) }) {
                    t.add("\$none")
                }
                for (s in Vars.schematics.all()) {
                    if (!search.isEmpty() && !s.name().toLowerCase().contains(search.toLowerCase())) continue
                    val sel = arrayOf<Button?>(null)
                    sel[0] = t.addButton({ b: Button ->
                        b.top()
                        b.margin(0f)
                        b.table { buttons: Table ->
                            buttons.left()
                            buttons.defaults().size(50f)
                            val style = Styles.clearPartiali
                            buttons.addImageButton(Icon.info, style) { showInfo(s) }
                            buttons.addImageButton(Icon.download, style) { showExport(s) }
                            buttons.addImageButton(Icon.pencil, style) {
                                Vars.ui.showTextInput("\$schematic.rename", "\$name", s.name()) { res: String ->
                                    val replacement = Vars.schematics.all().find { other: Schematic -> other.name() == res && other !== s }
                                    if (replacement != null) {
                                        //renaming to an existing schematic is not allowed, as it is not clear how the tags would be merged, and which one should be removed
                                        Vars.ui.showErrorMessage("\$schematic.exists")
                                        return@showTextInput
                                    }
                                    s.tags.put("name", res)
                                    s.save()
                                    rebuildPane[0]!!.run()
                                }
                            }
                            if (s.hasSteamID()) {
                                buttons.addImageButton(Icon.link, style) { Vars.platform.viewListing(s) }
                            } else {
                                buttons.addImageButton(Icon.trash, style) {
                                    if (s.mod != null) {
                                        Vars.ui.showInfo(Core.bundle.format("mod.item.remove", s.mod.meta.displayName()))
                                    } else {
                                        Vars.ui.showConfirm("\$confirm", "\$schematic.delete.confirm") {
                                            Vars.schematics.remove(s)
                                            rebuildPane[0]!!.run()
                                        }
                                    }
                                }
                            }
                        }.growX().height(50f)
                        b.row()
                        b.stack(SchematicImage(s).setScaling(Scaling.fit), Table(Cons { n: Table ->
                            n.top()
                            n.table(Styles.black3) { c: Table ->
                                val label = c.add(s.name()).style(Styles.outlineLabel).color(Color.white).top().growX().maxWidth(200f - 8f).get()
                                label.update {
                                    val core = Vars.player.closestCore as CoreBlock.CoreEntity
                                    var red = false
                                    for(item in s.requirements()){
                                        if(item.amount > core.items.get(item.item)){
                                            red = true
                                            break
                                        }
                                    }
                                    if(red){
                                        label.setColor(Color.red)
                                    }else{
                                        setColor(Color.white)
                                    }
                                }
                                label.setEllipsis(true)
                                label.setAlignment(Align.center)
                            }.growX().margin(1f).pad(4f).maxWidth(Scl.scl(200f - 8f)).padBottom(0f)
                        })).size(200f)
                    }) {
                        if (sel[0]!!.childrenPressed()) return@addButton
                        if (Vars.state.`is`(GameState.State.menu)) {
                            showInfo(s)
                        } else {
                            Vars.control.input.useSchematic(s)
                            hide()
                        }
                    }.pad(4f).style(Styles.cleari).get()
                    sel[0]?.style?.up = Tex.pane
                    if (++i % (if (Vars.mobile) if (Core.graphics.isPortrait) 2 else 3 else 4) == 0) {
                        t.row()
                    }
                }
            }
            rebuildPane[0]!!.run()
        }.get().setScrollingDisabled(true, false)
    }

    fun showInfo(schematic: Schematic) {
        info.show(schematic)
    }

    fun showImport() {
        val dialog = FloatingDialog("\$editor.export")
        dialog.cont.pane { p: Table ->
            p.margin(10f)
            p.table(Tex.button) { t: Table ->
                val style = Styles.cleart
                t.defaults().size(280f, 60f).left()
                t.row()
                t.addImageTextButton("\$schematic.copy.import", Icon.copy, style) {
                    dialog.hide()
                    try {
                        val s = Schematics.readBase64(Core.app.clipboardText)
                        s.removeSteamID()
                        Vars.schematics.add(s)
                        setup()
                        Vars.ui.showInfoFade("\$schematic.saved")
                        showInfo(s)
                    } catch (e: Throwable) {
                        Vars.ui.showException(e)
                    }
                }.marginLeft(12f).disabled { b: TextButton? -> Core.app.clipboardText == null || !Core.app.clipboardText.startsWith(Vars.schematicBaseStart) }
                t.row()
                t.addImageTextButton("\$schematic.importfile", Icon.download, style) {
                    Vars.platform.showFileChooser(true, Vars.schematicExtension) { file: Fi? ->
                        dialog.hide()
                        try {
                            val s = Schematics.read(file)
                            s.removeSteamID()
                            Vars.schematics.add(s)
                            setup()
                            showInfo(s)
                        } catch (e: Exception) {
                            Vars.ui.showException(e)
                        }
                    }
                }.marginLeft(12f)
                t.row()
                if (Vars.steam) {
                    t.addImageTextButton("\$schematic.browseworkshop", Icon.book, style) {
                        dialog.hide()
                        Vars.platform.openWorkshop()
                    }.marginLeft(12f)
                }
            }
        }
        dialog.addCloseButton()
        dialog.show()
    }

    fun showExport(s: Schematic) {
        val dialog = FloatingDialog("\$editor.export")
        dialog.cont.pane { p: Table ->
            p.margin(10f)
            p.table(Tex.button) { t: Table ->
                val style = Styles.cleart
                t.defaults().size(280f, 60f).left()
                if (Vars.steam && !s.hasSteamID()) {
                    t.addImageTextButton("\$schematic.shareworkshop", Icon.book, style
                    ) { Vars.platform.publish(s) }.marginLeft(12f)
                    t.row()
                    dialog.hide()
                }
                t.addImageTextButton("\$schematic.copy", Icon.copy, style) {
                    dialog.hide()
                    Vars.ui.showInfoFade("\$copied")
                    Core.app.clipboardText = Vars.schematics.writeBase64(s)
                }.marginLeft(12f)
                t.row()
                t.addImageTextButton("\$schematic.exportfile", Icon.export, style) {
                    if (!Vars.ios) {
                        Vars.platform.showFileChooser(false, Vars.schematicExtension) { file: Fi? ->
                            dialog.hide()
                            try {
                                Schematics.write(s, file)
                            } catch (e: Throwable) {
                                Vars.ui.showException(e)
                            }
                        }
                    } else {
                        dialog.hide()
                        try {
                            val file = Core.files.local(s.name() + "." + Vars.schematicExtension)
                            Schematics.write(s, file)
                            Vars.platform.shareFile(file)
                        } catch (e: Throwable) {
                            Vars.ui.showException(e)
                        }
                    }
                }.marginLeft(12f)
            }
        }
        dialog.addCloseButton()
        dialog.show()
    }

    class SchematicImage(s: Schematic) : Image(Tex.clear) {
        var scaling = 16f
        var thickness = 4f
        var borderColor = Pal.gray
        private val schematic: Schematic
        var set = false
        override fun draw() {
            val checked = (parent.parent is Button
                    && (parent.parent as Button).isOver)
            val wasSet = set
            if (!set) {
                Core.app.post { setPreview() }
                set = true
            }
            val background = Core.assets.get("sprites/schematic-background.png", Texture::class.java)
            val region = Draw.wrap(background)
            val xr = width / scaling
            val yr = height / scaling
            region.u2 = xr
            region.v2 = yr
            Draw.color()
            Draw.alpha(parentAlpha)
            Draw.rect(region, x + width / 2f, y + height / 2f, width, height)
            if (wasSet) {
                super.draw()
            } else {
                Draw.rect(Icon.refresh.region, x + width / 2f, y + height / 2f, width / 4f, height / 4f)
            }
            Draw.color(if (checked) Pal.accent else borderColor)
            Draw.alpha(parentAlpha)
            Lines.stroke(Scl.scl(thickness))
            Lines.rect(x, y, width, height)
            Draw.reset()
        }

        private fun setPreview() {
            val draw = TextureRegionDrawable(TextureRegion(Vars.schematics.getPreview(schematic)))
            drawable = draw
            setScaling(Scaling.fit)
        }

        init {
            setScaling(Scaling.fit)
            schematic = s
            if (Vars.schematics.hasPreview(s)) {
                setPreview()
                set = true
            }
        }
    }

    class SchematicInfoDialog internal constructor() : FloatingDialog("") {
        fun show(schem: Schematic) {
            cont.clear()
            title.setText("[[" + Core.bundle["schematic"] + "] " + schem.name())
            cont.add(Core.bundle.format("schematic.info", schem.width, schem.height, schem.tiles.size)).color(Color.lightGray)
            cont.row()
            cont.add(SchematicImage(schem)).maxSize(800f)
            cont.row()
            val arr = schem.requirements()
            cont.table { r: Table ->
                var i = 0
                for (s in arr) {
                    r.addImage(s.item.icon(Cicon.small)).left()
                    r.add(s.amount.toString() + "").padLeft(2f).left().color(Color.lightGray).padRight(4f)
                    if (++i % 4 == 0) {
                        r.row()
                    }
                }
            }
            show()
        }

        init {
            setFillParent(true)
            addCloseButton()
        }
    }

    init {
        Core.assets.load("sprites/schematic-background.png", Texture::class.java).loaded = Cons<Any> { t: Any -> (t as Texture).setWrap(Texture.TextureWrap.Repeat) }
        shouldPause = true
        addCloseButton()
        buttons.addImageTextButton("\$schematic.import", Icon.download) { showImport() }
        shown { setup() }
        onResize { setup() }
    }
}