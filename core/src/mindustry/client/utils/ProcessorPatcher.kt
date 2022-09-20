package mindustry.client.utils

import arc.util.*
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
        """.replace("\\s+#.+$".toRegex(RegexOption.MULTILINE), "").trimIndent().toRegex() // The regex multiline mode is dumb

    private val jumpMatcher = "jump (\\d+)(.*)".toRegex()

    fun countProcessors(builds: Iterable<LogicBuild>): Int {
        Time.mark()
        val count = builds.count { attemMatcher.containsMatchIn(it.code) }
        Log.debug("Counted $count/${builds.count()} attems in ${Time.elapsed()}ms")
        return count
    }

    fun patch(code: String): String {
        val result = attemMatcher.find(code) ?: return code

        val groups = result.groupValues
        val bindLine = (0..result.range.first).count { code[it] == '\n' }
        return buildString {
            replaceJumps(this, code.substring(0, result.range.first), bindLine)
            append(groups[1])
            append("\nsensor ").append(groups[2]).append(" @unit @flag\n")
            append("jump ").append(bindLine).append(" notEqual ").append(groups[2]).append(' ').append(groups[4]).append('\n')
            replaceJumps(this, code.substring(result.range.last + 1), bindLine)
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
        ClientVars.configs.add(ConfigRequest(build.tileX(), build.tileY(), compress("""
            print "Please do not use this logic "
            print "this attem logic is not good "
            print "it breaks other logic "
            print "more info at mindustry.dev/attem"
            printflush message1
        """.trimIndent(), build.relativeConnections()
        )))
    }
}