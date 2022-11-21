package mindustry.client

import arc.*
import arc.math.geom.*
import arc.util.*
import mindustry.*
import mindustry.gen.*
import mindustry.input.*

object Spectate {
    var pos: Position? = null
    var cursor = false

    fun update() {
        val pos = pos ?: return
        if (cursor && pos is Player) Tmp.v1.set(pos.mouseX, pos.mouseY) else Tmp.v1.set(pos)
        Core.camera.position.lerpDelta(Tmp.v1, if (Core.settings.getBool("smoothcamera")) 0.08f else 1f)
    }

    @JvmOverloads
    fun spectate(pos: Position, cursor: Boolean = false) {
        if (pos.x < -Vars.finalWorldBounds || pos.y < -Vars.finalWorldBounds || pos.x > Vars.world.unitWidth() + Vars.finalWorldBounds || pos.y > Vars.world.unitHeight() + Vars.finalWorldBounds) return
        (Vars.control.input as? DesktopInput)?.panning = true
        this.pos = pos
        this.cursor = cursor
    }

    fun draw() {
        (pos as? Player)?.unit()?.drawBuildPlans()
    }
}
