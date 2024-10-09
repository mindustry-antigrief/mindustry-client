package mindustry.client

import arc.*
import arc.graphics.*
import arc.graphics.g2d.*
import arc.math.*
import arc.math.geom.*
import arc.struct.*
import arc.util.*
import mindustry.Vars.*
import mindustry.ai.*
import mindustry.client.ClientVars.*
import mindustry.client.antigrief.*
import mindustry.client.claj.*
import mindustry.client.crypto.*
import mindustry.client.navigation.*
import mindustry.client.navigation.Navigation.getAllyTree
import mindustry.client.navigation.Navigation.getTree
import mindustry.client.utils.*
import mindustry.content.*
import mindustry.game.*
import mindustry.gen.*
import mindustry.graphics.*
import mindustry.world.*
import mindustry.world.blocks.defense.turrets.*
import mindustry.world.blocks.distribution.*
import mindustry.world.blocks.payloads.*
import mindustry.world.meta.*
import org.bouncycastle.jce.provider.*
import org.bouncycastle.jsse.provider.*
import java.security.*

object Client {
    var leaves: Moderation? = Moderation()
    val tiles = Seq<Tile>()
    /** Not actually tiles, instead Float pairs */
    val tilesFlying = FloatSeq()
    val tilesNaval = Seq<Tile>()
    val timer = Interval(4)
    val autoTransfer by lazy(::AutoTransfer) // FINISHME: Awful

    private val massDriverGreen: Color = Color.green.cpy().a(.7f)
    private val massDriverYellow: Color = Color.yellow.cpy().a(.7f)
    private val massDriverRed: Color = Color(Color.red).a(0.7f)


    fun initialize() {
        mainExecutor.execute(::setupCommands)
        AutoTransfer.init()
        ClientLogic()
        Server // Force the init block to be run
        CustomMode // Force the init block to be run

        val bc = BouncyCastleProvider()
        // append bouncycastle to the list
        val n = Security.getProviders().contentToString().length
        Security.insertProviderAt(bc, n)
        Security.insertProviderAt(BouncyCastleJsseProvider(bc), n + 1)
        provider.setProvider(bc)
        // FINISHME is this secure?  what exactly does this mean?  test without this every so often with new bouncycastle versions
        System.setProperty("jdk.tls.namedGroups", "secp256r1")

        ClajSupport.load()
    }

