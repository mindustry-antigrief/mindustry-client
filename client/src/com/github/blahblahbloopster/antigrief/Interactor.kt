package com.github.blahblahbloopster.antigrief

import mindustry.ai.types.LogicAI
import mindustry.gen.Unit

interface Interactor {
    val name: String
}

class UnitInteractor(unit: Unit) : Interactor {
    override val name: String = when {
        unit.isPlayer -> "${unit.type.localizedName} controlled by ${unit.playerNonNull().name}"
        unit.controller() is LogicAI -> "${unit.type.localizedName} logic-controlled by a processor accessed by ${(unit.controller() as LogicAI).controller.lastAccessed}"
        else -> unit.type.localizedName
    }
}
