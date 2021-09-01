package mindustry.client.utils

import arc.util.Log
import arc.util.Timer
import mindustry.Vars
import org.apache.commons.net.ntp.NTPUDPClient
import java.net.InetAddress
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.atomic.AtomicReference

class NTP {
    private val timeClient = NTPUDPClient()
    val address: InetAddress = InetAddress.getByName("pool.ntp.org")
    // haha get it atomic clock
    /** A reference to a [Clock] that is synchronized every 30 seconds to an NTP pool. */
    val clock: AtomicReference<Clock> = AtomicReference(Clock.systemUTC())

    init {
        Timer.schedule({
            Vars.clientThread.taskQueue.post {
                try {
                    val time = fetchTime()
                    val baseClock = clock.get()
                    clock.set(
                        Clock.offset(
                            baseClock,
                            Duration.between(baseClock.instant(), time)
                                .apply { Log.debug("Fetched time from NTP (clock was ${toMillis()} ms off)") })
                    )
                } catch (e: Exception) {
                    Log.debug("NTP error!\n" + e.stackTraceToString())
                }
            }
        }, 1f, 30f)
    }

    private fun fetchTime(): Instant {
        val timeInfo = timeClient.getTime(address)
        val returnTime = timeInfo.message.transmitTimeStamp.time
        val time = Date(returnTime)
        return time.toInstant()
    }

    fun instant(): Instant = clock.get().instant()
}
