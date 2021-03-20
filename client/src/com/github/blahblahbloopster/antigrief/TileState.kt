package com.github.blahblahbloopster.antigrief

import arc.scene.Element
import mindustry.world.Block
import mindustry.world.Tile

class TileState(tile: Tile, val block: Block, val configuration: Any?) {
    val x = tile.x
    val y = tile.y

    fun toElement(): Element {

    }
}
