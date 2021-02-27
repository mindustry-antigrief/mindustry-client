package com.github.blahblahbloopster.crypto

import arc.Core
import arc.files.Fi
import com.beust.klaxon.Klaxon
import com.github.blahblahbloopster.Initializable
import com.github.blahblahbloopster.Main
import com.github.blahblahbloopster.ui.base64

object KeyFolder : KeyList {
    private val klaxon = Klaxon().converter(KeyHolderJson)
    private var set = mutableSetOf<KeyHolder>()
    private var fi: Fi? = null

    override fun initializeAlways() {
        val file = Core.settings.dataDirectory.child("keys.json")
        var createdNewFile = false
        if (!file.exists()) {
            createdNewFile = true
            file.writeString("[]")
        }
        val items = klaxon.parseArray<KeyHolder>(file.readString())
        items ?: return
        set.addAll(items)
        fi = file

        if (createdNewFile) {
            add(KeyHolder(PublicKeyPair("8/GKCQvbLsHOYibfEjb3KlU5YX46hYHeO+X4zpU/MQjJR4T1l2kAqUT1EuO2YwD/n8u3blb9BnbiyNbwlvSTZw==".base64()!!), "foo", true, Main.messageCrypto))
        }
    }

    override fun add(element: KeyHolder): Boolean {
        val output = set.add(element)
        save()
        return output
    }

    private fun save() {
        fi?.writeString(klaxon.toJsonString(set))
    }

    private class KeyIterator : MutableIterator<KeyHolder> {
        private val lst = set.toMutableList()
        private var i = 0

        override fun hasNext(): Boolean {
            return i < lst.size
        }

        override fun next(): KeyHolder {
            return lst[i++]
        }

        override fun remove() {
            set.remove(lst.removeAt(i))
        }
    }

    override fun iterator(): MutableIterator<KeyHolder> {
        return KeyIterator()
    }

    override val size: Int
        get() = set.size

    override fun contains(element: KeyHolder) = set.contains(element)

    override fun containsAll(elements: Collection<KeyHolder>) = set.containsAll(elements)

    override fun isEmpty() = set.isEmpty()

    override fun addAll(elements: Collection<KeyHolder>): Boolean {
        val output = set.addAll(elements)
        save()
        return output
    }

    override fun clear() {
        set.clear()
        save()
    }

    override fun remove(element: KeyHolder): Boolean {
        val output = set.remove(element)
        save()
        return output
    }

    override fun removeAll(elements: Collection<KeyHolder>): Boolean {
        val output = set.removeAll(elements)
        save()
        return output
    }

    override fun retainAll(elements: Collection<KeyHolder>): Boolean {
        val output = set.retainAll(elements)
        save()
        return output
    }
}
