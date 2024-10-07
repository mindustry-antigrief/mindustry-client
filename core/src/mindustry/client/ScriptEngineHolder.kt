package mindustry.client

import javax.script.ScriptEngine
import javax.script.ScriptEngineManager

object ScriptEngineHolder {
    val kts: ScriptEngine? by lazy { ScriptEngineManager().getEngineByExtension("kts") }
}