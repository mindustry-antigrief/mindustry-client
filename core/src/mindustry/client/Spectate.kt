package mindustry.client

import arc.*
import arc.math.geom.*
import mindustry.*
import mindustry.gen.Unit
import mindustry.input.*

object Spectate {
    var pos: Position? = null

    fun update() {
        pos ?: return
        Core.camera.position.lerpDelta(pos, if (Core.settings.getBool("smoothcamera")) 0.08f else 1f)
    }

    fun spectate(pos: Position) {
        if (pos.x < -Vars.finalWorldBounds || pos.y < -Vars.finalWorldBounds || pos.x > Vars.world.unitWidth() + Vars.finalWorldBounds || pos.y > Vars.world.unitHeight() + Vars.finalWorldBounds) return
        (Vars.control.input as? DesktopInput)?.panning = true
        Spectate.pos = pos
    }

    fun draw() {
        (pos as? Unit)?.drawBuildPlans()
    }
}
