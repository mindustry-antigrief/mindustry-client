package mindustry.client.ui

import arc.*
import arc.scene.ui.layout.*
import arc.struct.*
import arc.util.*
import mindustry.Vars.*
import mindustry.content.TechTree.*
import mindustry.ctype.*
import mindustry.game.*
import mindustry.type.*
import mindustry.ui.dialogs.ResearchDialog.*

/** Handles various client behavior related to research in campaign */
object ResearchAssistant : Table() {
    private val queue = Seq<TechTreeNode>()
    private var sectors = content.planets().sum { it.sectors.count(Sector::hasBase) } // Captured sector count FINISHME: Have a number for the current planet too
    private var autoResearch = Core.settings.getBool("autoresearch")

    init {
        Events.on(EventType.ClientLoadEvent::class.java) { // FINISHME: Do on the unimportant work thread.
            ui.research.checkNodes(ui.research.root)
            switchTree() // Setup initial tree
        }

        Events.on(EventType.TurnEvent::class.java) {
            if (!state.isCampaign || net.client()) return@on

            sectors = content.planets().sum { it.sectors.count(Sector::hasBase) } // FINISHME: Change to or add per planet sector capture count. Also add uncaptured sectors and total sectors?

            queue.copy().each<TechTreeNode>(ui.research.nodes::contains) { spend(it.node) } // Terrible way to handle multiple planets I know.

            // Run until no new nodes are unlocked
            var any = autoResearch
            if (any) ui.research.checkNodes(ui.research.root) // Set visibility for each node (needed in case the research dialog hasn't been opened yet)
            while (any) {
                any = ui.research.nodes.any { it.visible && it.node.content.locked() && ui.research.view.canSpend(it.node) && spend(it.node) }
            }
        }
    }

    fun queue(node: TechTreeNode, tail: Boolean = true) {
        if (queue.contains(node)) dequeue(node) // Requeue the node
        if (node.node.objectives.contains { !it.complete() }) return // Requirements not met

        if (tail) queue.add(node) else queue.insert(0, node)
        updateQueue()
    }

    fun dequeue(node: TechTreeNode) {
        queue.remove(node)
        updateQueue()
    }

    fun dequeue(node: TechNode) { // This is super hacky
        dequeue(queue.find { it.node == node } ?: return)
    }

    /** Called when the tech tree switches */
    fun switchTree() {
        Time.mark()
        queue.clear()
        @Suppress("UNCHECKED_CAST")
        (Core.settings.getJson("autoresearchqueue-${ui.research.root.node.name}", Seq::class.java, UnlockableContent::class.java) { null } as? Seq<UnlockableContent>)?.each {
            val rNode = it.techNode ?: return@each
            queue.add(ui.research.nodes.find { n -> n.node == rNode } ?: return@each)
        }
        updateQueue(true)
        Log.debug("Research queue loaded in ${Time.elapsed()}")
    }

    /** Called when adding or removing queued research */
    fun updateQueue(switched: Boolean = false) {
        if (!switched) Core.settings.putJson("autoresearchqueue-${ui.research.root.node.name}", queue.map { it.node.content })

        top().right().clearChildren()
        defaults().right().top()

        check("@client.research.everything", autoResearch) { autoResearch = it }

        row()
        table {
            it.add(if (queue.isEmpty) "@client.research.queue" else "@client.research.queued")

            for (node in queue) it.button(node.node.icon()) { if (Core.input.shift()) queue(node, tail = false) else dequeue(node) }.size(48F).pad(5F)
        }

        row()
        label { "${Core.bundle.get("client.research.sectors")} $sectors" }.colspan(this.columns)
    }

    private fun spend(node: TechNode): Boolean {
        var complete = true
        for (sector in state.planet.sectors) {
            if (!sector.hasBase()) continue

            val items = sector.items()

            complete = true
            for (i in 0 until node.requirements.size) { // I don't know how slow indices is
                val req = node.requirements[i]
                val completed = node.finishedRequirements[i]
                if (completed.amount >= req.amount) continue
                val used = (req.amount - completed.amount).coerceAtMost(items.get(node.requirements[i].item) - 1000) // Actual used quantity
                if (used <= 0) {
                    complete = false
                    continue
                }
                sector.removeItem(req.item, used)
                completed.amount += used
                complete = complete && completed.amount >= req.amount
            }
            if (complete) break // Break early if research is already complete
        }
        if (complete) ui.research.view.unlock(node)
        node.save()
        return complete
    }
}
