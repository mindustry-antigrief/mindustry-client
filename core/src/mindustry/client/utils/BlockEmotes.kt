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

    override fun closest(input: String): Seq<Autocompleteable> {
        return emotes.sort { item: BlockEmote -> item.matches(input) }.`as`()
    }

    private class BlockEmote(private val unicode: String, private val name: String) : Autocompleteable {
        private var matchCache = 0f
        private var cacheName: String? = null

        override fun matches(input: String): Float {
            if (input == cacheName) return matchCache // FINISHME: This system still sucks.
            cacheName = input
            val text = getLast(input)
            if (text == null || Strings.count(text, ':') % 2 == 1) {
                matchCache = 0f
                return 0f
            }
            var dst = biasedLevenshtein(text, name)
            dst *= -1f
            dst += name.length.toFloat()
            dst /= name.length.toFloat()
            matchCache = dst
            return dst
        }

        override fun getCompletion(input: String): String {
            val text = getLast(input) ?: return input

            return input.replaceLast(":$text", unicode)
        }

        override fun getHover(input: String): String {
            val text = getLast(input) ?: return input

            return input.replaceLast(":$text", "$unicode :$name:")
        }

        private fun getLast(input: String): String? {
            val strings = Seq.with(input.split("\\s".toRegex()))
            if (strings.isEmpty) return null
            val text = strings.peek()
            if (!text.startsWith(":")) return null
            return text.replaceFirst(":", "")
        }
    }
}