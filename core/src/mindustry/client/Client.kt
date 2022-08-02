package mindustry.client

import arc.*
import arc.graphics.g2d.*
import arc.util.*
import mindustry.Vars.*
import mindustry.ai.*
import mindustry.client.ClientVars.*
import mindustry.client.antigrief.*
import mindustry.client.crypto.*
import mindustry.client.navigation.*
import mindustry.client.navigation.Navigation.getAllyTree
import mindustry.client.navigation.Navigation.getTree
import mindustry.client.utils.*
import mindustry.content.*
import mindustry.game.*
import mindustry.gen.*
import mindustry.graphics.*
import mindustry.net.*
import mindustry.world.*
import mindustry.world.blocks.defense.turrets.*
import org.bouncycastle.jce.provider.*
import org.bouncycastle.jsse.provider.*
import java.security.*

object Client {
    var leaves: Moderation? = Moderation()
    val tiles = mutableListOf<Tile>()
    val timer = Interval(4)
    val autoTransfer by lazy { AutoTransfer() } // FINISHME: Awful

    fun initialize() {
        setup()
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
        Navigation.update()
        PowerInfo.update()
        Spectate.update() // FINISHME: Why is spectate its own class? Move it here, no method is needed just add an `if` like below
        Core.camera.bounds(cameraBounds) // do we do this here or on draw? can Core.camera be null?
        cameraBounds.grow(2 * tilesizeF)

        if (ratelimitRemaining != Administration.Config.interactRateLimit.num() - 1 && timer.get(3, (Administration.Config.interactRateWindow.num() + 1) * 60F)) { // Reset ratelimit, extra second to account for server lag
            ratelimitRemaining = Administration.Config.interactRateLimit.num() - 1
        }

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
    }
}
