@file:Suppress("EnumEntryName") @file:JvmName("CustomMode")

package mindustry.client.utils

import arc.*
import arc.files.*
import arc.util.*
import mindustry.Vars.*
import mindustry.client.ui.*
import mindustry.game.EventType.*
import mindustry.mod.*
import java.lang.reflect.*
import kotlin.properties.*

enum class CustomMode(
    val modeName: String? = null // Override the name of the mode
) {
    none,
    flood {
        val floodCompatRepo = "mindustry-antigrief/FloodCompat"
        var hasLoaded = false

        override fun enable() {
            super.enable()
            if ((IO() || Corium()) && net.client()) {
                var floodMod: Mods.LoadedMod? = mods.getMod("floodcompat")

                fun enable() { // Just enables the mod
                    if (hasLoaded) return // Only attempt to enable the mod once
                    hasLoaded = true

                    Log.warn("FloodCompat installed but disabled. Foo's will load it at runtime.")

                    mods.mods.remove(floodMod)
                    floodMod!!.dispose()
                    Core.settings.put("mod-floodcompat-enabled", true) // Has to be enabled for the mod to load
                    val mod = Reflect.invoke<Mods.LoadedMod>(mods, "loadMod", arrayOf(floodMod!!.file), Fi::class.java) // Load the mod and call the init() function
                    mod.main.init()
                    mods.buildFiles(mod)
                    mods.loadBundles()
                    // Next 5 lines sort the new mod as if it were enabled without actually keeping it enabled after a restart
                    mod.state = Mods.ModState.enabled
                    mods.mods.add(mod)
                    Reflect.invoke<Void>(mods, "sortMods")
                    Reflect.set(mods, "lastOrderedMods", null) // Reset orderedMods cache
                    Core.settings.put("mod-floodcompat-enabled", false) // May as well disable it as it was before
                }

                fun download(update: Boolean = false) { // Downloads and enables the mod
                    Toast(3f).add(if (update) "Updating" else "Installing" + " FloodCompat")
                    Log.debug(if (update) "Updating" else "Installing" + " FloodCompat")
                    ui.mods.githubImportMod(floodCompatRepo, true, null, false, floodMod?.meta?.version) {
                        val new = mods.mods.last { it.name == "floodcompat"} // newly downloaded flood compat if any
                        val installed = !update || new != floodMod
                        if (update && installed) { // Delete old flood mod for update. If new == old, there was no update.
                            floodMod!!.file.deleteDirectory()
                            floodMod!!.dispose()
                            mods.mods.remove(floodMod)
                        }
                        val reload = Reflect.get<Boolean>(mods, "requiresReload")
                        Reflect.set(mods, "requiresReload", reload)
                        if (installed) Toast(3f).add("FloodCompat " + if (update) "updated" else "installed" + " successfully!")
                        Core.settings.put("mod-floodcompat-enabled", false) // Set as disabled as there's no reason to load it outside of flood gamemode
                        floodMod = mods.getMod("floodcompat") // floodMod is still null from before, set it to the mod we just downloaded
                        enable()
                    }
                }

                if (floodMod === null) {
                    ui.showConfirm("[scarlet]FloodCompat mod not found!", "Installing the [accent]${floodCompatRepo}[] mod is recommended for a better game experience. Would you like to install it?\nThis will not require a restart.") {
                        Toast(3f).add("Downloading mod")
                        download()
                    }
                } else if (!floodMod.enabled()) {
                    if (!hasLoaded && Time.timeSinceMillis(Core.settings.getLong("lastfloodcompatupdate")) > 1000 * 60 * 30L) { // Update floodCompat every 30m
                        Core.settings.put("lastfloodcompatupdate", Time.millis())
                        (floodMod.root as? ZipFi)?.delete() // Close the current flood zip just in case it's open somehow (it should not be)
                        download(true)
                    } else enable() // Enable the mod as normal otherwise
                }
            }
        }
    },
    defense(modeName = "tower defense");

    companion object {
        @JvmStatic var current by Delegates.observable(none) { _, oldValue, newValue ->
            if (oldValue == newValue) return@observable // This can happen.
            Log.debug("Swapping custom gamemode from $oldValue to $newValue")
            oldValue.disable()
            newValue.enable()
        }

        init {
            Events.on(WorldLoadEvent::class.java) {
                var modeName = if (!net.client() || ui.join.lastHost?.modeName?.isBlank() != false) state.rules.modeName?.lowercase() else ui.join.lastHost.modeName.lowercase()
                if (modeName == "flood pvp") modeName = "flood" // lazy way to support floodpvp
                current = entries.find { (it.modeName ?: it.name) == modeName } ?: none // If modeName (or just the enum name if modeName is unspecified) matches, setup this mode
            }

            Events.on(MenuReturnEvent::class.java) {
                current = none
            }
        }

        private var defaults: MutableList<Any> = mutableListOf()

        /** Convenient way of adding multiple overwrites at once */
        private fun overwrites(vararg args: Any) =
            args.indices.step(3).forEach { overwrite(args[it], args[it + 1] as String, args[it + 2]) }

        private fun <O : Any, T : Any> overwrite(obj: O, name: String, value: T) {
            val split = name.split('.', limit = 2)
            val field = obj::class.java.getField(split[0])
            field.isAccessible = true

            // In the case of a string with periods, run the function recursively until we get to the last item which is then set
            if (split.size > 1) return overwrite(field.get(obj), split[1], value)

            defaults.add(obj)
            defaults.add(field)
            defaults.add(field.get(obj))
            field.set(obj, value)
        }
    }

    @JvmName("b") operator fun invoke() = CustomMode.current === this

    /** Called when this gamemode is detected */
    protected open fun enable() {
        defaults = mutableListOf()
    }

    /** Called when switching to a different gamemode */
    protected open fun disable() = // Don't have to worry about clearing defaults as it is replaced with a blank mutable list when the new gamemode is applied
        defaults.indices.step(3).forEach { (defaults[it + 1] as Field).set(defaults[it], defaults[it + 2]) } // (obj, field, value) -> field.set(obj, value)
}