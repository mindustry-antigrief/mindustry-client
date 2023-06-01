@file:JvmName("MaliciousLogicDetector")

package mindustry.client.utils

import arc.util.*
import mindustry.world.blocks.logic.*

// enum class LogicDetectionType(

// ) {
// 	None,
// 	Flag,
// 	Bomb,
// 	Suicide,
// }

enum class LogicDetectionLevel {
	Safe, SlightlySus, Sus, Malicious
}

val SusFlagInstruction = Regex("^ucontrol flag \\d+", RegexOption.MULTILINE)
val FlagInstruction = Regex("^ucontrol flag", RegexOption.MULTILINE)

val ControlFlowInstruction = Regex("^jump ", RegexOption.MULTILINE)

val SusMoveInstruction = Regex("^ucontrol move \\d+ \\d+", RegexOption.MULTILINE)
val MoveInstruction = Regex("^ucontrol move|approach|pathfind", RegexOption.MULTILINE)

val ItemTakeInstruction = Regex("^ucontrol itemTake", RegexOption.MULTILINE)
val ItemDropAirInstruction = Regex("^ucontrol itemDrop @air", RegexOption.MULTILINE)
val LookupInstruction = Regex("^lookup", RegexOption.MULTILINE)

val ForceShootInstruction = Regex("^ucontrol targetp @unit (1|true)", RegexOption.MULTILINE)

val Crawler = Regex("@crawler");
val Flammable = Regex("@coal|@pyratite|@blast-compound|@surge-alloy");

val UnitBindInstruction = Regex("^ubind ", RegexOption.MULTILINE)

val SensorFlagInstruction = Regex("^sensor \\S+ @unit @flag", RegexOption.MULTILINE)
val CheckFlagZeroInstruction = Regex("^sensor (\\S+) @unit @flag[\\s\\S]*\njump \\S+ (?:notEqual|equal) \\1 0", RegexOption.MULTILINE) //regex is awesome, this tests if the logic: sensors the flag of @unit, then jumps if the flag is not zero

infix fun String.has(regex:Regex):Boolean = regex.containsMatchIn(this)

fun isMalicious(proc:LogicBlock.LogicBuild):LogicDetectionLevel {
	val code = proc.code;
	if(code.isEmpty()) return LogicDetectionLevel.Safe

	//Detect "logicvsfish"
	if(code.contains("logicvsfish")) return LogicDetectionLevel.Malicious

	//Detect flag malware
	if(
		code has SusFlagInstruction && //Hardcoded flag instruction
		code has UnitBindInstruction && //Unit bind instruction
		!(code has ControlFlowInstruction) //No control flow
	) return LogicDetectionLevel.Malicious //Probably flagging all units

	if(
		code has FlagInstruction && //Any flag instruction
		code has UnitBindInstruction && //Unit bind instruction
		!(code has CheckFlagZeroInstruction) //No flag zero check
	) return LogicDetectionLevel.SlightlySus //Probably not flagging all units


	//Detect suicide malware
	if(
		code has UnitBindInstruction && //Binds a unit
		code has SusMoveInstruction && //Moves to hardcoded location
		!(code has ControlFlowInstruction) //No control flow
	) return LogicDetectionLevel.Sus

	if(
		code has UnitBindInstruction && //Binds a unit
		code has MoveInstruction && //Moves
		!(code has CheckFlagZeroInstruction) //No flag zero check
	) return LogicDetectionLevel.SlightlySus

	//Detect void items malware
	if(
		code has UnitBindInstruction && //Binds a unit
		code has ItemTakeInstruction && //Takes item
		code has ItemDropAirInstruction && //Drops to air
		!(code has ControlFlowInstruction) //No control flow
	) return LogicDetectionLevel.Malicious

	if(
		code has UnitBindInstruction && //Binds a unit
		code has MoveInstruction && //Moves
		!(code has CheckFlagZeroInstruction) //No flag zero check
	) return LogicDetectionLevel.Sus


	//Detect crawler bomb malware
	if(
		code has UnitBindInstruction && //Binds a unit
		code has ItemTakeInstruction && //Takes item
		((code has Flammable &&
		code has Crawler) || code has LookupInstruction) && //References to a flammable and crawler, or lookup
		code has ForceShootInstruction //Tells the unit to shoot itself, for crawler this means die
	) return if(code has ControlFlowInstruction) LogicDetectionLevel.Sus else LogicDetectionLevel.Malicious // if theres control flow instructions, then sus, otherwise malicious

	//No checks failed
	return LogicDetectionLevel.Safe;

	
	

}