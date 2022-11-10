package mindustry.client.utils

import arc.Core
import arc.struct.Seq
import mindustry.Vars
import mindustry.Vars.player
import mindustry.Vars.tilesize
import mindustry.client.navigation.clientThread
import mindustry.gen.Building
import mindustry.graphics.Drawf
import mindustry.world.blocks.logic.LogicBlock
import java.util.concurrent.CopyOnWriteArrayList

object ProcessorFinder {
    private val highlighted: CopyOnWriteArrayList<LogicBlock.LogicBuild> = CopyOnWriteArrayList()
    val queries: CopyOnWriteArrayList<Regex> = CopyOnWriteArrayList()

    fun search() {
        highlighted.clear()
        val builds: Seq<Building> = player.team().data().buildings.filter { it is LogicBlock.LogicBuild }
    
        clientThread.post {
            var matchCount = 0
            var processorCount = 0
            for (build in builds) {
                val logicBuild = build as LogicBlock.LogicBuild
                for (query in queries) {
                    if (query.containsMatchIn((logicBuild.code))) {
                        matchCount++
                        highlighted.add(logicBuild)
                    }
                    processorCount++
                }
            }
    
            Core.app.post {
                if (matchCount == 0) player.sendMessage(Core.bundle.get("client.procesorpatcher.nomatches"))
                else player.sendMessage(Core.bundle.format("client.procesorpatcher.nomatches", matchCount, processorCount))
            }
        }
    }
    
    fun searchAll() {
        highlighted.clear()
    
        val tiles = Vars.world.tiles
        clientThread.post {
            var matchCount = 0
            var processorCount = 0
            for (tile in tiles) {
                if (tile.build is LogicBlock.LogicBuild) {
                    for (query in queries) {
                        if (query.containsMatchIn(((tile.build as LogicBlock.LogicBuild).code))) {
                            matchCount++
                            highlighted.add((tile.build as LogicBlock.LogicBuild))
                        }
                        processorCount++
                    }
                }
            }
    
            Core.app.post {
                if (matchCount == 0) player.sendMessage(Core.bundle.get("client.procesorpatcher.nomatches"))
                else player.sendMessage(Core.bundle.format("client.procesorpatcher.foundmatches", matchCount, processorCount))
            }
        }
    }

    fun list() {
        val sb = StringBuilder(Core.bundle.get("client.command.procfind.list"))
        for (build in highlighted) {
            sb.append(String.format("(%d, %d), ", build.x / 8, build.y / 8))
        }
        player.sendMessage(sb.toString())
    }

    fun clear() {
        queries.clear()
        highlighted.clear()
    }

    fun getCount(): Int {
        return highlighted.size
    }

    fun draw() {
        for (build in highlighted) {
            Drawf.square(build.x, build.y, build.block.size * tilesize / 2f + 8f)
        }
    }
}
