package mindustry.client.navigation

import arc.*
import arc.math.*
import arc.math.geom.*
import arc.struct.*
import arc.util.*
import mindustry.Vars.*
import mindustry.client.*
import mindustry.client.ClientVars.*
import mindustry.client.communication.*
import mindustry.entities.units.*
import mindustry.game.*
import mindustry.gen.*
import mindustry.input.*

class AssistPath(val assisting: Player?, private val cursor: Boolean = false, private val noFollow: Boolean = false) : Path() {
    private var show: Boolean = true
    private var plans = Seq<BuildPlan>()
    private var tolerance = 0F

    companion object { // Events.remove is weird, so we just create the hooks once instead
        init {
            Events.on(EventType.DepositEvent::class.java) {
                val assisting = (Navigation.currentlyFollowing as? AssistPath)?.assisting ?: return@on
                if (it.player != assisting || ratelimitRemaining <= 1) return@on
                ratelimitRemaining--
                Call.transferInventory(player, it.tile)
            }
            Events.on(EventType.WithdrawEvent::class.java) {
                val assisting = (Navigation.currentlyFollowing as? AssistPath)?.assisting ?: return@on
                if (it.player != assisting || ratelimitRemaining <= 1) return@on
                ratelimitRemaining--
                Call.requestItem(player, it.tile, it.item, it.amount)
            }
        }
    }

    override fun reset() {}

    override fun setShow(show: Boolean) {
        this.show = show
    }

    override fun getShow() = show

    override fun follow() {
        if (player?.dead() != false) return
        assisting?.unit() ?: return // We don't care if they are dead

        tolerance = assisting.unit().hitSize * Core.settings.getFloat("assistdistance", 1.5f) // FINISHME: Factor in formations

        handleInput()

        if (player.unit() is Minerc && assisting.unit() is Minerc) { // Code stolen from formationAi.java, matches player mine state to assisting
            val mine = player.unit() as Minerc
            val com = assisting.unit() as Minerc
            if (com.mineTile() != null && mine.validMine(com.mineTile())) {
                mine.mineTile(com.mineTile())

                val core = player.unit().team.core()

                if (core != null && com.mineTile().drop() != null && player.unit().within(core, player.unit().type.range) && !player.unit().acceptsItem(com.mineTile().drop())) {
                    if (core.acceptStack(player.unit().stack.item, player.unit().stack.amount, player.unit()) > 0) {
                        Call.transferInventory(player, core)

                        player.unit().clearItem()
                    }
                }
            } else {
                mine.mineTile(null)
            }
        }

        if (assisting.isBuilder && player.isBuilder) {
            if (assisting.unit().updateBuilding && assisting.team() == player.team()) {
                plans.forEach { player.unit().removeBuild(it.x, it.y, it.breaking) }
                plans.clear()
                for (plan in assisting.unit().plans) {
                    if (BuildPlanCommunicationSystem.isNetworking(plan)) continue
                    plans.add(plan)
                    player.unit().addBuild(plan, false)
                }
            }
        }
    }

    private fun handleInput() {
        if (player?.dead() != false) return
        assisting?.unit() ?: return // We don't care if they are dead

        val unit = player.unit()
        val shouldShoot =
            assisting.unit().isShooting || // Target shooting
            noFollow && player.shooting && Core.input.keyDown(Binding.select) // Player not following and shooting
        val aimPos =
            if (!noFollow || assisting.unit().isShooting) Tmp.v1.set(assisting.unit().aimX, assisting.unit().aimY) // Following or shooting
            else if (unit.type.faceTarget) Core.input.mouseWorld() else Tmp.v1.trns(unit.rotation, Core.input.mouseWorld().dst(unit)).add(unit.x, player.unit().y) // Not following, not shooting
        val lookPos =
            if (assisting.unit().isShooting && unit.type.rotateShooting) player.angleTo(assisting.unit().aimX, assisting.unit().aimY) // Assisting is shooting and player has fixed weapons
            else if (unit.type.omniMovement && player.shooting && unit.type.hasWeapons() && unit.type.faceTarget && !(unit is Mechc && unit.isFlying()) && unit.type.rotateShooting) Angles.mouseAngle(unit.x, unit.y);
            else player.unit().prefRotation() // Anything else

        player.shooting(shouldShoot)
        player.unit().isShooting()
        unit.aim(aimPos)
        unit.lookAt(lookPos)

        if (!noFollow) { // Following
            goTo(if (cursor) assisting.mouseX else assisting.x, if (cursor) assisting.mouseY else assisting.y, tolerance, tolerance + tilesize * 5)
        } else { // Not following
            player.unit().moveAt((control.input as? DesktopInput)?.movement ?: (control.input as MobileInput).movement)
        }
    }

    override fun draw() {
        assisting ?: return
        if (!noFollow && player.dst(if (cursor) Tmp.v1.set(assisting.mouseX, assisting.mouseY) else assisting) > tolerance + tilesize * 5) waypoints.draw()

        if (Spectate.pos != assisting) assisting.unit().drawBuildPlans() // Don't draw plans twice
    }

    override fun progress(): Float {
        return if (assisting == null || !assisting.added) 1f else 0f
    }

    override fun next(): Position? {
        return null
    }
}
