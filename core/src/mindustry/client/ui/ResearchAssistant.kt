package mindustry.client.ui

import arc.*
import arc.scene.ui.layout.*
import arc.struct.*
import mindustry.Vars.*
import mindustry.content.TechTree.*
import mindustry.game.*
import mindustry.type.*

/** Handles various client behavior related to research in campaign */
object ResearchAssistant: Table() {
    private val queue = Seq<TechNode>()
    private var sectors = content.planets().sum { it.sectors.count(Sector::hasBase) } // Owned sector count
    private var autoResearch = false

    init {
        drawQueue()
        Events.on(EventType.TurnEvent::class.java) {
            if (state.isCampaign && !net.client()) {
                sectors = content.planets().sum { it.sectors.count(Sector::hasBase) }

                queue.each { spend(it) }

                // Run until no new nodes are unlocked
                while (autoResearch && ui.research.nodes.any {
                    if (it.visible && ui.research.view.canSpend(it.node)) spend(it.node)
                    else false
                }) {}
            }
        }
    }

    fun queue(node: TechNode) {
        if (queue.contains(node)) dequeue(node) // Requeue the node
        if (node.objectives.contains { !it.complete() }) return // Requirements not met

        queue.add(node)
        drawQueue()
    }

    fun dequeue(node: TechNode) {
        queue.remove(node)
        drawQueue()
    }

    private fun drawQueue() {
        top().right().clearChildren()
        defaults().right().top()

        check("Automatically Research Everything (Queue is Prioritized)", autoResearch) { autoResearch = it } // FINISHME: Bundle

        row()
        table {
            it.add(if (queue.isEmpty) "Shift + Click to Queue Research" else "Research Queue:") // FINISHME: Bundle

            for (node in queue) it.button(node.content.emoji()) { dequeue(node) }.pad(5F)
        }

        row()
        add("Sectors Captured: $sectors").colspan(this.columns) // FINISHME: Bundle
    }

    fun spend(node: TechNode): Boolean {
        val shine = BooleanArray(node.requirements.size)
        val usedShine = BooleanArray(content.items().size)

        for (planet in content.planets()) {
            for (sector in planet.sectors) {
                if (!sector.hasBase()) continue

                for (i in node.requirements.indices) {
                    val req = node.requirements[i]
                    val completed = node.finishedRequirements[i]

                    //amount actually taken from inventory
                    val used = (req.amount - completed.amount).coerceAtMost(sector.items().get(req.item) - 1000).coerceAtLeast(0)
                    ui.research.items.total -= used
                    ui.research.items.values[req.item.id.toInt()] -= used
                    sector.removeItem(req.item, used)
                    completed.amount += used
                    if (used > 0) {
                        shine[i] = true
                        usedShine[req.item.id.toInt()] = true
                    }
                }
            }
        }
        //disable completion if not all requirements are met
        val completed = node.requirements.withIndex().all { node.finishedRequirements[it.index].amount >= it.value.amount }
        if (completed) ui.research.view.unlock(node)
        node.save()

        //??????
        Core.scene.act()
        ui.research.view.rebuild(shine)
        ui.research.itemDisplay.rebuild(ui.research.items, usedShine)
        return completed
    }
}