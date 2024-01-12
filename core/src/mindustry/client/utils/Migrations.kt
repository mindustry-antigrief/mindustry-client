package mindustry.client.utils

import arc.*
import arc.Core.*
import arc.input.*
import arc.struct.*
import arc.util.*
import mindustry.input.*
import mindustry.type.*

@Suppress("unused")
/** Allows for simple migrations between versions of the client. */
class Migrations {
    fun runMigrations() {
        val functions = this::class.java.declaredMethods // Cached function list. Using kotlin reflection to find functions is extremely slow.
        var migration = settings.getInt("foomigration", 1) // Starts at 1
        while (true) {
            val migrateFun = functions.find { it.name == "migration$migration" } ?: break // Find next migration or break
            Log.debug("Running foo's migration $migration")
            migrateFun.isAccessible = true
            migrateFun.invoke(this)
            migrateFun.isAccessible = false
            Log.debug("Finished running foo's migration $migration")
            migration++
        }
        if (settings.getInt("foomigration", 1) != migration) settings.put("foomigration", migration) // Avoids saving settings if the value remains the same
    }

    private fun migration1() { // All of the migrations from before the existence of the migration system
        // Various setting names and formats have changed
        if (settings.has("gameovertext")) {
            if (settings.getString("gameovertext").isNotBlank()) settings.put("gamewintext", settings.getString("gameovertext"))
            settings.remove("gameovertext")
        }
        if (settings.has("graphdisplay")) {
            if (settings.getBool("graphdisplay")) settings.put("highlighthoveredgraph", true)
            settings.remove("graphdisplay")
        }
        if (settings.getBool("drawhitboxes") && settings.getInt("hitboxopacity") == 0) { // Old setting was enabled and new opacity hasn't been set yet
            settings.put("hitboxopacity", 30)
            UnitType.hitboxAlpha = settings.getInt("hitboxopacity") / 100f
        }

        // Old settings that no longer exist
        settings.remove("drawhitboxes")
        settings.remove("signmessages")
        settings.remove("firescl")
        settings.remove("effectscl")
        settings.remove("commandwarnings")
        settings.remove("nodeconfigs")
        settings.remove("attemwarfarewhisper")
    }

    private fun migration2() { // Lowercased the pingExecutorThreads setting name
        if (!settings.has("pingExecutorThreads")) return
        settings.put("pingexecutorthreads", settings.getInt("pingExecutorThreads"))
        settings.remove("pingExecutorThreads")
    }

    private fun migration3() { // Finally changed Binding.navigate_to_camera to navigate_to_cursor
        InputDevice.DeviceType.values().forEach { device ->
            if (!settings.has("keybind-default-$device-navigate_to_camera-key")) return@forEach
            val saved = settings.getInt("keybind-default-$device-navigate_to_camera-key")
            settings.remove("keybind-default-$device-navigate_to_camera-key")
            settings.remove("keybind-default-$device-navigate_to_camera-single")
            keybinds.sections.first { it.name == "default" }.binds[device, ::OrderedMap].put(
                Binding.navigate_to_cursor,
                KeyBinds.Axis(KeyCode.byOrdinal(saved))
            )
        }
    }

    private fun migration4() = settings.remove("broadcastcoreattack") // Removed as it was super annoying

    private fun migration5() = settings.remove("disablemonofont") // Removed as it was made irrelevant long ago

    private fun migration6() = settings.remove("vanillamovement") // Removed as it actively broke when not connected to a server as a client

    private fun migration7() {
        if (settings.has("restrictschematicloading")) settings.put("schemloadtime", 10) // moved from restrictschematicloading which was hardcoded to 10ms to a new schemloadtime setting that is configurable
        settings.remove("restrictschematicloading")
        settings.remove("cnpw") // no longer needed as cn has updated their account system
    }

    private fun migration8() = settings.remove("schematicsearchdesc") // Now in form of search bar

    private fun migration9() {
        val s = Seq<String>()
        for (setting in settings.keys()) {
            if (setting.startsWith("ptext-")) s.add(setting)
        }
        s.forEach {
            settings.put(it.substring(1), settings.getString(it, ""))
            settings.remove(it)
        }
    }
}