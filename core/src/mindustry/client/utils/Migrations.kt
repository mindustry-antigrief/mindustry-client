package mindustry.client.utils

import arc.Core.*
import arc.struct.*
import arc.util.*
import mindustry.type.*

@Suppress("unused")
/** Allows for simple migrations between versions of the client. */
class Migrations {
    val prevMigration = settings.getInt("foomigration", 1) // Starts at 1

    fun runMigrations() {
        val start = Time.nanos()
        val functions = this::class.java.declaredMethods // Cached function list. Using kotlin reflection to find functions is extremely slow.
        var migration = prevMigration
        while (true) {
            val migrateFun = functions.find { it.name == "migration$migration" } ?: break // Find next migration or break
            Log.debug("Running foo's migration $migration")
            migrateFun.isAccessible = true
            migrateFun.invoke(this)
            migrateFun.isAccessible = false
            Log.debug("Finished running foo's migration $migration")
            migration++
        }
        if (prevMigration != migration) settings.put("foomigration", migration) // Avoids saving settings if the value remains the same
        Log.debug("${migration - prevMigration} migrations ran in ${Time.millisSinceNanos(start)}ms.")
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
        // NOOP: Code changed relating to keybinds and this migration is 2 years old so its not even worth fixing.
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

    private fun migration10() {
        // Previous bug put mapautosavetime between 0 and 10, thus spamming autosaves
        // Set to 3600 (default minimum) if they have been affected
        if (settings.getInt("mapautosavetime", Integer.MAX_VALUE) <= 10)
            settings.put("mapautosavetime", 3600)
        settings.remove("displaydef") // Unrelated to above, but also remove
    }

    private fun migration11() {
        // Transfer range opacity setting default was changed from 30 to 0
        // This ensures people who have kept it at 30 wouldn't have their setting changed
        if (prevMigration > 1 && !settings.has("transferrangeopacity")) {
            settings.put("transferrangeopacity", 30)
        }
    }
}