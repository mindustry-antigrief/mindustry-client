package mindustry.client.crypto

import arc.Core
import arc.files.Fi
import com.beust.klaxon.Klaxon
import mindustry.client.Main
import mindustry.client.utils.base64

object KeyFolder : KeyList {
    private val klaxon = Klaxon().converter(KeyHolderJson)
    private var set = mutableSetOf<KeyHolder>()
    private lateinit var fi: Fi

    override fun initializeAlways() {
        fi = Core.settings.dataDirectory.child("keys.json")
        if (!fi.exists()) {
            add(KeyHolder(PublicKeyPair("8/GKCQvbLsHOYibfEjb3KlU5YX46hYHeO+X4zpU/MQjJR4T1l2kAqUT1EuO2YwD/n8u3blb9BnbiyNbwlvSTZw==".base64()!!), "foo", true, Main.messageCrypto))
            add(KeyHolder(PublicKeyPair("wnnWJvq5c60ryrYndufA5i6JVZcHijLoCHMDsnHPVx76jmfThaX+pxnAAGID6l9jVbFefC6tq8SFsBE5mGU0LQ==".base64()!!), "buthed010203", true, Main.messageCrypto))
        }

        val items = klaxon.parseArray<KeyHolder>(fi.readString())
        items ?: return
        set.addAll(items)
    }

    override fun add(element: KeyHolder): Boolean {
        val output = set.add(element)
        save()
        return output
    }

    private fun save() {
        fi.writeString(klaxon.toJsonString(set))
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
