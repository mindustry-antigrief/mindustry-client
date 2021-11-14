package mindustry.client.communication

import arc.*
import arc.util.*
import mindustry.*
import mindustry.client.utils.*
import mindustry.content.*
import mindustry.entities.units.*
import mindustry.game.*
import mindustry.gen.*
import kotlin.collections.set
import kotlin.random.*

object BuildPlanCommunicationSystem : CommunicationSystem() {
    override val listeners: MutableList<(input: ByteArray, sender: Int) -> Unit> = mutableListOf()
    override val id get() = Vars.player.id
    override val MAX_LENGTH get() = 512
    override val RATE = 30f // up to twice a second
    private const val PREFIX = "end\nprint \"gwiogrwog\"\nprint \"%s\"\n"
    private const val MAX_PRINT_LENGTH = 34

    private val lastGotten = mutableMapOf<Int, Int>()
    private val corners get() = listOf(
        Vars.world.tiles.get(0, 0),
        Vars.world.tiles.get(0, Vars.world.height() - 1),
        Vars.world.tiles.getc(Vars.world.width() - 1, 0),
        Vars.world.tiles.getc(Vars.world.width() - 1, Vars.world.height() - 1)
    )

    private fun findLocation() = corners.maxByOrNull { Vars.player.dst(it) }!!

    fun isNetworking(plan: BuildPlan) = plan.block == Blocks.microProcessor && plan.tile() in corners

    init {
        Events.on(EventType.WorldLoadEvent::class.java) {
            lastGotten.clear()
        }

        val re = ("\\A$PREFIX").replace("%s", "-?\\d+").replace("\n", "\\n").toRegex()
        Timer.schedule({
            Core.app.post { // don't do async for thread safety
                val start = Time.millis()
                for (p in Groups.player) {
                    val plan = p.unit()?.plans?.find { it.block == Blocks.microProcessor && (it.config as? String)?.run { re.containsMatchIn(this) } == true }
                    if (plan == null || lastGotten[p.id] == plan.config?.hashCode()) continue

                    lastGotten[p.id] = plan.config.hashCode()
                    val config = re.replace(plan.config as String, "").lines().joinToString("") { it.removeSurrounding("print \"", "\"") }
                    val decoded = config.base32678()
                    decoded ?: return@post
                    listeners.forEach { it(decoded, p.id) }
                }
                val time = Time.timeSinceMillis(start)
                if (time > 50) Log.debug("Scanning players took $time ms, this is a problem")
            }
        }, 0.2f, 0.2f)
    }

    override fun send(bytes: ByteArray) {
        val config = bytes.base32678().chunked(MAX_PRINT_LENGTH).joinToString("\n", prefix = PREFIX.format(Random.nextLong())) { "print \"$it\"" }
        val tile = findLocation()
        val plan = BuildPlan(tile.x.toInt(), tile.y.toInt(), 0, Blocks.microProcessor, config)
        // null unless the block is too close in which case we store the current build state, pause it and then re-enable it if needed later
        val state = if (Vars.player.dst(plan) > Vars.buildingRange) null else Vars.control.input.isBuilding
        if (state == true) Vars.control.input.isBuilding = false
        Vars.player.unit().addBuild(plan, false)
        Timer.schedule({
            Core.app.post { // make sure it doesn't do this while something else is iterating through the plans
                Vars.player.unit().plans.remove(plan)
                if (state == true) Vars.control.input.isBuilding = true
            }
        }, 0.25f)
    }
}