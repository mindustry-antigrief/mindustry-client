package mindustry.client.communication.syncing

import arc.func.Boolf
import arc.func.Cons
import arc.struct.Queue
import java.util.*
import java.util.function.Consumer

open class SyncedQueue<T>(val syncer: Syncer<T>) : Queue<T>() {
    private fun updateSize() {
        size = syncer.list.size
    }

    override fun addLast(`object`: T) {
        syncer.added(listOf(`object` to size))
        updateSize()
    }

    override fun clear() {
        syncer.clear()
        updateSize()
    }

    override fun first(): T {
        return syncer.list.first()
    }

    override fun add(`object`: T) {
        syncer.added(listOf(`object` to size))
        updateSize()
    }

    override fun addFirst(`object`: T) {
        syncer.added(listOf(`object` to 0))
        updateSize()
    }

    override fun contains(value: T): Boolean {
        return syncer.list.contains(value)
    }

    override fun hashCode(): Int {
        return syncer.list.hashCode()
    }

    override fun each(c: Cons<in T>) {
        syncer.list.forEach { c.get(it) }
    }

    override fun iterator(): MutableIterator<T> {
        return object : MutableIterator<T> {
            private var i = 0
            private val itemsCopy = syncer.list.toList()
            private var removedCount = 0
            override fun remove() { syncer.removed(listOf(i - removedCount)); updateSize() }
            override fun hasNext() = i < itemsCopy.size - 1
            override fun next() = itemsCopy[i++]
        }
    }

    override fun last() = syncer.list.last()

    override fun isEmpty(): Boolean {
        return syncer.list.isEmpty()
    }

    override fun ensureCapacity(additional: Int) {}

    override fun removeFirst(): T {
        val f = first()
        syncer.removed(listOf(0))
        updateSize()
        return f
    }

    override fun get(index: Int): T {
        return syncer.list[index]
    }

    override fun removeLast(): T {
        val l = last()
        syncer.removed(listOf(size - 1))
        updateSize()
        return l
    }

    override fun indexOf(value: Boolf<T>): Int {
        return syncer.list.indexOfFirst { value.get(it) }
    }

    override fun indexOf(value: T, identity: Boolean): Int {
        return syncer.list.indexOfFirst { if (identity) value === it else value == it }
    }

    override fun remove(value: Boolf<T>): Boolean {
        val toRemove = mutableListOf<Int>()
        for ((index, item) in syncer.list.withIndex()) {
            if (value.get(item)) {
                toRemove.add(index)
            }
        }
        toRemove.reverse()
        syncer.removed(toRemove)
        updateSize()
        return toRemove.isNotEmpty()
    }

    override fun remove(value: T, identity: Boolean): Boolean {
        val index = indexOf(value, identity)
        if (index == -1) return false
        syncer.removed(listOf(index))
        updateSize()
        return true
    }

    override fun removeIndex(index: Int): T {
        val t = this[index]
        syncer.removed(listOf(index))
        updateSize()
        return t
    }

    override fun resize(newSize: Int) {
        return
    }

    override fun remove(value: T): Boolean {
        return super.remove(value)
    }

    override fun contains(value: T, identity: Boolean): Boolean {
        return super.contains(value, identity)
    }

    override fun toString(): String {
        return syncer.list.toString()
    }

    override fun spliterator(): Spliterator<T> {
        return syncer.list.spliterator()
    }

    override fun toArray(type: Class<T>): Array<T> {
        val array = arrayOfNulls<Any?>(size)
        for ((index, item) in withIndex()) {
            array[index] = item
        }
        return array as Array<T>
    }

    override fun forEach(action: Consumer<in T>) {
        syncer.list.forEach { action.accept(it) }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as SyncedQueue<*>

        if (syncer.list != other.syncer.list) return false

        return true
    }
}
