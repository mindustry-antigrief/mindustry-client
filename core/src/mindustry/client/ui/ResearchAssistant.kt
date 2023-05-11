package mindustry.client.ui

import arc.*
import arc.scene.ui.layout.*
import arc.struct.*
import mindustry.Vars.*
import mindustry.content.TechTree.*
import mindustry.game.*
import mindustry.type.*
import mindustry.ui.dialogs.ResearchDialog.*

/** Handles various client behavior related to research in campaign */
object ResearchAssistant : Table() {
    private val queue = Seq<TechTreeNode>()
    private var sectors = content.planets().sum { it.sectors.count(Sector::hasBase) } // Owned sector count
    private var autoResearch = Core.settings.getBool("autoresearch")

    init {
        drawQueue()
        Events.on(EventType.TurnEvent::class.java) {
            if (state.isCampaign && !net.client()) {
                sectors = content.planets().sum { it.sectors.count(Sector::hasBase) } // FINISHME: Change to or add per planet sector capture count. Also add uncaptured sectors and total sectors?

                queue.each<TechTreeNode>(ui.research.nodes::contains) { spend(it.node) } // Terrible way to handle multiple planets I know.

                // Run until no new nodes are unlocked
                var any = autoResearch
                while (any) {
                    any = ui.research.nodes.any { it.visible && it.node.content.locked() && ui.research.view.canSpend(it.node) && spend(it.node) }
                }
            }
        }
    }

    fun queue(node: TechTreeNode) {
        if (queue.contains(node)) dequeue(node) // Requeue the node
        if (node.node.objectives.contains { !it.complete() }) return // Requirements not met

        queue.add(node)
        drawQueue()
    }

    fun dequeue(node: TechTreeNode) {
        queue.remove(node)
        drawQueue()
    }

    fun dequeue(node: TechNode) { // This is super hacky
        dequeue(queue.find { it.node == node } ?: return)
    }

    fun drawQueue() {
        top().right().clearChildren()
        defaults().right().top()

        check("@client.research", autoResearch) { autoResearch = it }

        row()
        table {
            it.add(if (queue.isEmpty) "@client.research.queue" else "@client.research.queued")

            for (node in queue) it.button(node.node.content.emoji()) { dequeue(node) }.pad(5F).disabled { !ui.research.nodes.contains(node) }
        }

        row()
        label { "${Core.bundle.get("client.research.sectors")} $sectors" }.colspan(this.columns)
    }

    private fun spend(node: TechNode): Boolean {
        for (sector in state.planet.sectors) {
            if (!sector.hasBase()) continue
            val items = sector.items()

            for (i in 0 until node.requirements.size) { // I don't know how slow indices is
                val req = node.requirements[i]
                val completed = node.finishedRequirements[i]
                if (completed.amount >= req.amount) continue
                val used = (req.amount - completed.amount).coerceAtMost(items.get(node.requirements[i].item) - 1000) // Actual used quantity
                if (used <= 0) continue
                sector.removeItem(req.item, used)
                completed.amount += used
            }
        }
        //disable completion if not all requirements are met
        val completed = node.requirements.withIndex().all { node.finishedRequirements[it.index].amount >= it.value.amount }
        if (completed) ui.research.view.unlock(node)
        node.save()
        return completed
    }
}
