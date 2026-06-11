package mindustry.client

import arc.*
import arc.math.geom.*
import arc.util.*
import mindustry.*
import mindustry.client.ClientVars.*
import mindustry.gen.*
import mindustry.input.*

object Spectate { // FINISHME v8: Remove this as vanilla now has a spectate feature that we should use instead.
    var pos: Position? = null
    var cursor = false

    fun update() {
        val currentPos = pos ?: return
        if (currentPos is Entityc && !currentPos.isAdded) {
            reset()
            return
        }
        if (cursor && currentPos is Player) Tmp.v1.set(currentPos.mouseX, currentPos.mouseY) else Tmp.v1.set(currentPos)
        Core.camera.position.lerpDelta(Tmp.v1, if (Core.settings.getBool("smoothcamera")) 0.08f else 1f)
    }

    fun reset(): Boolean {
        if (pos != null) {
            pos = null
            if (Vars.ui.listfrag.shown()) {
                Vars.ui.listfrag.rebuild()
            }
            return true
        }
        return false
    }

    @JvmOverloads
    fun spectate(pos: Position, cursor: Boolean = false) {
        if (pos.x < -Vars.finalWorldBounds || pos.y < -Vars.finalWorldBounds || pos.x > Vars.world.unitWidth() + Vars.finalWorldBounds || pos.y > Vars.world.unitHeight() + Vars.finalWorldBounds) return
        (Vars.control.input as? DesktopInput)?.panning = true
        this.pos = pos
        this.cursor = cursor
    }

    fun draw() {
        // if(!hidingPlans) (pos as? Player)?.unit()?.drawBuildPlans()
    }
}
