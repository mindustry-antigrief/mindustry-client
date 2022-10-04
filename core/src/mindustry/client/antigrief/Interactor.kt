package mindustry.client.antigrief

import mindustry.ai.types.*
import mindustry.gen.*
import mindustry.gen.Unit

interface Interactor {
    val name: String

    val shortName: String
}

open class UnitInteractor(unit: Unit?) : Interactor {
    override val name = when {
        unit?.isPlayer == true -> "${unit.type.localizedName} controlled by ${unit.player.name}"
//        (unit?.controller() as? FormationAI)?.leader?.isPlayer == true -> "${unit.type.localizedName} controlled by ${(unit.controller() as? FormationAI)?.leader?.playerNonNull()?.name}" FINISHME: commanding exists
        unit?.controller() is LogicAI -> "${unit.type.localizedName} logic-controlled by a processor accessed by ${(unit.controller() as? LogicAI)?.controller?.lastAccessed}"
        else -> unit?.type?.localizedName ?: "null unit"
    }

    override val shortName: String = when {
        unit?.isPlayer == true -> unit.player.name
//        (unit?.controller() as? FormationAI)?.leader?.isPlayer == true -> (unit.controller() as FormationAI).leader.playerNonNull().name FINISHME: Commanding exists
        unit?.controller() is LogicAI -> "logic-controlled ${unit.type.localizedName}"
        else -> unit?.type?.localizedName ?: "null unit"
    }
}

class NullUnitInteractor : UnitInteractor(Nulls.unit) {
    override val name = "null unit" // FINISHME: Dont use this when nodes are automatically configured

    override val shortName = "null unit" // FINISHME: Dont use this when nodes are automatically configured
}

class NoInteractor : Interactor {
    override val name = ""
    override val shortName = ""
}

fun Player?.toInteractor(): Interactor {
    this ?: return NullUnitInteractor()
    return UnitInteractor(unit())
}

fun Unit?.toInteractor(): Interactor {
    this ?: return NullUnitInteractor()
    return UnitInteractor(this)
}
