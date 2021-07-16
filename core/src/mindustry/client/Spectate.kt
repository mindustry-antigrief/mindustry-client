package mindustry.client

import arc.*
import arc.math.geom.*
import mindustry.*
import mindustry.Vars.control
import mindustry.input.*

object Spectate {
    var pos: Position? = null

    fun update() {
        if (pos != null) {
            Core.camera.position.lerpDelta(pos, if (Core.settings.getBool("smoothcamera")) 0.08f else 1f)
        }
    }

    fun spectate(pos: Position) {
        if (pos.x < -Vars.finalWorldBounds || pos.y < -Vars.finalWorldBounds || pos.x > Vars.world.unitWidth() + Vars.finalWorldBounds || pos.y > Vars.world.unitHeight() + Vars.finalWorldBounds) return // Dont go to space
        (control.input as? DesktopInput)?.panning = true
        Spectate.pos = pos
    }
}
