package me.redth.autotext.core

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.redth.autotext.config.Macro

/** Versioned storage for macro data kept in OneConfig's normal profile file. */
object MacroCodec {
    private const val CURRENT_VERSION = 1
    const val EMPTY_PAYLOAD = "{\"version\":1,\"macros\":[]}"
    const val MAX_PAYLOAD_LENGTH = 1_048_576
    const val MAX_MACROS = 100
    const val MAX_ID_LENGTH = 128
    const val MAX_NAME_LENGTH = 48
    const val MAX_TEXT_LENGTH = 1024
    const val MAX_BINDING_INPUTS = 16
    const val MAX_KEY_CODE = 512
    const val MAX_MOUSE_BUTTON = 31

    fun encode(macros: List<Macro>): String {
        require(macros.size <= MAX_MACROS) { "Too many macros" }
        val root = JsonObject()
        root.addProperty("version", CURRENT_VERSION)
        root.add("macros", JsonArray().apply {
            macros.forEach { macro ->
                require(macro.id.length <= MAX_ID_LENGTH)
                require(macro.name.length <= MAX_NAME_LENGTH)
                require(macro.text.length <= MAX_TEXT_LENGTH)
                require(macro.binding.keys.size <= MAX_BINDING_INPUTS)
                require(macro.binding.mouseButtons.size <= MAX_BINDING_INPUTS)
                require(macro.binding.keys.all { it in 1..MAX_KEY_CODE })
                require(macro.binding.mouseButtons.all { it in 0..MAX_MOUSE_BUTTON })
                require(macro.binding.modifiers.toInt() in 0..15)

                add(JsonObject().apply {
                    addProperty("id", macro.id)
                    addProperty("name", macro.name)
                    addProperty("text", macro.text)
                    addProperty("enabled", macro.enabled)
                    add("binding", JsonObject().apply {
                        add("keys", macro.binding.keys.toJsonArray())
                        add("mouseButtons", macro.binding.mouseButtons.toJsonArray())
                        addProperty("modifiers", macro.binding.modifiers.toInt())
                    })
                })
            }
        })
        return root.toString().also { require(it.length <= MAX_PAYLOAD_LENGTH) }
    }

    fun decode(payload: String): List<Macro>? {
        if (payload.length > MAX_PAYLOAD_LENGTH) return null
        if (payload.isBlank()) return emptyList()

        return runCatching {
            val root = JsonParser().parse(payload).takeIf { it.isJsonObject }?.asJsonObject
                ?: return@runCatching null
            if (root.get("version")?.asInt != CURRENT_VERSION) return@runCatching null
            val values = root.get("macros")?.takeIf { it.isJsonArray }?.asJsonArray
                ?: return@runCatching null
            if (values.size() > MAX_MACROS) return@runCatching null

            values.map { element ->
                val obj = element.takeIf { it.isJsonObject }?.asJsonObject
                    ?: error("Macro must be an object")
                val binding = obj.get("binding")?.takeIf { it.isJsonObject }?.asJsonObject
                    ?: JsonObject()
                val keys = readInputs(binding, "keys", 1..MAX_KEY_CODE)
                val mouseButtons = readInputs(binding, "mouseButtons", 0..MAX_MOUSE_BUTTON)
                val modifiers = binding.get("modifiers")?.asInt ?: 0
                require(modifiers in 0..15)

                Macro(
                    id = obj.string("id", ""),
                    name = obj.string("name", "New macro"),
                    text = obj.string("text", ""),
                    enabled = obj.get("enabled")?.asBoolean ?: false,
                    binding = MacroBinding(keys, mouseButtons, modifiers.toByte()),
                )
            }
        }.getOrNull()
    }

    private fun JsonObject.string(name: String, fallback: String): String {
        val value = get(name) ?: return fallback
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
        return value.asString
    }

    private fun readInputs(obj: JsonObject, name: String, validCodes: IntRange): IntArray {
        val value = obj.get(name) ?: return intArrayOf()
        require(value.isJsonArray && value.asJsonArray.size() <= MAX_BINDING_INPUTS)
        return value.asJsonArray.map {
            require(it.isJsonPrimitive && it.asJsonPrimitive.isNumber)
            it.asInt.also { code -> require(code in validCodes) }
        }.distinct().toIntArray()
    }

    private fun IntArray.toJsonArray(): JsonArray = JsonArray().also { array ->
        forEach(array::add)
    }
}
