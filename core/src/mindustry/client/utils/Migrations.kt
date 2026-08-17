package mindustry.client.utils

import arc.Core.*
import arc.input.*
import arc.struct.*
import arc.util.*
import mindustry.input.*
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

    private fun migration12() {
        // Many keybinds were split into multiple bindings
        // Update the new bindings based on the user's value of the old binding
        if (prevMigration > 1) {
            //this is really terrible but i cant be bothered to open arc again and it works
            settings.get("keybind-default-keyboard-ping-key", null)?.let {
                Binding.pingText.save()
                Binding.pingClear.save()
                val key = it as Int
                settings.put("keybind-default-keyboard-ping_text-key", key)
                settings.put("keybind-default-keyboard-ping_clear-key", key)
            }
            settings.get("keybind-default-keyboard-schematic_menu-key", null)?.let {
                Binding.schematicBrowser.save()
                val key = it as Int
                settings.put("keybind-default-keyboard-schematic_browser-key", key)
            }
            settings.get("keybind-default-keyboard-select_all_units-key", null)?.let {
                Binding.selectReallyAllUnits.save()
                val key = it as Int
                settings.put("keybind-default-keyboard-select_really_all_units-key", key)
            }
            settings.get("keybind-default-keyboard-navigate_to_cursor-key", null)?.let {
                Binding.viewChatPosition.save()
                Binding.viewWarnPosition.save()
                val key = it as Int
                settings.put("keybind-default-keyboard-view_warn_position-key", key)
                settings.put("keybind-default-keyboard-view_chat_position-key", key)
            }
            settings.get("keybind-default-keyboard-show_turret_ranges-key", null)?.let {
                Binding.showOverdriveRanges.save()
                Binding.showAlliedTurretRanges.save()
                Binding.showInvertedTurretRanges.save()
                val key = it as Int
                settings.put("keybind-default-keyboard-show_overdrive_ranges-key", key)
                settings.put("keybind-default-keyboard-show_allied_turret_ranges-key", key)
                settings.put("keybind-default-keyboard-show_inverted_turret_ranges-key", key)
            }
            settings.get("keybind-default-keyboard-hide_blocks-key", null)?.let {
                Binding.hidePlans.save()
                val key = it as Int
                settings.put("keybind-default-keyboard-hide_plans-key", key)
            }
            settings.get("keybind-default-keyboard-invisible_units-key", null)?.let {
                Binding.invisibleAirUnits.save()
                val key = it as Int
                settings.put("keybind-default-keyboard-invisible_air_units-key", key)
            }
            settings.get("keybind-default-keyboard-auto_build-key", null)?.let {
                Binding.sortBuildPlans.save()
                val key = it as Int
                settings.put("keybind-default-keyboard-sort_build_plans-key", key)
            }
            settings.get("keybind-default-keyboard-toggle_auto_target-key", null)?.let {
                Binding.autoTransfer.save()
                val key = it as Int
                settings.put("keybind-default-keyboard-auto_transfer-key", key)
            }
            settings.get("keybind-default-keyboard-pause_building-key", null)?.let {
                Binding.toggleFreezeQueueing.save()
                Binding.flushFrozenPlans.save()
                val key = it as Int
                settings.put("keybind-default-keyboard-toggle_freeze_queueing-key", key)
                settings.put("keybind-default-keyboard-flush_frozen_plans-key", key)
            }
            settings.get("keybind-default-keyboard-clear_building-key", null)?.let {
                Binding.clearFrozenPlans.save()
                val key = it as Int
                settings.put("keybind-default-keyboard-clear_frozen_plans-key", key)
            }
            for(bind in KeyBind.all){
                bind.load();
            }
        }
    }
    private fun migration13(){
        if(prevMigration > 1){
            settings.getInt("keybind-default-keyboard-find_modifier-key", KeyCode.controlLeft.ordinal).let {
                Binding.find.value.modifiers = arrayOf(KeyCode.byOrdinal(it))
                Binding.find.save()
            }
        }
    }

    private fun migration14(){
        if(prevMigration > 1){
            settings.remove("blockiotutorial")
        }
    }
}