package mindustry.client

import arc.Core
import arc.math.geom.Position
import mindustry.input.DesktopInput

object Spectate {
    var pos: Position? = null

    fun update() {
        if (pos != null) {
            Core.camera.position.lerpDelta(pos, if (Core.settings.getBool("smoothcamera")) 0.08f else 1f)
        }
    }

    fun spectate(pos: Position?) {
        DesktopInput.panning = true
        Spectate.pos = pos
    }
}
