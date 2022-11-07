package mindustry.client.utils

import arc.util.*
import arc.*
import mindustry.client.*
import mindustry.client.antigrief.*
import mindustry.Vars.*
import mindustry.client.*
import mindustry.client.antigrief.*
import mindustry.world.blocks.logic.LogicBlock.*

object ProcessorPatcher {
    private val attemMatcher =
        """
        (ubind @?[^ ]+)                            # bind a unit
        sensor (\S+) @unit @flag                   # set _flag to unit flag
        op add (\S+) \3 1                          # increment _attem by 1
        jump \d+ greaterThanEq \3 \d+              # break if _attem >= 83
        jump \d+ (?:notEqual|always) ([^ ]+) \2    # loop if _flag != 0 (or always in some variants)
        set \3 0                                   # _attem = 0
        """.replace("\\s+#.+$".toRegex(RegexOption.MULTILINE), "").trimIndent().toRegex() // The regex comment mode is dumb

    public val jumpMatcher = "jump (\\d+)(.*)".toRegex()

    public val attemText = """
        print "Please do not use this delivery logic."
        print "It is attem83 logic and is considered bad logic"
        print "as it breaks other delivery logic and even other attem logic."
        print "For more info please go to https://mindustry.dev/attem."
        printflush message1
    """.trimIndent();

    fun countProcessors(builds: Iterable<LogicBuild>): Int {
        Time.mark()
        val count = builds.count { attemMatcher.containsMatchIn(it.code) }
        Log.debug("Counted $count/${builds.count()} attems in ${Time.elapsed()}ms")
        return count
    }

    fun isAttem(code: String): Boolean {
        return attemMatcher.containsMatchIn(code)
    }

    fun patch(code: String): String {return patch(code, if(Core.settings.getBool("removeatteminsteadoffixing")) "r" else "c")}
    fun patch(code: String, mode: String): String {
        val result = attemMatcher.find(code) ?: return code

        return when (mode) {
            "c" -> {
                val groups = result.groupValues
                val bindLine = (0..result.range.first).count { code[it] == '\n' }
                buildString {
                    replaceJumps(this, code.substring(0, result.range.first), bindLine)
                    append(groups[1])
                    append("\nsensor ").append(groups[2]).append(" @unit @flag\n")
                    append("jump ").append(bindLine).append(" notEqual ").append(groups[2]).append(' ').append(groups[4]).append('\n')
                    replaceJumps(this, code.substring(result.range.last + 1), bindLine)
                }
            }
            "r" -> attemText
            else -> code
        }
    }

    private fun replaceJumps(sb: StringBuilder, code: String, bindLine: Int) {
        val matches = jumpMatcher.findAll(code)
        val extra = sb.length
        sb.append(code)
        matches.forEach {
            val group = it.groups[1]!!
            val line = Strings.parseInt(group.value)
            if (line >= bindLine) sb.setRange(group.range.first + extra, group.range.last + extra + 1, (line - 3).toString())
        }
    }

    fun inform(build: LogicBuild) {
        ClientVars.configs.add(ConfigRequest(build.tileX(), build.tileY(), compress(attemText, build.relativeConnections()
        )))
    }
}
