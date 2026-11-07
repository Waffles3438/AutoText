package me.redth.autotext.client

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import me.redth.autotext.config.Macro
import me.redth.autotext.config.ModConfig
import me.redth.autotext.core.MacroBinding
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.ComponentLike
import org.polyfrost.oneconfig.api.config.v1.Properties
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.api.config.v1.Tree
import org.polyfrost.oneconfig.api.config.v1.Visualizer
import org.polyfrost.oneconfig.api.config.v1.Visualizer.ButtonVisualizer
import org.polyfrost.oneconfig.api.config.v1.Visualizer.KeybindVisualizer
import org.polyfrost.oneconfig.api.config.v1.Visualizer.SliderVisualizer
import org.polyfrost.oneconfig.api.config.v1.Visualizer.SwitchVisualizer
import org.polyfrost.oneconfig.api.config.v1.Visualizer.TextVisualizer
import org.polyfrost.oneconfig.api.ui.v1.keybind.OneConfigKeybind
import org.polyfrost.oneconfig.internal.ui.api.ConfigRegistry
import org.polyfrost.oneconfig.internal.ui.api.ConfigSource
import org.polyfrost.oneconfig.internal.ui.api.TreeConfigData

/**
 * Builds the OneConfig v1 tree for the variable-length macro list.
 *
 * The tree is only rebuilt when macros are added or removed. OneConfig keys each accordion's expanded
 * state to its tree node, so rebuilding on every field edit would collapse the macro being edited and
 * drop focus from its text box. Field edits update the model in place, and the accordion header uses
 * [LiveText] so the title and preview still follow what the user types.
 */
internal object MacroEditorTree {
    private const val CATEGORY = "Macros"
    private const val SUBCATEGORY = "General"
    private const val NO_MC_MIRROR = "oc_no_mc_mirror"

    private var installedTree: Tree? = null
    private var installedRevision = Long.MIN_VALUE

    /** Cheap when nothing changed: rebuilds only on a structural revision, re-registers only if replaced. */
    @Synchronized
    fun syncRegistry() {
        val revision = ModConfig.structureRevision
        val tree = installedTree?.takeIf { installedRevision == revision }
            ?: create().also {
                installedTree = it
                installedRevision = revision
            }

        if (ConfigRegistry.findTree(ModConfig.id) !== tree) {
            ConfigRegistry.register(TreeConfigData(tree, ConfigSource.OC))
        }
    }

    internal fun create(): Tree {
        val root = Tree(ModConfig.id, ModConfig.title, null, null)
        root.put(functional(
            id = "enabled",
            title = "Enabled",
            description = "Allow configured shortcuts to run.",
            type = Boolean::class.javaObjectType,
            getter = { ModConfig.enabled },
            setter = { ModConfig.enabled = it },
            visualizer = SwitchVisualizer::class.java,
        ))
        root.put(functional(
            id = "cooldownMillis",
            title = "Cooldown",
            description = "Minimum time between uses of the same macro.",
            type = Float::class.javaObjectType,
            getter = { ModConfig.cooldownMillis },
            setter = { ModConfig.cooldownMillis = it },
            visualizer = SliderVisualizer::class.java,
        ).apply {
            addMetadata("min", 0f)
            addMetadata("max", 5000f)
            addMetadata("step", 50f)
        })
        root.put(functional(
            id = "blockedActionNotifications",
            title = "Blocked-action notifications",
            description = "Show why an invalid or conflicting shortcut was blocked.",
            type = Boolean::class.javaObjectType,
            getter = { ModConfig.blockedActionNotifications },
            setter = { ModConfig.blockedActionNotifications = it },
            visualizer = SwitchVisualizer::class.java,
        ))
        root.put(functional(
            id = "macrosJson",
            type = String::class.java,
            getter = { ModConfig.macrosJson },
            setter = { ModConfig.macrosJson = it },
        ).apply { addMetadata("hidden", true) })

        root.put(action(
            id = "add_macro",
            title = "New macro",
            description = "Create a new message or command shortcut.",
            text = "Add",
        ) {
            if (ModConfig.addMacro() != null) syncRegistry()
        }.inMacroCategory())

        ModConfig.snapshot().forEachIndexed { index, macro ->
            root.put(buildMacro(macro, index).also {
                it.addMetadata("category", CATEGORY)
                it.addMetadata("subcategory", SUBCATEGORY)
            })
        }
        return root
    }

