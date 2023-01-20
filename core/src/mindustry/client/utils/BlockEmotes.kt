package mindustry.client.utils

import arc.struct.*
import arc.util.*
import mindustry.gen.*
import mindustry.ui.*

class BlockEmotes : Autocompleter {
    private val emotes = Seq<BlockEmote>()
    override fun initialize() {
        Fonts.stringIcons.each { name, ch -> emotes.add(BlockEmote(ch, name)) }
        Iconc.codes.forEach {
            emotes.add(BlockEmote(it.value.toChar().toString(), it.key)) // Hmm yes very idio~~tic~~*matic*
        }
    }

    override fun getCompletion(input: String): Autocompleteable = bestMatch(input)

    private fun bestMatch(input: String) = emotes.maxBy { it.matches(input) }

    override fun matches(input: String) = (bestMatch(input)?.matches(input) ?: 0f) > .5f

    override fun closest(input: String): Seq<Autocompleteable> {
        return emotes.sort { item: BlockEmote -> item.matches(input) }.`as`()
    }

    private class BlockEmote(private val unicode: String, private val name: String) : Autocompleteable {
        private var matchCache = 0f
        private var cacheName: String? = null
        override fun matches(input: String): Float {
            if (input == cacheName) return matchCache // FINISHME: This system still sucks.
            cacheName = input
            if (Strings.count(input, ':') % 2 == 0) {
                matchCache = 0f
                return 0f
            }
            var dst = BiasedLevenshtein.biasedLevenshteinInsensitive(input.substring(input.lastIndexOf(':') + 1), name)
            dst *= -1f
            dst += name.length.toFloat()
            dst /= name.length.toFloat()
            matchCache = dst
            return dst
        }

        override fun getCompletion(input: String): String { // FINISHME: I don't know what foo was thinking when he wrote the autocomplete system but it's all horrible and needs a proper rewrite
            val items = Seq(input.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
            if (items.isEmpty) return input
            items.pop()
            val start = items.toString("")
            return start + unicode
        }

        override fun getHover(input: String): String {
            if (!input.contains(":")) return input
            val items = Seq(input.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
            if (items.size == 0) return input
            val text = items.peek()
            return input.replace(":$text", ":$name:")
        }
    }
}