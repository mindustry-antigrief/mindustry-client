package mindustry.client.navigation

import arc.*
import arc.math.geom.*
import arc.struct.*
import mindustry.Vars.*
import mindustry.entities.units.*
import mindustry.gen.*

class AssistPath(val assisting: Player?, val cursor: Boolean) : Path() {
    constructor(assisting: Player?) : this(assisting, false)
    private var show: Boolean = true
    private var plans = Seq<BuildPlan>()

    override fun reset() {}

    override fun setShow(show: Boolean) {
        this.show = show
    }

    override fun getShow() = show

    override fun follow() {
        assisting ?: return
        player ?: return
        assisting.unit() ?: return
        player.unit() ?: return

        val tolerance = assisting.unit().hitSize * Core.settings.getFloat("assistdistance", 1.5f)

        try {
            player.shooting(assisting.unit().isShooting) // Match shoot state
            player.unit().aim(assisting.unit().aimX(), assisting.unit().aimY()) // Match aim coordinates

            if ((assisting.unit().isShooting && player.unit().type.rotateShooting)) { // Rotate to aim position if needed, otherwise face assisted player
                player.unit().lookAt(assisting.unit().aimX(), assisting.unit().aimY())
            }
        } catch (ignored: Exception) {}
        waypoint.set(if (cursor) assisting.mouseX else assisting.x, if (cursor) assisting.mouseY else assisting.y, tolerance, tolerance).run()


        if (player.unit() is Minerc && assisting.unit() is Minerc) { // Code stolen from formationAi.java, matches player mine state to assisting
            val mine = player.unit() as Minerc
            val com = assisting.unit() as Minerc
            if (com.mineTile() != null && mine.validMine(com.mineTile())) {
                mine.mineTile(com.mineTile())

                val core = player.unit().team.core()

                if (core != null && com.mineTile().drop() != null && player.unit().within(core, player.unit().type.range) && !player.unit().acceptsItem(com.mineTile().drop())) {
                    if (core.acceptStack(player.unit().stack.item, player.unit().stack.amount, player.unit()) > 0) {
                        Call.transferItemTo(player.unit(), player.unit().stack.item, player.unit().stack.amount, player.unit().x, player.unit().y, core)

                        player.unit().clearItem()
                    }
                }
            } else {
                mine.mineTile(null)
            }
        }

        if (assisting.isBuilder && player.isBuilder) {
            if (assisting.unit().activelyBuilding() && assisting.team() == player.team()) {
                plans.forEach { player.unit().removeBuild(it.x, it.y, it.breaking) }
                plans.clear()
                plans.addAll(assisting.unit().plans())
                assisting.unit().plans().forEach { player.unit().addBuild(it, false) }
            }
        }
    }

    override fun progress(): Float {
        return if (assisting == null) 1f else 0f
    }

    override fun next(): Position? {
        return null
    }
}
