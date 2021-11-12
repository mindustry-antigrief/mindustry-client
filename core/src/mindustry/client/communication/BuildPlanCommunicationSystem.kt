package mindustry.client.communication

import arc.Core
import arc.Events
import arc.util.Timer
import mindustry.Vars
import mindustry.client.utils.base32678
import mindustry.content.Blocks
import mindustry.entities.units.BuildPlan
import mindustry.game.EventType
import mindustry.gen.Groups
import kotlin.random.Random

object BuildPlanCommunicationSystem : CommunicationSystem() {
    override val listeners: MutableList<(input: ByteArray, sender: Int) -> Unit> = mutableListOf()
    override val id get() = Vars.player.id
    override val MAX_LENGTH get() = 512
    override val RATE = 30f  // up to twice a second
    private const val PREFIX = "end\nprint \"gwiogrwog\"\nprint \"%s\"\n"
    private const val MAX_PRINT_LENGTH = 34

    private val lastGotten = mutableMapOf<Int, Int>()

    private fun findLocation() = /*Vars.world.tiles.firstOrNull { it.block() is StaticWall }*/ Vars.world.tile(0, 0)

    fun isNetworking(plan: BuildPlan) = plan.x == 0 && plan.y == 0 && plan.block == Blocks.microProcessor

    init {
        Events.on(EventType.WorldLoadEvent::class.java) {
            lastGotten.clear()
        }

        val re = PREFIX.replace("%s", "-?\\d+").replace("\n", "\\n").toRegex()
        Timer.schedule({
            Core.app.post {  // don't do async for thread safety
                val start = System.currentTimeMillis()
                for (p in Groups.player) {
                    val plan = p.unit()?.plans?.find { it.block == Blocks.microProcessor && (it.config as? String)?.run { re.containsMatchIn(this) } == true }
                    plan ?: continue
                    if (lastGotten[p.id] == plan.config?.hashCode()) {
                        continue
                    }
                    lastGotten[p.id] = plan.config.hashCode()
                    val config = re.replace(plan.config as String, "").lines().joinToString("") { it.removeSurrounding("print \"", "\"") }
                    val decoded = config.base32678()
                    decoded ?: return@post
                    listeners.forEach { it(decoded, p.id) }
                }
                val time = System.currentTimeMillis() - start
                if (time > 100) {
                    println("scanning players took $time ms, this is a problem")
                }
            }
        }, 0.2f, 0.2f)
    }

    override fun send(bytes: ByteArray) {
        val location = findLocation() ?: return
        val based = bytes.base32678()
        val config = based.chunked(MAX_PRINT_LENGTH).joinToString("\n", prefix = PREFIX.format(Random.nextLong())) { "print \"$it\"" }
        Vars.player.unit()?.addBuild(BuildPlan(location.x.toInt(), location.y.toInt(), 0, Blocks.microProcessor, config), false)
        val x = location.x.toInt()  // don't keep tiles around
        val y = location.y.toInt()
        Timer.schedule({
            Core.app.post {  // make sure it doesn't do this while something else is iterating through the plans
                Vars.player.unit()?.plans?.remove { it.x == x && it.y == y && it.block == Blocks.microProcessor }
            }
        }, 0.25f)
    }
}
