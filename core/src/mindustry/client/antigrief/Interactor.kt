package mindustry.client.antigrief

import arc.*
import mindustry.ai.types.*
import mindustry.entities.units.*
import mindustry.gen.*
import mindustry.gen.Unit
import mindustry.ui.*

interface Interactor {
    val name: String

    val shortName: String

    val playerID: Int
}

open class UnitInteractor(unit: Unit?) : Interactor {
    override val name = when { // FINISHME: bundle
        unit?.isPlayer == true -> "${unit.type.localizedName} controlled by ${unit.player.coloredName()}"
        unit?.controller() is CommandAI -> "${unit.type.localizedName} commanded by ${unit.lastCommanded ?: "unknown"} " +
            "to ${(unit.controller() as CommandAI).currentCommand()?.name ?: "unknown"}"
        unit?.controller() is LogicAI -> {
            val lcontrol = (unit.controller() as? LogicAI)?.controller
            "${unit.type.localizedName} logic-controlled by ${lcontrol?.block?.localizedName} (${lcontrol?.tileX()}, ${lcontrol?.tileY()}) accessed by ${lcontrol?.lastAccessed}"
        }
        unit?.controller() is AIController -> {
            val controllerName = when (unit.controller()) {
                is MinerAI -> Core.bundle.get("command.mine")
                is FlyingAI -> "fly"
                is GroundAI -> "walk"
                is BuilderAI -> Core.bundle.get("command.${if ((unit.controller() as BuilderAI).onlyAssist) "rebuild" else "assist"}")
                is RepairAI -> Core.bundle.get("command.repair")
                else -> "control"
            }
            "AI-${controllerName} ${unit.type.localizedName}"
        }
        else -> unit?.type?.localizedName ?: "null unit"
    }

    override val shortName: String = when {
        unit?.isPlayer == true -> unit.player.coloredName()
        unit?.controller() is CommandAI -> if (Core.settings.getBool("useiconslogs")) Iconc.codes.get("units").toChar().toString() + Fonts.getUnicodeStr(unit.type.name)
            else "player-commanded ${unit.type.localizedName}"
        unit?.controller() is LogicAI -> if (Core.settings.getBool("useiconslogs")) Iconc.codes.get("logic").toChar().toString() + Fonts.getUnicodeStr(unit.type.name)
            else "logic-controlled ${unit.type.localizedName}"
        else -> unit?.type?.localizedName ?: "null unit"
    }

    override val playerID: Int = if (unit?.isPlayer == true) unit.player.id else -1
}

object NullUnitInteractor : UnitInteractor(null) {
    override val name = "null unit"

    override val shortName = "null unit"
}

object NoInteractor : Interactor {
    override val name = ""
    override val shortName = ""
    override val playerID: Int = -1
}

class NetworkInteractor(override val name: String, override val shortName: String, override val playerID: Int) : Interactor

fun Player?.toInteractor(): Interactor {
    this ?: return NullUnitInteractor
    return UnitInteractor(unit())
}

fun Unit?.toInteractor(): Interactor {
    this ?: return NullUnitInteractor
    return UnitInteractor(this)
}