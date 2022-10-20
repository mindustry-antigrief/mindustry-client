package mindustry.client

import arc.*
import mindustry.*
import mindustry.ai.*
import mindustry.client.utils.*
import mindustry.content.*
import mindustry.entities.*
import mindustry.gen.*
import mindustry.input.*
import mindustry.world.blocks.*
import mindustry.world.blocks.defense.turrets.*
import mindustry.world.blocks.power.*
import mindustry.world.blocks.sandbox.LiquidSource.*

private var target: Teamc? = null
private var hadTarget = false

fun autoShoot() {
    if (!Core.settings.getBool("autotarget") || Vars.state.isMenu || Vars.state.isEditor) return
    val unit = Vars.player.unit()
    if (((unit as? BlockUnitUnit)?.tile() as? ControlBlock)?.shouldAutoTarget() == false) return
    if (unit.activelyBuilding()) return
    if (unit.mining()) return
    val type = unit.type ?: return
    val targetBuild = target as? Building
    val validHealTarget = Vars.player.unit().type.canHeal && targetBuild?.isValid == true && target?.team() == unit.team && targetBuild.damaged() && target?.within(unit, unit.range()) == true

    if ((hadTarget && target == null || target != null && Units.invalidateTarget(target, unit, unit.range())) && !validHealTarget) { // Invalidate target
        val desktopInput = Vars.control.input as? DesktopInput
        Vars.player.shooting = Core.input.keyDown(Binding.select) && !Core.scene.hasMouse() && (desktopInput == null || desktopInput.shouldShoot)
        target = null
        hadTarget = false
    }

    if (target == null || Client.timer.get(2, 6f)) { // Acquire target FINISHME: Heal allied units?
        if (type.canAttack) target = Units.closestEnemy(unit.team, unit.x, unit.y, unit.range()) { u -> u.checkTarget(type.targetAir, unit.type.targetGround) }
        if (type.canHeal && target == null) {
            target = Units.findDamagedTile(Vars.player.team(), Vars.player.x, Vars.player.y)
            if (target != null && !unit.within(target, if (type.hasWeapons()) unit.range() + 4 + (target as Building).hitSize()/2f else 0f)) target = null
        }
        val flood = flood()
        if (target == null && (type == UnitTypes.block || type.canAttack) && flood) { // Shoot buildings in flood because why not.
            target = Units.findEnemyTile(Vars.player.team(), Vars.player.x, Vars.player.y, unit.range()) { type.targetGround }
        }
        if (!flood && (unit as? BlockUnitc)?.tile()?.block == Blocks.foreshadow) {
            val amount = unit.range() * 2 + 1
            var closestScore = Float.POSITIVE_INFINITY

            circle(Vars.player.tileX(), Vars.player.tileY(), unit.range()) { tile ->
                tile ?: return@circle
                if (!tile.team().isEnemy(Vars.player.team())) return@circle
                val block = tile.block()
                val scoreMul = when {
                    // do NOT shoot power voided networks
                    (tile.build?.power?.graph?.getPowerBalance() ?: 0f) <= -1e12f -> Float.POSITIVE_INFINITY
                    // otherwise nodes are good to shoot
                    block == Blocks.powerSource -> 0f
                    block is PowerNode -> if (tile.build.power.status < .9) 2f else 1f

                    block == Blocks.liquidSource && (tile.build as LiquidSourceBuild).config() == Liquids.oil -> 1f
                    block == Blocks.itemSource -> 2f
                    block == Blocks.liquidSource -> 3f  // lower priority because things generally don't need liquid to run

                    // likely to be touching a turret or something
                    block == Blocks.router -> 4f
                    block == Blocks.overflowGate -> 4f
                    block == Blocks.underflowGate -> 4f
                    block == Blocks.sorter -> 4f
                    block == Blocks.invertedSorter -> 4f

                    block == Blocks.liquidRouter -> 5f

                    block == Blocks.mendProjector -> 6f
                    block == Blocks.forceProjector -> 6f
                    block is PointDefenseTurret -> 6f

                    block is BaseTurret -> 7f

                    block != Blocks.air -> 9f
                    else -> 10f
                }

                var score = Astar.manhattan.cost(tile.x.toInt(), tile.y.toInt(), Vars.player.tileX(), Vars.player.tileY())
                score += scoreMul * amount * if (tile.build?.proximity?.contains { it is BaseTurret.BaseTurretBuild } == true) 1F else 1.3F

                if (score < closestScore) {
                    target = tile.build
                    closestScore = score
                }
            }
        }
    }

    if (target != null) { // Shoot at target
        val intercept = if (type.weapons.contains { !it.predictTarget }) target!! else Predict.intercept(unit, target, if (type.hasWeapons()) type.weapons.first().bullet.speed else 0f)
        val boosting = unit is Mechc && unit.isFlying()

        Vars.player.mouseX = intercept.x
        Vars.player.mouseY = intercept.y
        Vars.player.shooting = !boosting

        if (type.omniMovement && Vars.player.shooting && type.hasWeapons() && type.faceTarget && !boosting) { // Rotate towards enemy
            unit.lookAt(unit.angleTo(Vars.player.mouseX, Vars.player.mouseY))
        }

        unit.aim(Vars.player.mouseX, Vars.player.mouseY)
        hadTarget = true
    }
}
