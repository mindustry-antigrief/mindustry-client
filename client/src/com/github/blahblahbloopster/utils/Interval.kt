package com.github.blahblahbloopster.utils

import com.github.blahblahbloopster.age
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Facilitates running something at a fixed rate in a loop.
 * Example usage:
 * ```
 * val interval = Interval(100L)
 * while (true) {
 *     if (interval.get()) {
 *         // this block is run every 100 ms
 *     }
 * }
 * ```
 * @param milliseconds The number of milliseconds to wait between calls to [get] returning true.
 */
class Interval(private val milliseconds: Long) {
    private var lastInvoked = Instant.ofEpochSecond(0)

    /** Returns true if it has been [milliseconds] ms or more since last returning true. */
    fun get(): Boolean {
        if (lastInvoked.age(ChronoUnit.MILLIS) >= milliseconds) {
            lastInvoked = Instant.now()
            return true
        }
        return false
    }
}
