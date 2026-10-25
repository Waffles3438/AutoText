package me.redth.autotext.config

import java.util.UUID
import me.redth.autotext.core.MacroBinding

data class Macro(
    var id: String = UUID.randomUUID().toString(),
    var name: String = "New macro",
    var text: String = "",
    var enabled: Boolean = true,
    var binding: MacroBinding = MacroBinding(),
) {
    fun deepCopy(
        id: String = this.id,
        name: String = this.name,
    ): Macro = Macro(id, name, text, enabled, binding.deepCopy())
}
