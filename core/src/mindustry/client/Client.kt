package mindustry.client

import arc.*
import arc.graphics.*
import arc.graphics.g2d.*
import arc.math.*
import arc.math.geom.*
import arc.util.*
import mindustry.Vars.*
import mindustry.ai.*
import mindustry.client.ClientVars.*
import mindustry.client.antigrief.*
import mindustry.client.crypto.*
import mindustry.client.navigation.*
import mindustry.client.navigation.Navigation.getTree
import mindustry.client.navigation.Navigation.getAllyTree
import mindustry.client.utils.*
import mindustry.content.*
import mindustry.game.*
import mindustry.gen.*
import mindustry.graphics.*
import mindustry.world.*
import mindustry.world.blocks.defense.turrets.*
import mindustry.world.blocks.distribution.MassDriver
import org.bouncycastle.jce.provider.*
import org.bouncycastle.jsse.provider.*
import java.security.*

object Client {
    var leaves: Moderation? = Moderation()
    val tiles = mutableListOf<Tile>()
    val timer = Interval(4)
    val autoTransfer by lazy { AutoTransfer() } // FINISHME: Awful
//    val kts by lazy { ScriptEngineManager().getEngineByExtension("kts") }

    private val massDriverGreen: Color = Color(Color.green).a(0.7f)
    private val massDriverYellow: Color = Color(Color.yellow).a(0.7f)


    fun initialize() {
        setup()
        AutoTransfer.init()
        ClientLogic()

        val bc = BouncyCastleProvider()
        // append bouncycastle to the list
        val n = Security.getProviders().contentToString().length
        Security.insertProviderAt(bc, n)
        Security.insertProviderAt(BouncyCastleJsseProvider(bc), n + 1)
        provider.setProvider(bc)
        // FINISHME is this secure?  what exactly does this mean?  test without this every so often with new bouncycastle versions
        System.setProperty("jdk.tls.namedGroups", "secp256r1")
    }

    fun update() {
        autoTransfer.update()
        Seer.update()
        Navigation.update()
        PowerInfo.update()
        Spectate.update() // FINISHME: Why is spectate its own class? Move it here, no method is needed just add an `if` like below
        Core.camera.bounds(cameraBounds) // do we do this here or on draw? can Core.camera be null?
        cameraBounds.grow(2 * tilesizeF)

        // Ratelimit reset handling
        if (ratelimitRemaining != ratelimitMax && timer.get(3, ratelimitSeconds * 60F)) ratelimitRemaining = ratelimitMax

        if (!configs.isEmpty()) {
            try {
                if (ratelimitRemaining > 0 || !net.client()) { // Run the config NOTE: Counter decremented in InputHandler and not here so that manual configs don't cause issues
                    configs.poll().run()
                }
            } catch (e: Exception) {
                Log.err(e)
            }
        }

        if (state?.rules?.editor == true) ui.editor.autoSave()
    }

