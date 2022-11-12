package mindustry.client.navigation

import arc.*
import arc.math.*
import arc.math.geom.*
import arc.struct.*
import arc.util.*
import mindustry.Vars.*
import mindustry.client.*
import mindustry.client.ClientVars.*;
import mindustry.client.communication.*
import mindustry.entities.units.*
import mindustry.game.*
import mindustry.gen.*
import mindustry.input.*

class AssistPath(val assisting: Player?, val type: Type = Type.Regular, var circling: Boolean = false) : Path() {
    private var show: Boolean = true
    private var plans = Seq<BuildPlan>()
    private var tolerance = 0F
    private var aStarTolerance = 0F
    private var buildPath: BuildPath? = if (type == Type.BuildPath) BuildPath.Self() else null
    private var theta: Float = 0F
    private var circleRadius: Float = 0F

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

        if (circling) {
            theta += Core.settings.getFloat("circleassistspeed", 0f)
            theta %= (Math.PI * 2).toFloat()
        }

        aStarTolerance = assisting.unit().hitSize * Core.settings.getFloat("assistdistance", 1.5f) + tilesize * 5;
        tolerance = if(circling) 0.1f else assisting.unit().hitSize * Core.settings.getFloat("assistdistance", 1.5f)
        circleRadius = if(circling) assisting.unit().hitSize * Core.settings.getFloat("assistdistance", 1.5f) else 0f

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

        if (assisting.isBuilder && player.isBuilder /* && build */) {
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
            type != Type.BuildPath &&
            (assisting.unit().isShooting || // Target shooting
            player.shooting && Core.input.keyDown(Binding.select)) // Player not following and shooting
        val aimPos =
            if ((type == Type.Regular || type == Type.Cursor) && assisting.unit().isShooting) Tmp.v1.set(assisting.unit().aimX, assisting.unit().aimY) // Following or shooting
            else if (unit.type.faceTarget) Core.input.mouseWorld() else Tmp.v1.trns(unit.rotation, Core.input.mouseWorld().dst(unit)).add(unit.x, player.unit().y) // Not following, not shooting
        val lookPos =
            if (assisting.unit().isShooting && unit.type.faceTarget) player.angleTo(assisting.unit().aimX, assisting.unit().aimY) // Assisting is shooting and player has fixed weapons
            else if (unit.type.omniMovement && player.shooting && unit.type.hasWeapons() && unit.type.faceTarget && !(unit is Mechc && unit.isFlying())) Angles.mouseAngle(unit.x, unit.y);
            else player.unit().prefRotation() // Anything else

        player.shooting(shouldShoot)
        player.unit().isShooting()
        unit.aim(aimPos)
        unit.lookAt(lookPos)

        when (type) {
            Type.Regular -> goTo(assisting.x + (circleRadius * Math.cos(theta.toDouble())).toFloat(), assisting.y + (circleRadius * Math.sin(theta.toDouble())).toFloat(), tolerance, aStarTolerance + tilesize * 5)
            Type.FreeMove -> {
                val input = control.input
                if (input is DesktopInput) {
                    if (input.movement.epsilonEquals(0f, 0f)) {
                        if (Core.settings.getBool("zerodrift")) unit.vel.setZero()
                        else if (Core.settings.getBool("decreasedrift") && unit.vel().len() > 3.5) unit.vel.set(unit.vel().scl(0.95f));
                    }
                    else player.unit().moveAt(input.movement)
                } else player.unit().moveAt((input as MobileInput).movement)
            }
            Type.Cursor -> goTo(assisting.mouseX + (circleRadius * Math.cos(theta.toDouble())).toFloat(), assisting.mouseY + (circleRadius * Math.sin(theta.toDouble())).toFloat(), tolerance, tolerance + tilesize * 5)
            Type.BuildPath -> if (!plans.isEmpty) buildPath?.follow() else goTo(assisting.x + (circleRadius * Math.cos(theta.toDouble())).toFloat(), assisting.y + (circleRadius * Math.sin(theta.toDouble())).toFloat(), tolerance, aStarTolerance + tilesize * 5) // Follow build path if plans exist, otherwise follow player
        }
    }

    override fun draw() {
        assisting ?: return
        if (type != Type.FreeMove && player.dst(assisting) > aStarTolerance) waypoints.draw()

        if (Spectate.pos != assisting) assisting.unit().drawBuildPlans() // Don't draw plans twice
    }

    override fun progress(): Float {
        return if (assisting == null || !assisting.isAdded) 1f else 0f
    }

    override fun next(): Position? {
        return null
    }

    enum class Type {
        Regular,
        FreeMove,
        Cursor,
        BuildPath,
    }
}
