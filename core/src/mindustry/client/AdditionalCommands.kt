package mindustry.client

import arc.Core
import arc.func.Prov
import arc.struct.Seq
import arc.util.Log
import arc.util.*
import mindustry.Vars.*
import mindustry.client.ClientVars.*
import mindustry.client.antigrief.ConfigRequest
import mindustry.client.navigation.*
import mindustry.client.navigation.Navigation.navigator
import mindustry.client.utils.*
import mindustry.content.Blocks
import mindustry.world.blocks.distribution.ItemBridge
import mindustry.world.blocks.logic.MessageBlock

fun additionalSetup() {
    register("uc <unit-type>", "Picks a unit nearest to cursor") { args, _ ->
        ui.unitPicker.pickUnit(findUnit(args[0]), Core.input.mouseWorldX(), Core.input.mouseWorldY(), true)
    }

    register("replacemessage <from> <to> [useRegex=t]", "Replaces corresponding text in messages.") { args, player ->
        if (args[0].length < 3) {
            player.sendMessage("[scarlet]That might not be a good idea...")
            return@register
        }
        val useRegex = args.size > 2 && args[2] == "t"
        replaceMsg(args[0], useRegex, args[0], useRegex, args[1])
    }

    register(
        "replacemsgif <matches> <from> <to> [useMatchRegex=t] [useFromRegex=t]",
        "Replaces corresponding text in messages, only if they match the text."
    ) { args, player ->
        if (args[0].length < 3) {
            player.sendMessage("[scarlet]That might not be a good idea...")
            return@register
        }
        replaceMsg(args[0], args.size > 3 && args[3] == "t", args[1], args.size > 4 && args[4] == "t", args[2])
    }

    register("phasei <interval>", "Changes interval for end bridge when shift+dragging phase conveyors.") { args, player ->
        try{
            val interval = Integer.parseInt(args[0])
            val maxInterval = (Blocks.phaseConveyor as ItemBridge).range
            if(interval < 1 || interval > maxInterval){
                player.sendMessage("[scarlet]Interval must be within 1 and $maxInterval!")
                return@register
            }
            ItemBridge.phaseWeaveInterval = interval
            Core.settings.put("weaveEndInterval", interval)
            player.sendMessage("[accent]Successfully set interval to $interval.")
        } catch (e : Exception){
            player.sendMessage("[scarlet]Failed to parse integer!")
        }
    }

    register("pathing", "Change the pathfinding algorithm") { _, player ->
        if (navigator is AStarNavigator) {
            navigator = AStarNavigatorOptimised
            player.sendMessage("[accent]Using [green]improved[] algorithm")
        } else if (navigator is AStarNavigatorOptimised) {
            navigator = AStarNavigator
            player.sendMessage("[accent]Using [gray]classic[] algorithm")
        }
    }

    register("pic [quality]", "Sets the image quality for sending via chat (0 -> png)") { args, player ->
        if (args.isEmpty()) {
            player.sendMessage("[accent]Enter a value between 0.0 and 1.0 for quality (0.0 -> png)\n" +
                    "Currently set to [white]${jpegQuality}${if(jpegQuality == 0f)" (png)" else ""}[].")
            return@register
        }
        try {
            val quality = args[0].toFloat()
            if (quality !in 0f .. 1f) {
                player.sendMessage("[scarlet]Please enter a number between 0.0 and 1.0 (please)")
                return@register
            }
            jpegQuality = quality
            Core.settings.put("commpicquality", quality)
            player.sendMessage("[accent]Set quality to [white]${quality}${if(quality == 0f)" (png)" else ""}[].")
        } catch (e: Exception) {
            Log.err(e)
            if (e is NumberFormatException) player.sendMessage("[scarlet]Please enter a valid number (please)")
            else player.sendMessage("[scarlet]Something went wrong.")
        }
    }

    registerReplace("%", "c", "cursor") {
        Strings.format("(@, @)", control.input.rawTileX(), control.input.rawTileY())
    }

    registerReplace("%", "s", "shrug") {
        "¯\\_(ツ)_/¯"
    }

    registerReplace("%", "h", "here") {
        Strings.format("(@, @)", player.tileX(), player.tileY())
    }

    //TOOD: add various % for gamerules
}

fun registerReplace(symbol: String = "%", vararg cmds: String, runner: Prov<String>) {
    cmds.forEach { registerReplace(symbol, it, runner) }
}

fun registerReplace(symbol: String = "%", cmd: String, runner: Prov<String>) {
    if(symbol.length != 1) throw IllegalArgumentException("Bad symbol in replace command")
    val seq = containsCommandHandler.get(symbol) { Seq() }
    seq.add(Pair(cmd, runner))
    seq.sort(Structs.comparingInt{ -it.first.length })
}

fun replaceMsg(match: String, matchRegex: Boolean, from: String, fromRegex: Boolean, to: String){
    clientThread.post {
        var matchReg = Regex("No. Something went wrong.")
        var fromReg = Regex("No. Something went wrong.")
        if(matchRegex) matchReg = match.toRegex()
        if(fromRegex) fromReg = from.toRegex()
        var num = 0
        val seq = player.team().data().buildings.copy()
        seq.each<MessageBlock.MessageBuild>({ it.team() == player.team() && it is MessageBlock.MessageBuild}, {
            val msg = it.message.toString()
            if((!matchRegex && !msg.contains(match)) || (matchRegex && !matchReg.matches(msg))) return@each
            val msg2 = if(fromRegex) msg.replace(fromReg, to)
            else msg.replace(from, to)
            configs.add(ConfigRequest(it.tileX(), it.tileY(), msg2))
            num++
        })
        player.sendMessage("[accent]Queued $num messages for editing");
    }
}