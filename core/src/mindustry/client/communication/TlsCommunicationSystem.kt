package mindustry.client.communication

import arc.struct.ByteSeq
import arc.util.Log
import mindustry.client.crypto.TlsClientHolder
import mindustry.client.crypto.TlsPeerHolder
import mindustry.client.utils.escape
import mindustry.client.utils.unescape
import java.io.Closeable
import java.security.cert.X509Certificate

class TlsCommunicationSystem(
    val peer: TlsPeerHolder,
    private val underlying: Packets.CommunicationClient,
    val cert: X509Certificate
) : CommunicationSystem(), Closeable {

    override val listeners: MutableList<(input: ByteArray, sender: Int) -> Unit> = mutableListOf()

    override val id = if (peer is TlsClientHolder) 1 else 0

    private var keepaliveSendingTimer = 0
    private var keepaliveRecieveTimer = 0

    init {
        peer.start()
        underlying.addListener { transmission, _ ->
            try {
                if (transmission is TLSDataTransmission && transmission.destination == cert.serialNumber && transmission.source == peer.expectedCert.serialNumber) {
                    peer.write(transmission.content)
                }
            } catch (e: Exception) {
                close()
                Log.debug("TLS receive exception!\n${e.stackTraceToString()}")
            }
        }
    }

    override val MAX_LENGTH
        get() = underlying.communicationSystem.MAX_LENGTH

    override val RATE
        get() = underlying.communicationSystem.RATE

    var isClosed = false
        private set

    private companion object {
        private const val ESCAPEMENT = 0.toByte()
        private const val KEEPALIVE = 1.toByte()
        private const val CLOSE = 2.toByte()
        private const val DELIMINATOR = 3.toByte()

        private val escapeChars = arrayOf(KEEPALIVE, CLOSE, DELIMINATOR)

        private fun ByteArray.escape() = toList().escape(ESCAPEMENT, *escapeChars).toByteArray()
        private fun ByteArray.unescape() = toList().unescape(ESCAPEMENT, *escapeChars).toByteArray()
    }

    private val incoming = ByteSeq()

    override fun send(bytes: ByteArray) {
        try {
            peer.writeSecure(bytes.escape() + DELIMINATOR)
        } catch (e: Exception) {
            close()
            Log.debug("TLS exception!\n${e.stackTraceToString()}")
        }
    }

    fun update() {
        underlying.update()
        if (keepaliveRecieveTimer++ > 1800) {
            close()
            return
        }
        try {
            if (peer.isClosed) {
                if (!isClosed) close()
                return
            }

            if (peer.handshakeDone && keepaliveSendingTimer++ >= 600) {
                keepaliveSendingTimer = 0
                peer.writeSecure(byteArrayOf(KEEPALIVE))
            }

            val read = peer.read()
            if (read.isNotEmpty()) {
                underlying.send(TLSDataTransmission(cert.serialNumber, peer.expectedCert.serialNumber, read))
            }

            val applicationIn = peer.readSecure()
            if (applicationIn.isNotEmpty()) {
                val output = mutableListOf<ByteArray>()
                val current = ByteSeq(incoming)

                var lastWasEscape = false
                for (item in applicationIn) {
                    if (item == ESCAPEMENT && !lastWasEscape) {
                        lastWasEscape = true
                    } else if (!lastWasEscape && item in escapeChars) {
                        when (item) {
                            KEEPALIVE -> keepaliveRecieveTimer = 0
                            CLOSE -> { close(); return }
                            DELIMINATOR -> { output.add(current.toArray()); current.clear() }
                        }
                        lastWasEscape = false
                    } else {
                        current.add(item)
                        lastWasEscape = false
                    }
                }
                incoming.addAll(current)

                for (fullTransmission in output) {
                    listeners.forEach { it(fullTransmission, if (id == 1) 0 else 1) }
                }
            }
        } catch (e: Exception) {
            Log.debug("TLS update exception!\n${e.stackTraceToString()}")
            close()
        }
    }

    override fun close() {
        try {
            peer.writeSecure(byteArrayOf(CLOSE))
            val read = peer.read()
            if (read.isNotEmpty()) underlying.send(
                TLSDataTransmission(
                    cert.serialNumber,
                    peer.expectedCert.serialNumber,
                    read
                )
            )
            peer.close()
        }catch (e: Exception) {
            Log.debug(e.stackTraceToString())
        } finally {
            isClosed = true
        }
    }
}
