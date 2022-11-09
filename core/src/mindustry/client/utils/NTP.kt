package mindustry.client.utils

import arc.*
import arc.util.*
import org.apache.commons.net.ntp.*
import java.net.*
import java.time.*
import java.util.*
import java.util.concurrent.atomic.*

class NTP {
    private val timeClient = NTPUDPClient()
    lateinit var address: InetAddress
    // haha get it atomic clock
    /** A reference to a [Clock] that is synchronized every minute to an NTP pool. */
    val clock: AtomicReference<Clock> = AtomicReference(Clock.systemUTC())

    init {
        try {
            address = InetAddress.getByName("pool.ntp.org")
            timeClient.defaultTimeout = 5000 // For whatever reason, sometimes it just fails

            Threads.daemon("NTP") {
                while (true) {
                    try {
                        val time = fetchTime()
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
            }
        } catch (e: UnknownHostException) {
            Log.warn("Not initializing NTP, most likely because there is no internet.")
        }
    }

    private fun fetchTime(): Instant {
        val timeInfo = timeClient.getTime(address)
        val returnTime = timeInfo.message.transmitTimeStamp.time
        val time = Date(returnTime)
        return time.toInstant()
    }

    fun instant(): Instant = clock.get().instant()
}