package mindustry.client

import arc.math.Rand
import arc.struct.Array
import mindustry.Vars
import mindustry.world.Tile
import mindustry.world.blocks.power.PowerNode

object PowerGridFinder {
    fun updatePower() {
        if (Vars.world == null) {
            return
        }
        if (Vars.world.tiles == null) {
            return
        }
        if (Rand().chance(1 / 60f.toDouble()) || Vars.world.tile(Vars.powerTilePos) == null || Vars.world.tile(Vars.powerTilePos).block() == null) {
            val nodes = Array<Tile>()
            for (tiles in Vars.world.tiles) {
                for (tile2 in tiles) {
                    if (tile2.block() is PowerNode && tile2.team === Vars.player.team) {
                        nodes.add(tile2)
                    }
                }
            }
            var links = Array<IntArray>()
            val found = Array<Int>()
            for (tile2 in nodes) {
                if (found.contains(tile2.entity.power.graph.id)) {
                    continue
                }
                links.add(intArrayOf(tile2.entity.power.graph.size, tile2.pos()))
                found.add(tile2.entity.power.graph.id)
            }
            if (links.size == 0) {
                Vars.powerTilePos = 0
            } else {
                links = links.sort { a: IntArray -> a[0].toFloat() }
                links.reverse()
                Vars.powerTilePos = links[0][1]
                //                    System.out.println(powerTilePos);
//                    System.out.println(world.tile(powerTilePos).block());
            }
        }
    }
}