    private fun buildMacro(snapshot: Macro, index: Int): Tree {
        val id = snapshot.id
        var editorBinding = snapshot.binding.toOneConfigKeybind()
        val liveTitle = LiveText(displayName(snapshot.name, index))
        val livePreview = LiveText(preview(snapshot.text))

        return Tree("macro_$id", liveTitle, livePreview, null).apply {
            addMetadata("collapsed", true)
            put(functional(
                id = "enabled",
                title = "Enabled",
                description = "Allow this macro to run.",
                type = Boolean::class.javaObjectType,
                getter = { ModConfig.find(id)?.enabled == true },
                setter = { enabled -> ModConfig.updateMacro(id) { it.enabled = enabled } },
                visualizer = SwitchVisualizer::class.java,
            ))
            put(functional(
                id = "name",
                title = "Name",
                description = "Label shown in the macro list.",
                type = String::class.java,
                getter = { ModConfig.find(id)?.name ?: "" },
                setter = { value ->
                    if (ModConfig.updateMacro(id) { it.name = value }) {
                        liveTitle.value = displayName(ModConfig.find(id)?.name ?: value, index)
                    }
                },
                visualizer = TextVisualizer::class.java,
            ).apply { addMetadata("placeholder", "Macro name") })
            put(functional(
                id = "text",
                title = "Text",
                description = "Start with / to send a command. Up to 256 characters are sent.",
                type = String::class.java,
                getter = { ModConfig.find(id)?.text ?: "" },
                setter = { value ->
                    if (ModConfig.updateMacro(id) { it.text = value }) {
                        livePreview.value = preview(ModConfig.find(id)?.text ?: value)
                    }
                },
                visualizer = TextVisualizer::class.java,
            ).apply { addMetadata("placeholder", "Message or /command") })
            put(functional(
                id = "keybind",
                title = "Keybind",
                description = "One press runs this macro once.",
                type = OneConfigKeybind::class.java,
                getter = { editorBinding },
                setter = { binding ->
                    editorBinding = binding
                    ModConfig.updateMacro(id) { it.binding = binding.toMacroBinding() }
                },
                visualizer = KeybindVisualizer::class.java,
            ).apply { addMetadata(NO_MC_MIRROR, true) })
            put(action(
                id = "delete",
                title = "Delete",
                description = "Remove this macro.",
                text = "Delete",
            ) {
                if (ModConfig.deleteMacro(id)) syncRegistry()
            })
        }
    }

    private fun displayName(name: String, index: Int): String = name.ifBlank { "Macro ${index + 1}" }

    private fun preview(text: String): String =
        text.trim().replace(Regex("\\s+"), " ").take(80).ifBlank { "No text configured" }

    private fun <T> functional(
        id: String,
        title: String? = null,
        description: String? = null,
        type: Class<T>,
        getter: () -> T,
        setter: (T) -> Unit,
        visualizer: Class<out Visualizer>? = null,
    ): Property<T> = Properties.functional(
        { getter() },
        { value -> setter(value) },
        id,
        title,
        description,
        type,
    ).apply {
        visualizer?.let { addMetadata("visualizer", it) }
    }

    private fun action(
        id: String,
        title: String,
        description: String,
        text: String,
        run: () -> Unit,
    ): Property<Void> = Properties.dummy(id, title, description).apply {
        addMetadata("visualizer", ButtonVisualizer::class.java)
        addMetadata("text", text)
        addMetadata("runnable", Runnable { run() })
    }

    private fun <T : Property<*>> T.inMacroCategory(): T = apply {
        addMetadata("category", CATEGORY)
        addMetadata("subcategory", SUBCATEGORY)
    }

    private fun MacroBinding.toOneConfigKeybind(): OneConfigKeybind = OneConfigKeybind(
        keys.clone().takeIf { it.isNotEmpty() },
        mouseButtons.clone().takeIf { it.isNotEmpty() },
        modifiers,
        0L,
        { true },
    )

    private fun OneConfigKeybind.toMacroBinding(): MacroBinding = MacroBinding(
        keyCodes?.clone() ?: intArrayOf(),
        mouseBtns?.clone() ?: intArrayOf(),
        mods,
    )

    /** Header text backed by Compose state, so OneConfig recomposes it without a tree rebuild. */
    private class LiveText(initial: String) : ComponentLike {
        var value by mutableStateOf(initial)
        override fun asComponent(): Component = Component.text(value)
    }
}
