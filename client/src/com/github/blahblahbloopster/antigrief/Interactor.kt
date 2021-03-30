package com.github.blahblahbloopster.antigrief

import mindustry.ai.types.LogicAI
import mindustry.gen.Nulls
import mindustry.gen.Player
import mindustry.gen.Unit

interface Interactor {
    val name: String
}

open class UnitInteractor(unit: Unit?) : Interactor {
    override val name: String = when {
        unit?.isPlayer == true -> "${unit.type.localizedName} controlled by ${unit.playerNonNull().name}"
        unit?.controller() is LogicAI -> "${unit.type.localizedName} logic-controlled by a processor accessed by ${(unit.controller() as LogicAI).controller.lastAccessed}"
        else -> unit?.type?.localizedName ?: "null unit"
    }
}

class NullUnitInteractor : UnitInteractor(Nulls.unit) {
    override val name = "null unit"
}

fun Player?.toInteractor(): Interactor {
    this ?: return NullUnitInteractor()
    return UnitInteractor(unit())
}

fun Unit?.toInteractor(): Interactor {
    this ?: return NullUnitInteractor()
    return UnitInteractor(this)
}
