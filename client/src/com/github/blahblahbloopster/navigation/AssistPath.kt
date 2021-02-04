package com.github.blahblahbloopster.navigation

import arc.math.geom.Position
import mindustry.client.navigation.Path
import mindustry.client.navigation.waypoints.PositionWaypoint
import mindustry.gen.Call
import mindustry.gen.Minerc
import mindustry.gen.Player
import kotlin.math.max
import mindustry.Vars.player

class AssistPath(private val assisting: Player?) : Path() {
    private var show: Boolean = true

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

        val tolerance = max(assisting.unit().hitSize * 1.5f, player.unit().hitSize * 1.5f)

        try {
            player.shooting(assisting.unit().isShooting) // Match shoot state
            player.unit().aim(assisting.unit().aimX(), assisting.unit().aimY()) // Match aim coordinates

            if ((assisting.unit().isShooting && player.unit().type.rotateShooting)) { // Rotate to aim position if needed, otherwise face assisted player
                player.unit().lookAt(assisting.unit().aimX(), assisting.unit().aimY())
            }
        } catch (ignored: Exception) {}
        PositionWaypoint(assisting.x, assisting.y, tolerance, tolerance).run()


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
            player.unit().clearBuilding()
            if (assisting.unit().activelyBuilding() && assisting.team() == player.team()) {
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
