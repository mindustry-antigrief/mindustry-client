package mindustry.client.graphics

import arc.graphics.*
import arc.graphics.g2d.*
import mindustry.Vars.*
import mindustry.client.navigation.*
import mindustry.graphics.*

object RangeDrawer {
    fun draw(ranges: MutableList<Pair<TurretPathfindingEntity, Color>>) { // FINISHME: Remove this class as it's completely redundant
        for (c in ranges) {
                Drawf.dashCircle(c.first.x(), c.first.y(), c.first.range - tilesize, c.second)
        }
        Draw.reset()
    }
}