    fun draw() {
        Navigation.draw()
        Spectate.draw()
        autoTransfer.draw()

        // Spawn path
        if (spawnTime < 0 && spawner.spawns.size < 50) { // FINISHME: Repetitive code, squash down
            Draw.color(state.rules.waveTeam.color)
            for (i in 0 until spawner.spawns.size) {
                var target: Tile? = spawner.spawns[i]
                Lines.beginLine()
                while(target != pathfinder.getTargetTile(target, pathfinder.getField(state.rules.waveTeam, Pathfinder.costGround, Pathfinder.fieldCore)).also { target = it }) {
                    Lines.linePoint(target)
                }
                Lines.endLine()
            }
        } else if (spawnTime != 0f && travelTime != 0f && spawner.spawns.size < 50 && timer.get(0, travelTime)) {
            if (timer.get(1, spawnTime)) tiles.addAll(spawner.spawns)
            for (i in 0 until tiles.size) {
                val t = tiles.removeFirst()
                val target = pathfinder.getTargetTile(t, pathfinder.getField(state.rules.waveTeam, Pathfinder.costGround, Pathfinder.fieldCore))
                if (target != t) tiles.add(target)
                Fx.healBlock.at(t.worldx(), t.worldy(), 1f, state.rules.waveTeam.color)
            }
        }

        // Turret range
        val bounds = Core.camera.bounds(Tmp.r3).grow(tilesize.toFloat())
        if (showingTurrets || showingInvTurrets || showingAllyTurrets) {
            val enemyunits = Core.settings.getBool("enemyunitranges")
            val allyunits = Core.settings.getBool("allyunitranges")
            if (showingTurrets || showingInvTurrets) {
                val flying = player.unit().isFlying
                getTree().intersect(bounds) {
                    if (!fogControl.isVisible(player.team(), it.x(), it.y())) return@intersect
                    if ((enemyunits || it.turret) && it.canShoot() && (it.targetAir || it.targetGround)) {//circles.add(it to if (it.canHitPlayer()) it.entity.team().color else Team.derelict.color)
                        val valid = (flying && it.targetAir) || (!flying && it.targetGround)
                        val validInv = (!flying && it.targetAir) || (flying && it.targetGround)
                        Drawf.dashCircle(
                            it.entity.x, it.entity.y, it.range - tilesize,
                            if ((valid && showingTurrets) || (validInv && showingInvTurrets)) it.entity.team().color else Team.derelict.color)
                    }
                }
            }
            if (showingAllyTurrets) {
                getAllyTree().intersect(bounds) {
                    if ((allyunits || it.turret) && it.canShoot() && (it.targetAir || it.targetGround)) {
                        Drawf.dashCircle(it.entity.x, it.entity.y, it.range - tilesize, it.entity.team().color)
                    }
                }
            }
        }

        // Player controlled turret range
        if ((player.unit() as? BlockUnitUnit)?.tile() is BaseTurret.BaseTurretBuild) {
            Drawf.dashCircle(player.x, player.y, player.unit().range(), player.team().color)
        }

        // Overdrive range
        if (showingOverdrives) {
            val team = player.team()
            overdrives.forEach { b ->
                if (b.team != team) return@forEach
                val range = b.realRange()
                if (bounds.overlaps(b.x - range, b.y - range, range * 2, range * 2)) b.drawSelect()
            }
        }

        // Mass driver config
        if (showingMassDrivers) {
            val progress = (Time.globalTime % 80f) / 60f // 1 second arrow time with 0.333 second pause
            bounds.grow(tilesizeF * Blocks.massDriver.size - tilesizeF) // grow bounds to accommodate for entire mass driver
            val aS = Mathf.clamp(bounds.width / 100f, 8f, 20f) // arrow size
            massDrivers.forEach { b ->
                if (!b.linkValid()) return@forEach
                val to = world.tile(b.link).build as? MassDriver.MassDriverBuild ?: return@forEach
                if ((bounds.contains(b.x, b.y) && bounds.contains(to.x, to.y)) || Intersector.intersectSegmentRectangle(b.x, b.y, to.x, to.y, bounds)) {
                    //val maxTime = (Mathf.dst(b.x, b.y, to.x, to.y) / 5.5f).coerceAtLeast(90f) // 5.5 is from MassDriver.java - projectile speed. // 90f is min 1.5 seconds
                    //val progress = (Time.globalTime % maxTime) / maxTime
                    // nah that looks bad
                    val toBlock = to.block as MassDriver
                    Lines.stroke(1.5f, if (to.state === MassDriver.DriverState.idle && toBlock.itemCapacity - to.items.total() < toBlock.minDistribute) massDriverGreen else massDriverYellow)
                    Lines.line(b.x, b.y, to.x, to.y)
                    // TODO: change color according to item type? or is that too inconsistent
                    if (progress > 1f) return@forEach
                    val ax = Mathf.lerp(b.x, to.x, progress)
                    val ay = Mathf.lerp(b.y, to.y, progress)
                    if (bounds.contains(ax, ay))
                        Tex.logicNode.draw(ax-aS/2, ay-aS/2, aS/2, aS/2, aS, aS, 1f, 1f, Mathf.angle(to.x - b.x, to.y - b.y))
                }
            }
            Draw.reset()
            bounds.grow(-tilesizeF * Blocks.massDriver.size + tilesizeF)
        }
    }
}
