package mindustry.client.utils

import arc.struct.*
import arc.util.*
import mindustry.Vars.*
import mindustry.client.*
import mindustry.client.antigrief.*
import mindustry.gen.*
import mindustry.world.blocks.logic.LogicBlock.*

object ProcessorPatcher {
    private val attemMatcher =
        "(ubind @?[^ ]+\\n)sensor (\\w+) @unit @flag\\nop add (\\w+) \\3 1\\njump \\d+ greaterThanEq \\3 \\d+\\njump \\d+ notEqual ([^ ]+) \\2\\nset \\3 0".toRegex()

    private val jumpMatcher = "jump (\\d+)(.*)".toRegex()

    private val attemText = """
        print "Please do not use this delivery logic."
        print "It is attem83 logic is considered bad logic"
        print "as it breaks other logic."
        print "For more info please go to mindustry.dev/attem"
        printflush message1
    """.trimIndent();

    fun countProcessors(builds: Seq<LogicBuild>): Int {
        Time.mark()
        val count = builds.count { attemMatcher.containsMatchIn(it.code) }
        Log.debug("Counted $count/${builds.size} attems in ${Time.elapsed()}ms")
        return count
    }

    fun isAttem(code: String): Boolean {
        return attemMatcher.containsMatchIn(code)
    }

    fun patch(code: String, mode: String = "c"): String {
        val result = attemMatcher.find(code) ?: return code

        return when (mode) {
            "c" -> {
                val groups = result.groupValues
                val bindLine = (0..result.range.first).count { code[it] == '\n' }
                buildString {
                    replaceJumps(this, code.substring(0, result.range.first), bindLine)
                    append(groups[1])
                    append("sensor ").append(groups[2]).append(" @unit @flag\n")
                    append("jump ").append(bindLine).append(" notEqual ").append(groups[2]).append(' ').append(groups[4]).append('\n')
                    replaceJumps(this, code.substring(result.range.last + 1), bindLine)
                }
            }
            "r" -> attemText
            else -> code
        }
    }

    private fun replaceJumps(sb: StringBuilder, code: String, bindLine: Int) {
        val matches = jumpMatcher.findAll(code).toList()
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
