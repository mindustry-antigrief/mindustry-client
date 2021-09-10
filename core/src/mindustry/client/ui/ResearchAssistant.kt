package mindustry.client.ui

import arc.*
import arc.scene.ui.layout.*
import arc.struct.*
import mindustry.Vars.*
import mindustry.content.TechTree.*
import mindustry.game.*

/** Handles various client behavior related to research in campaign */
object ResearchAssistant: Table() {
    val queue: Seq<TechNode> = Seq()
    var sectors = 0

    init {
        content.planets().each { p -> p.sectors.each { s -> if (s.hasBase()) sectors++ } } // Owned sector count
        drawQueue()
        Events.on(EventType.TurnEvent::class.java) {
            if (state.isCampaign) {
                sectors = 0
                content.planets().each { p -> p.sectors.each { s -> if (s.hasBase()) sectors++ } } // Owned sector count

                for (node in queue) spend(node)
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
        add(if (queue.isEmpty) "Shift + Click to Queue Research" else "Research Queue:")

        for (node in queue) button(node.content.emoji()) { dequeue(node) }.pad(5F)

        row()
        add("Sectors Captured: $sectors").colspan(this.columns).right()
    }

    fun spend(node: TechNode) {
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
                    ui.research.items[req.item] -= used
                    completed.amount += used
                    if (used > 0) {
                        shine[i] = true
                        usedShine[req.item.id.toInt()] = true
                    }
                }
            }
        }
        //disable completion if not all requirements are met
        var complete = true
        node.requirements.forEachIndexed {i, _ ->  if (node.finishedRequirements[i].amount < node.requirements[i].amount) complete = false}
        if (complete) ui.research.view.unlock(node)
        node.save()

        //??????
        Core.scene.act()
        ui.research.view.rebuild(shine)
        ui.research.itemDisplay.rebuild(ui.research.items, usedShine)
    }
}