package mindustry.client.communication

/** An arbitrary-length serializable container.
 * To implement:
 *  1. Override [serialize] to encode your transmission's data
 *  2. Create a constructor that takes a [ByteArray] (the serialized transmission) and [Long] (transmission id)
 *  3. Register it in [Packets.registeredTransmissionTypes]
 */
interface Transmission {
    var id: Long

    val secureOnly: Boolean

    fun serialize(): ByteArray
}
