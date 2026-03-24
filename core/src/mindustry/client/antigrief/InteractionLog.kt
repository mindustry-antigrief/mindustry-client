package mindustry.client.antigrief

import arc.util.io.*
import java.time.*

interface InteractionLog {
    val cause: Interactor
    var time: Instant

    companion object {
        fun read(id: Int, reads: Reads, interactor: Interactor) = when(id) {
            8 -> BlockPayloadDropLog.read(reads, interactor)
            7 -> BlockPayloadPickupLog.read(reads, interactor)
            6 -> NodeLinkAddedTileLog.read(reads, interactor)
            5 -> UnitDestroyedLog.read(reads, interactor)
            4 -> TileDestroyedLog.read(reads)
            3 -> RotateTileLog.read(reads, interactor)
            2 -> ConfigureTileLog.read(reads, interactor)
            1 -> TileBreakLog.read(reads, interactor)
            0 -> TilePlacedLog.read(reads, interactor)
            else -> throw RuntimeException("Invalid log id. Add it to the mapping: $id")
        }
    }
}
