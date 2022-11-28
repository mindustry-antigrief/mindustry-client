package mindustry.client.antigrief

import arc.*
import arc.struct.*
import mindustry.Vars.*
import mindustry.client.ClientVars.*
import mindustry.client.navigation.*
import mindustry.content.*
import mindustry.entities.units.*
import mindustry.world.*
import java.time.Instant
import kotlin.math.*

fun rollbackTiles(tiles: Iterable<Tile>, timeInstant: Instant){
    val time =  if (timeInstant > TileRecords.joinTime) timeInstant else TileRecords.joinTime
    clientThread.post {
        val plans = Seq<BuildPlan>()
        val toBreak = IntSet()

        tiles.forEach {
            val record = TileRecords[it] ?: return@forEach
            // Get the sequence associated with the rollback time
            val seq: TileLogSequence = record.lastSequence(time) ?: return@forEach
            val state = seq.snapshot.clone()
            // Step through logs until time is reached
            for (diff in seq.iterator()) {
                if (diff.time > time) break
                diff.apply(state)
            }
            state.restoreState(it, plans, toBreak)
        }
        toBreak.clear()
        if (plans.size == 0) {
            Core.app.post { player.sendMessage(Core.bundle.get("client.norebuildsfound")) }
            return@post
        }
        Core.app.post {
            var numConfigs = configs.size
            var numPlans = player.unit().plans.size
            control.input.flushPlans(plans, false, true, false)
            numPlans = player.unit().plans.size - numPlans
            numConfigs = configs.size - numConfigs
            if (numPlans == 0 && numConfigs == 0) {
                Core.app.post { player.sendMessage(Core.bundle.get("client.norebuildsfound")) }
            } else {
                player.sendMessage("[accent]Queued [white]${player.unit().plans.size - numPlans}[] builds and [white]${configs.size - numConfigs}[] configs.")
            }
            plans.clear()
        }
    }
}

fun rebuildBroken(tiles: Iterable<Tile>, timeStart: Instant, timeEnd: Instant, range: Float){
    clientThread.post {
        val states: Seq<TileState> = Seq()
        tiles.forEach {
            val sequences = TileRecords[it]?.sequences ?: return@forEach
            var last: TileState? = null
            var hasBeenOverwritten = false // Whether there is another block that is placed offset some time in the future

            seq@ for (seq in sequences.asReversed()) { // Rebuilds are likely used on recent states, so start from the last logs
                if (seq.snapshot.time > timeEnd) continue // Skip to the first sequence that overlaps with time interval
                val state = seq.snapshot.clone()
                last = if (state.isRootTile && seq.snapshot.time > timeStart) state.clone() else null
                // Step through logs until time is reached
                for (diff in seq.iterator()) {
                    if (diff.time > timeEnd) break // Abort if we have reached time end
                    if (diff.time >= timeStart && diff is TileBreakLog && state.block !== Blocks.air) {
                        if (state.isRootTile) last = state.clone()
                        hasBeenOverwritten = true
                    }
                    diff.apply(state)
                }
                if ((last != null && last.isRootTile) || hasBeenOverwritten) break@seq // Break if we can restore that, or no earlier logs need to be used
            }
            if (last == null || !last.isRootTile) return@forEach
            states.add(last)
        }

        if (states.size == 0) {
            Core.app.post { player.sendMessage(Core.bundle.get("client.norebuildsfound")) }
            return@post
        }
        // The following is so inefficient lol what
        // FINSIHME: Do not plan over existing buildings
        clientThread.sortingInstance.sort(states, Comparator.comparing { it.time }) // Sort by time to deal with tile overlaps
        val minX = max(floor((player.x - range) / tilesize).toInt(), 0)
        val minY = max(floor((player.y - range) / tilesize).toInt(), 0)
        val takenTiles = GridBits(min(ceil((player.x + range) / tilesize).toInt() - minX, world.width()) + 1, min(ceil((player.y + range) / tilesize).toInt() - minY, world.height()) + 1)
        val plans = Seq<BuildPlan>()
        for (i in states.size - 1 downTo 0) { // Reverse this because latest should be rebuilt, not the earliest
            var taken = false
            val state = states[i]
            // state.x, state.y describe the middle/bottom-left of the tile. So we steal code from TileLog.companion.linkedArea
            val size = state.block.size
            val offset = -(size - 1) / 2
            val bounds = IntRectangle(state.x + offset, state.y + offset + size - 1, size, size)
            bounds.iterator().forEach {
                taken = taken || takenTiles.get(it.x - minX, it.y - minY) // Maybe use quadtree for large things
            }
            if (taken) continue
            bounds.iterator().forEachRemaining {
                takenTiles.set(it.x - minX, it.y - minY)
            }
            plans.add(BuildPlan(state.x, state.y, state.rotation, state.block, state.configuration))
        }
        states.clear()
        if (plans.size == 0) {
            Core.app.post { player.sendMessage(Core.bundle.get("client.norebuildsfound")) }
            return@post
        }
        Core.app.post {
            control.input.flushPlans(plans)
            player.sendMessage("[accent]Queued [white]${plans.size} blocks for rebuilding.")
            plans.clear()
        }
    }
}

fun undoPlayer(tiles: Iterable<Tile>, id: Int){
    clientThread.post {
        var playerName: String? = null
        val plans: Seq<BuildPlan> = Seq()
        var prevID: Int

        val toBreak = IntSet()
        tiles.forEach {
            val sequences = TileRecords[it]?.sequences ?: return@forEach
            var last: TileState? = null

            val seqReversed = sequences.asReversed()
            for ((i, seq) in seqReversed.withIndex()) { // For each tile, get the last state before it was touched by the player
                val state = seq.snapshot.clone()
                prevID = id
                // Evaluate if the current snapshot was caused by the target player. If so, do not use it.
                last = if (i + 1 < seqReversed.size && ((seqReversed[i + 1].logs.lastOrNull()?.cause?.playerID?: id.inv()) == id)) null else state.clone()
                for (diff in seq.iterator()) {
                    if (prevID != id && diff.cause.playerID == id) { // Only clone state if the ids change
                        last = state.clone()
                    }
                    diff.apply(state)
                    prevID = diff.cause.playerID
                    if (playerName == null && prevID == id) playerName = diff.cause.shortName
                }
                if (prevID != id) { // Capture last diff
                    if (i == 0) return@forEach // If last diff can be captured, it is not different from the current state
                    last = state.clone()
                }
                if (!state.isRootTile && state.block !== Blocks.air) return@forEach // If there is something else on top, do not build it
                if (last != null) break
            }
            if (last == null || (!last.isRootTile && last.block !== Blocks.air)) return@forEach
            last.restoreState(it, plans, toBreak)
        }
        toBreak.clear()

        if (playerName == null) {
            Core.app.post { player.sendMessage(Core.bundle.get("client.norebuildsfound")) }
            return@post
        }

        Core.app.post {
            val numConfigs = configs.size
            val numPlans = player.unit().plans.size
            control.input.flushPlans(plans, false, true, false) // Overplace
            player.sendMessage("[accent] Queued [white]${plans.size - numPlans}[] builds and [white]${configs.size - numConfigs}[] configs to undo actions by $playerName")
            plans.clear()
        }
    }
}