    fun update() {
        autoTransfer.update()
        Seer.update()
        Navigation.update()
        PowerInfo.update()
        Spectate.update() // FINISHME: Why is spectate its own class? Move it here, no method is needed just add an `if` like below

        // Ratelimit reset handling
        if (ratelimitRemaining != ratelimitMax && (!net.client() || timer.get(3, ratelimitSeconds * 60F))) ratelimitRemaining = ratelimitMax

        if (!configs.isEmpty()) {
            try {
                if (ratelimitRemaining > 1) configs.poll().run() // Run the config (leave 1 free config just in case)
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
            if (timer.get(1, 1 * 60F)) { // We still cache the start points as eachGroundSpawn is expensive. FINISHME: Should this actually use spawnTime?
                tiles.clear()
                tilesFlying.clear()
                spawner.eachGroundSpawn { x, y -> tiles.add(world.tile(x, y)) }
                spawner.eachFlyerSpawn(tilesFlying::add) // FINISHME: Add a path for each unit type? Also add this to the non line based path drawing
            }
            for (t in tiles) {
                var target = t
                val field = pathfinder.getField(state.rules.waveTeam, Pathfinder.costGround, Pathfinder.fieldCore)
                Lines.beginLine()
                Lines.linePoint(target)
                while(target != pathfinder.getTargetTile(target, field).also { target = it }) {
                    Lines.linePoint(target)
                }
                Lines.endLine()
            }

            Draw.alpha(.5F)
            for (i in 0..<tilesFlying.size step 2) { // Draw air paths FINISHME: Instead of direct path to core, we should check the types of all existing and upcoming units and draw a path for each of their main targets. See FlyingAI.findMainTarget
                val target = Geometry.findClosest(tilesFlying.get(i), tilesFlying.get(i+1), indexer.getEnemy(state.rules.waveTeam, BlockFlag.core))
                if (target != null) Lines.line(tilesFlying.get(i), tilesFlying.get(i+1), target.x, target.y)
            }
        } else if (spawnTime != 0f && travelTime != 0f && spawner.spawns.size < 50 && timer.get(0, travelTime)) {
            if (timer.get(1, spawnTime)) {
                spawner.eachGroundSpawn { x, y -> tiles.add(world.tile(x, y) ?: run { Log.debug("Invalid ground tile at $x $y"); return@eachGroundSpawn }) }
                spawner.eachGroundSpawn { x, y -> tilesNaval.add(world.tile(x, y) ?: run { Log.debug("Invalid naval tile at $x $y"); return@eachGroundSpawn }) }
            }
//            if (timer.get(1, spawnTime)) tiles.addAll(spawner.spawns)
            for (i in tiles.size - 1 downTo 0) {
                val t = tiles.get(i)
                val target = pathfinder.getTargetTile(t, pathfinder.getField(state.rules.waveTeam, Pathfinder.costGround, Pathfinder.fieldCore))
                if (target != t) tiles.set(i, target) else tiles.remove(i)
                Fx.healBlock.at(t.worldx(), t.worldy(), 1f, Team.crux.color)
            }

            for (i in tilesNaval.size - 1 downTo 0) {
                val t = tilesNaval.get(i)
                val target = pathfinder.getTargetTile(t, pathfinder.getField(state.rules.waveTeam, Pathfinder.costNaval, Pathfinder.fieldCore))
                if (target != t) tilesNaval.set(i, target) else tilesNaval.remove(i)
                Fx.healBlock.at(t.worldx(), t.worldy(), 1f, Team.blue.color)
            }
        }

        // Turret range
        val bounds = Core.camera.bounds(Tmp.r3).grow(tilesizeF)
        if (showingTurrets || showingInvTurrets || showingAllyTurrets) {
            if (showingTurrets || showingInvTurrets) {
                val enemyunits = Core.settings.getBool("enemyunitranges")
                val showall = Core.settings.getBool("showallturrets")
                val flying = player.unit().isFlying
                val mousev = Core.input.mouseWorld()
                val mouseBuild = world.buildWorld(mousev.x, mousev.y)
                getTree().use {
                    intersect(bounds) {
                        if (!(showall || fogControl.isDiscovered(player.team(), it.entity.tileX(), it.entity.tileY()))) return@intersect
                        if ((enemyunits || it.turret) && it.canShoot() && (it.targetAir || it.targetGround) && it.entity != mouseBuild) {//circles.add(it to if (it.canHitPlayer()) it.entity.team().color else Team.derelict.color)
                            val valid = (flying && it.targetAir) || (!flying && it.targetGround)
                            val validInv = (!flying && it.targetAir) || (flying && it.targetGround)
                            Drawf.dashCircle(
                                it.entity.x, it.entity.y, it.range(),
                                if ((valid && showingTurrets) || (validInv && showingInvTurrets)) it.entity.team().color else Team.derelict.color)
                        }
                    }
                }
            }
            if (showingAllyTurrets) {
                val allyunits = Core.settings.getBool("allyunitranges")
                getAllyTree().use {
                    intersect(bounds) {
                        if ((allyunits || it.turret) && it.canShoot() && (it.targetAir || it.targetGround)) {
                            Drawf.dashCircle(it.entity.x, it.entity.y, it.range(), it.entity.team().color)
                        }
                    }
                }
            }
        }

        // Player controlled turret range
        if ((player.unit() as? BlockUnitUnit)?.tile() is BaseTurret.BaseTurretBuild) {
            Drawf.dashCircle(player.x, player.y, player.unit().range(), player.team().color, Color.lightGray)
        }

        // Item transfer range FINISHME: Setting, bundle, do the same for build range(?)
        val transferOpacity = Core.settings.getInt("transferrangeopacity") / 100F
        if (transferOpacity > 0) {
            Lines.stroke(3f)
            Draw.color(Pal.gray, transferOpacity)
            Lines.circle(player.x, player.y, itemTransferRange)
            Lines.stroke(1f)
            Draw.color(player.team().color, transferOpacity)
            Lines.circle(player.x, player.y, itemTransferRange)
            Draw.reset()
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
        //Enemy spawners
        if (state.hasSpawns()) {
            val tiles = Seq<Tile>()
            spawner.spawns.forEach { s ->
                val intRad = Mathf.floor(state.rules.dropZoneRadius / tilesize)
                indexer.eachBlock(player.team(), s.worldx(), s.worldy(), tilesize * intRad.toFloat(), { true }) {
                    it.tile.getLinkedTiles(tiles)
                    if (tiles.contains { t -> Mathf.pow(t.x - s.x, 2) + Mathf.pow(t.y - s.y, 2) < intRad * intRad }) {
                        Drawf.selected(it, Tmp.c1.set(Color.red).a(Mathf.absin(4f, 1f)))
                    }
                }
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
                    Lines.stroke(1.5f,
                        if(b.efficiency <= 0f) massDriverRed
                        else if(b.state === MassDriver.DriverState.idle && toBlock.itemCapacity - to.items.total() < toBlock.minDistribute) massDriverYellow
                        else massDriverGreen
                    )
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
            // FINISHME: literally copypasted code
            bounds.grow(tilesizeF * Blocks.largePayloadMassDriver.size - tilesizeF) // grow bounds to accommodate for entire mass driver
            payloadMassDrivers.forEach { b ->
                if (!b.linkValid()) return@forEach
                val to = world.tile(b.link).build as? PayloadMassDriver.PayloadDriverBuild ?: return@forEach
                if ((bounds.contains(b.x, b.y) && bounds.contains(to.x, to.y)) || Intersector.intersectSegmentRectangle(b.x, b.y, to.x, to.y, bounds)) {
                    Lines.stroke(1.5f, if (to.state === PayloadMassDriver.PayloadDriverState.idle) massDriverYellow else massDriverGreen)
                    Lines.line(b.x, b.y, to.x, to.y)
                    if (progress > 1f) return@forEach
                    val ax = Mathf.lerp(b.x, to.x, progress)
                    val ay = Mathf.lerp(b.y, to.y, progress)
                    if (bounds.contains(ax, ay))
                        Tex.logicNode.draw(ax-aS/2, ay-aS/2, aS/2, aS/2, aS, aS, 1f, 1f, Mathf.angle(to.x - b.x, to.y - b.y))
                }
            }
            Draw.reset()
        }
    }
}
