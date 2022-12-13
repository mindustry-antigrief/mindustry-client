package mindustry.client.utils

import arc.*
import arc.struct.*
import mindustry.Vars.*
import mindustry.client.navigation.*
import mindustry.gen.*
import mindustry.graphics.*
import mindustry.world.blocks.logic.*
import java.util.concurrent.*

object ProcessorFinder {
    private val highlighted: CopyOnWriteArrayList<LogicBlock.LogicBuild> = CopyOnWriteArrayList()
    val queries: CopyOnWriteArrayList<Regex> = CopyOnWriteArrayList()

    fun search(query: Regex) {
        highlighted.clear()
        val builds: Seq<Building> = player.team().data().buildings.filter { it is LogicBlock.LogicBuild }
    
        clientThread.post {
            var matchCount = 0
            var processorCount = 0
            for (build in builds) {
                val logicBuild = build as LogicBlock.LogicBuild
                if (query.containsMatchIn((logicBuild.code))) {
                    matchCount++
                    highlighted.add(logicBuild)
                }
                processorCount++
            }
    
            Core.app.post {
                if (matchCount == 0) player.sendMessage(Core.bundle.get("client.processorpatcher.nomatches"))
                else player.sendMessage(Core.bundle.format("client.processorpatcher.foundmatches", matchCount, processorCount))
            }
        }

        queries.add(query)
    }
    
    fun searchAll() {
        highlighted.clear()
    
        val tiles = world.tiles
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
                    }
                    processorCount++
                }
            }
    
            Core.app.post {
                if (matchCount == 0) player.sendMessage(Core.bundle.get("client.procesorpatcher.nomatches"))
                else ui.chatfrag.addMsg(Core.bundle.format("client.procesorpatcher.foundmatches", matchCount, processorCount))
            }
        }
    }

    fun list() {
        val sb = StringBuilder(Core.bundle.get("client.command.procfind.list"))
        for (build in highlighted) {
            sb.append(String.format("(%d, %d), ", (build.x / 8).toInt(), (build.y / 8).toInt()))
        }
        ui.chatfrag.addMsg(sb.toString()).findCoords()
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
