package me.redth.autotext.config

import java.util.UUID
import me.redth.autotext.AutoText
import me.redth.autotext.client.MacroEditorTree
import me.redth.autotext.core.MacroBinding
import me.redth.autotext.core.MacroCodec
import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.ConfigManager
import org.polyfrost.oneconfig.api.config.v1.Tree

object ModConfig : Config("autotext.json", AutoText.NAME, Category.QOL) {
    @JvmField
    @Volatile
    var enabled = true

    @JvmField
    @Volatile
    var cooldownMillis = 750f

    @JvmField
    @Volatile
    var blockedActionNotifications = true

    @JvmField
    @Volatile
    var macrosJson = MacroCodec.EMPTY_PAYLOAD

    private val stateLock = Any()
    @Volatile
    private var state: List<Macro> = emptyList()

    /**
     * Bumped only when macros are added, removed, or reloaded. The settings tree is rebuilt on this
     * revision, so plain field edits must not touch it or the open accordion collapses mid-typing.
     */
    @Volatile
    var structureRevision: Long = 0L
        private set

    /** Bumped when a macro's id, enabled flag, or keybind changes, so keybinds get re-registered. */
    @Volatile
    var runtimeRevision: Long = 0L
        private set

    override fun initialize(byConfigManager: Boolean) {
        super.initialize(byConfigManager)
        synchronized(stateLock) {
            val decoded = MacroCodec.decode(macrosJson)
            state = normalize(decoded ?: emptyList())
            macrosJson = MacroCodec.encode(state)
            structureRevision++
            runtimeRevision++
        }
    }

    override fun makeTree(): Tree = MacroEditorTree.create()

    fun snapshot(): List<Macro> = synchronized(stateLock) {
        state.map { it.deepCopy() }
    }

    fun find(id: String): Macro? = synchronized(stateLock) {
        state.firstOrNull { it.id == id }?.deepCopy()
    }

    fun addMacro(): Macro? {
        val added: Macro
        val treeToSave: Tree?
        synchronized(stateLock) {
            if (state.size >= MacroCodec.MAX_MACROS) return null
            val index = state.size + 1
            added = Macro(name = "Macro $index")
            treeToSave = publishLocked(state + added, structural = true)
        }
        save(treeToSave)
        return added.deepCopy()
    }

    fun updateMacro(id: String, update: (Macro) -> Unit): Boolean {
        val treeToSave: Tree?
        synchronized(stateLock) {
            val index = state.indexOfFirst { it.id == id }
            if (index < 0) return false
            val draft = state[index].deepCopy()
            update(draft)
            val values = state.toMutableList()
            values[index] = draft
            treeToSave = publishLocked(values, structural = false)
        }
        save(treeToSave)
        return true
    }

    fun deleteMacro(id: String): Boolean {
        val treeToSave: Tree?
        synchronized(stateLock) {
            if (state.none { it.id == id }) return false
            treeToSave = publishLocked(state.filterNot { it.id == id }, structural = true)
        }
        save(treeToSave)
        return true
    }

    private fun publishLocked(values: List<Macro>, structural: Boolean): Tree? {
        val previous = state
        val next = normalize(values)
        state = next
        macrosJson = MacroCodec.encode(next)
        if (structural) structureRevision++
        if (!sameRuntimeShape(previous, next)) runtimeRevision++
        return tree
    }

    private fun sameRuntimeShape(first: List<Macro>, second: List<Macro>): Boolean =
        first.size == second.size && first.indices.all { i ->
            val a = first[i]
            val b = second[i]
            a.id == b.id && a.enabled == b.enabled && a.binding.fingerprint() == b.binding.fingerprint()
        }

    private fun save(treeToSave: Tree?) {
        treeToSave ?: return
        ConfigManager.active().save(treeToSave)
    }

    private fun normalize(values: List<Macro>): List<Macro> {
        val usedIds = HashSet<String>()
        return values.take(MacroCodec.MAX_MACROS).map { source ->
            source.deepCopy().apply {
                id = id.takeIf { it.isNotBlank() && it.length <= MacroCodec.MAX_ID_LENGTH && usedIds.add(it) }
                    ?: generateSequence { UUID.randomUUID().toString() }.first { usedIds.add(it) }
                name = truncate(name, MacroCodec.MAX_NAME_LENGTH)
                text = truncate(text, MacroCodec.MAX_TEXT_LENGTH)
                binding = MacroBinding(
                    binding.keys.asSequence()
                        .filter { it in 1..MacroCodec.MAX_KEY_CODE }
                        .distinct().take(MacroCodec.MAX_BINDING_INPUTS).toList().toIntArray(),
                    binding.mouseButtons.asSequence()
                        .filter { it in 0..MacroCodec.MAX_MOUSE_BUTTON }
                        .distinct().take(MacroCodec.MAX_BINDING_INPUTS).toList().toIntArray(),
                    (binding.modifiers.toInt() and 15).toByte(),
                )
            }
        }
    }

    private fun truncate(value: String, maxLength: Int): String =
        if (value.length <= maxLength) value else value.substring(0, maxLength)
}
