package mindustry.client.utils

import arc.*
import arc.util.*
import org.apache.commons.net.ntp.*
import java.net.*
import java.time.*
import java.util.concurrent.atomic.*

class NTP {
    // haha get it atomic clock
    /** A reference to a [Clock] that is synchronized every minute to an NTP pool. */
    val clock: AtomicReference<Clock> = AtomicReference(Clock.systemUTC())

    init {
        Threads.daemon("NTP") {
            try {
                val address = InetAddress.getByName("pool.ntp.org")
                val timeClient = NTPUDPClient()
                timeClient.defaultTimeout = 5000 // For whatever reason, sometimes it just fails

                while (true) {
                    try {
                        val timeInfo = timeClient.getTime(address)
                        val time = Instant.ofEpochMilli(timeInfo.message.transmitTimeStamp.time)

                        val baseClock = clock.get()
                        clock.set(
                            Clock.offset(
                                baseClock,
                                Duration.between(baseClock.instant(), time)
                                    .apply { if (Core.settings.getBool("logntp")) Log.debug("Fetched time from NTP (clock was ${toMillis()} ms off)") })
                        )
                    } catch (e: SocketTimeoutException) {
                        Log.debug("NTP Timed out")
                    } catch (e: Exception) {
                        if (e.message == "Network is unreachable") Log.debug("NTP server unreachable")
                        else Log.debug("NTP error!\n" + e.stackTraceToString())
                    }
                    Threads.sleep(60_000)
                }
            } catch (e: UnknownHostException) {
                Log.warn("Not initializing NTP, most likely because there is no internet.")
            }
        }
    }

    fun instant(): Instant = clock.get().instant()
}