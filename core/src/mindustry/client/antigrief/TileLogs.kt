package mindustry.client.antigrief

import mindustry.Vars
import mindustry.core.World

object TileLogs {
    private var logs = emptyArray<TileLog>()
    private var width = 0
    private var height = 0

    fun reset(world: World) {
        width = world.width()
        height = world.height()
        logs = Array(width * height) { n -> TileLog(world.tile(n % width, n / width)) }
    }

    operator fun get(x: Int, y: Int): TileLog {
        if (Vars.world?.width() != width || Vars.world?.height() != height) reset(Vars.world)
        return logs[(y * width) + x]
    }
}
