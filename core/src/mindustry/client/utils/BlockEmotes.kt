package mindustry.client.utils

import arc.struct.*
import mindustry.gen.*
import mindustry.ui.*

class BlockEmotes : Autocompleter {
    private val emotes = Seq<BlockEmote>()
    private var cacheInput: String? = null
    override fun initialize() {
        Fonts.stringIcons.each { name, ch -> emotes.add(BlockEmote(ch, name)) }
        Iconc.codes.forEach {
            emotes.add(BlockEmote(it.value.toChar().toString(), it.key)) // Hmm yes very idio~~tic~~*matic*
        }
    }

    override fun closest(input: String): Seq<Autocompleteable> {
        if (input == cacheInput) return emotes.`as`()
        cacheInput = input
        val name = getLast(input, false)
        if (name === null) return emotes.`as`()

        emotes.each { it.match(name) }
        return emotes.sort { item: BlockEmote -> item.matchCache }.`as`()
    }

    /**
     * @param suffix whether to return the final : in (:text:)
     *
     * From input, we want to find a trailing (:text:), where the final : is optional.
     * Hence reverse the string, find the corresponding pattern via regex grouping, and reverse that pattern back.
     */
    private fun getLast(input: String, suffix: Boolean = true) = (if (suffix) "^(:?[^:\\s]+):" else "^:?([^:\\s]+):")
            .toRegex().find(input.reversed())?.groups?.get(1)?.value?.reversed()

    private inner class BlockEmote(private val unicode: String, private val name: String) : Autocompleteable {
        var matchCache = 0f
        fun match(queryName: String): Float {
            matchCache = (-biasedLevenshtein(queryName, name) + queryName.length.toFloat()) / queryName.length.toFloat()
            return matchCache
        }

        override fun matches(input: String): Float {
            if (input == cacheInput) return matchCache
            return match(getLast(input, false)?: return 0f)
        }

        override fun getCompletion(input: String): String {
            val text = getLast(input) ?: return input

            return input.replaceLast(":$text", unicode)
        }

        override fun getHover(input: String): String {
            val text = getLast(input) ?: return input

            return input.replaceLast(":$text", "$unicode :$name:")
        }
    }
}