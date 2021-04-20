package mindustry.client.antigrief

import java.time.Instant

interface InteractionLog {
    val cause: Interactor
    val time: Instant
}
