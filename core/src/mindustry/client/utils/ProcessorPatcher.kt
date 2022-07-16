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
        "(ubind @?[^ ]+\\n)sensor (\\S+) @unit @flag\\nop add (\\S+) \\3 1\\njump \\d+ greaterThanEq \\3 \\d+\\njump \\d+ notEqual ([^ ]+) \\2\\nset \\3 0".toRegex()

    private val jumpMatcher = "jump (\\d+)(.*)".toRegex()

    fun countProcessors(builds: Seq<Building>) = builds.count { it.team == player.team() && it is LogicBuild && attemMatcher.containsMatchIn(it.code) }

    fun patch(code: String): String {
        val result = attemMatcher.find(code) ?: return code

        val groups = result.groupValues
        val bindLine = (0..result.range.first).count { code[it] == '\n' }
        return buildString {
            replaceJumps(this, code.substring(0, result.range.first), bindLine)
            append(groups[1])
            append("sensor ").append(groups[2]).append(" @unit @flag\n")
            append("jump ").append(bindLine).append(" notEqual ").append(groups[2]).append(' ').append(groups[4]).append('\n')
            replaceJumps(this, code.substring(result.range.last + 1), bindLine)
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