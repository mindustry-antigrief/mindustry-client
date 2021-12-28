package mindustry.client.antigrief

import java.time.*

interface InteractionLog {
    val cause: Interactor
    val time: Instant
}
