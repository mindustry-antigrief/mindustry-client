package mindustry.client.communication

import arc.*
import arc.util.*
import mindustry.*
import mindustry.client.*
import mindustry.client.ui.*
import mindustry.client.utils.*
import mindustry.content.*
import mindustry.entities.units.*
import mindustry.game.*
import mindustry.gen.*
import mindustry.world.*
import kotlin.collections.set
import kotlin.random.*

object BuildPlanCommunicationSystem : CommunicationSystem() {
    override val listeners: MutableList<(input: ByteArray, sender: Int) -> Unit> = mutableListOf()
    override val id get() = Vars.player.id
    override val MAX_LENGTH get() = 512
    override val RATE = 30f // up to twice a second
    private const val PREFIX = "end\nprint \"gwiogrwog\"\nprint \"%s\"\n"

    private val lastGotten = mutableMapOf<Int, Int>()
    private lateinit var corners: Array<Tile>

    private fun findLocation() = corners.maxByOrNull { Vars.player.dst2(it) }!!

    fun isNetworking(plan: BuildPlan) = plan.block == Blocks.microProcessor && plan.tile() in corners

    init {
        Events.on(EventType.WorldLoadEvent::class.java) {
            lastGotten.clear()
            corners = arrayOf(
                Vars.world.tiles.get(0, 0),
                Vars.world.tiles.get(0, Vars.world.height() - 1),
                Vars.world.tiles.getc(Vars.world.width() - 1, 0),
                Vars.world.tiles.getc(Vars.world.width() - 1, Vars.world.height() - 1)
            )
        }

        val re = ("\\A$PREFIX").replace("%s", "-?\\d+").replace("\n", "\\n").toRegex()
        Timer.schedule({
            val start = Time.millis()
            for (p in Groups.player) {
                val plan = p.unit()?.plans?.find { it.block == Blocks.microProcessor && (it.config as? String)?.run { re.containsMatchIn(this) } == true }
                if (plan == null || plan.config !is String) continue
                val hash = plan.config.hashCode()
                if (lastGotten[p.id] == hash) continue

                lastGotten[p.id] = plan.config.hashCode()
                val config = re.replace(plan.config as String, "").lines().joinToString("") { it.removeSurrounding("print \"", "\"") }
                val decoded = config.base32768()
                decoded ?: return@schedule
                listeners.forEach { it(decoded, p.id) }
            }
            val time = Time.timeSinceMillis(start) // FINISHME: How is 50 ms acceptable?
            if (time > 50) Log.debug("Scanning players took $time ms, this is a problem")
        }, 0.2f, 0.2f)
    }

    override fun send(bytes: ByteArray) {
        if (!Vars.player.unit().canBuild()) {
            Toast(3f).add("[scarlet]Failed to send packet, build plan networking doesn't work if you can't build.")
            return
        }
//        val config = bytes.base32768().chunked(LAssembler.maxTokenLength - 2).joinToString("\n", prefix = PREFIX.format(Random.nextLong())) { "print \"$it\"" }
        val config = "${PREFIX.format(Random.nextLong())} print \"${bytes.base32768()}\""
        val tile = findLocation()
        val plan = BuildPlan(tile.x.toInt(), tile.y.toInt(), 0, Blocks.microProcessor, config)
        // Stores build state. Toggles building off as otherwise it can fail.
        val toggle = Vars.control.input.isBuilding
        Vars.control.input.isBuilding = false
        ClientVars.isBuildingLock = true
        Vars.player.unit().updateBuilding = false
        Vars.player.unit().addBuild(plan, false)
        Timer.schedule({
            Vars.player.unit().plans.remove(plan)
            Vars.control.input.isBuilding = toggle
            ClientVars.isBuildingLock = false
        }, 0.25f)
    }
}
