package me.redth.autotext.client

import me.redth.autotext.config.Macro
import me.redth.autotext.config.ModConfig
import me.redth.autotext.core.KeybindReleaseGate
import me.redth.autotext.core.MacroContext
import me.redth.autotext.core.MacroExecutor
import me.redth.autotext.core.TriggerResult
import me.redth.autotext.core.bindingInputsDown
import me.redth.autotext.core.bindingMatchesExactly
import me.redth.autotext.core.hasUnexpectedModifiers
import net.minecraft.client.Minecraft
import org.polyfrost.oneconfig.api.notifications.v1.Notifications
import org.polyfrost.oneconfig.api.ui.v1.keybind.KeybindManager
import org.polyfrost.oneconfig.api.ui.v1.keybind.OneConfigKeybind

object AutoTextRuntime {
    private val executor = MacroExecutor(MinecraftMacroTarget)
    private val registered = LinkedHashMap<String, OneConfigKeybind>()
    private var conflictingIds: Set<String> = emptySet()
    private var observedRevision = Long.MIN_VALUE
    private var registrationEpoch = 0L
    private var lastBlockedNoticeAt = 0L

    fun initialize() = refreshBindings()

    fun refreshBindings() {
        registrationEpoch++
        val epoch = registrationEpoch
        val snapshot = ModConfig.snapshot()
        registered.values.forEach(KeybindManager::unregister)
        registered.clear()
        conflictingIds = findConflicts(snapshot)

        snapshot.forEach { macro ->
            if (macro.enabled && macro.binding.isBound) {
                val binding = macro.binding.deepCopy()
                val keybind = ExactModifierBind(
                    binding.keys.clone(),
                    binding.mouseButtons.clone(),
                    binding.modifiers,
                    0L,
                ) { pressed ->
                    if (pressed) trigger(macro.id, binding.fingerprint(), epoch)
                    true
                }
                registered[macro.id] = KeybindManager.register(keybind)
            }
        }
        observedRevision = ModConfig.revision
    }

    fun syncIfChanged() {
        if (ModConfig.revision != observedRevision) {
            executor.clearSession()
            refreshBindings()
            MacroEditorTree.syncRegistry()
        }
    }

    fun onProfileChanged() {
        executor.clearSession()
        refreshBindings()
        MacroEditorTree.syncRegistry()
    }

    fun beginTick() = executor.beginTick()

    fun clearSession() = executor.clearSession()

    private fun trigger(id: String, expectedBinding: String, callbackEpoch: Long) {
        if (callbackEpoch != registrationEpoch) return
        if (ModConfig.revision != observedRevision) {
            refreshBindings()
            return
        }
        if (id in conflictingIds) {
            notifyBlocked(TriggerResult.CONFLICTING_BIND)
            return
        }

        val macro = ModConfig.find(id) ?: return
        if (macro.binding.fingerprint() != expectedBinding) return
        val minecraft = Minecraft.getInstance()
        val result = executor.trigger(
            macro = macro,
            context = MacroContext(
                playerReady = minecraft.player != null,
                connectionReady = minecraft.world != null,
                screenOpen = minecraft.screen != null,
            ),
            globallyEnabled = ModConfig.enabled,
            conflictingBind = false,
            cooldownMillis = ModConfig.cooldownMillis.toLong(),
        )
        notifyBlocked(result)
    }

    private fun notifyBlocked(result: TriggerResult) {
        if (!ModConfig.blockedActionNotifications) return
        val message = when (result) {
            TriggerResult.INVALID -> "This macro has no valid message or command."
            TriggerResult.CONFLICTING_BIND -> "Two enabled macros use the same keybind."
            else -> return
        }
        val now = System.currentTimeMillis()
        if (now - lastBlockedNoticeAt < 2000L) return
        lastBlockedNoticeAt = now
        Notifications.info("AutoText action blocked", message)
    }

    private fun findConflicts(macros: List<Macro>): Set<String> {
        val groups = LinkedHashMap<String, MutableList<String>>()
        macros.forEach { macro ->
            if (macro.enabled && macro.binding.isBound) {
                groups.getOrPut(macro.binding.fingerprint()) { ArrayList() }.add(macro.id)
            }
        }
        return groups.values.filter { it.size > 1 }.flatten().toSet()
    }

    private class ExactModifierBind(
        keyCodes: IntArray?,
        mouseBtns: IntArray?,
        mods: Byte,
        durationNanos: Long,
        action: (Boolean) -> Boolean,
    ) : OneConfigKeybind(keyCodes, mouseBtns, mods, durationNanos, action) {
        private val releaseGate = KeybindReleaseGate()

        override fun test(downKeys: Set<Int>, downMouse: Set<Int>, currentMods: Byte): Boolean {
            val inputsDown = bindingInputsDown(keyCodes, mouseBtns, downKeys, downMouse)
            val screenOpen = Minecraft.getInstance().screen != null
            val matches = bindingMatchesExactly(mods, keyCodes, mouseBtns, downKeys, downMouse) && !screenOpen
            val blockCurrentPress = screenOpen || hasUnexpectedModifiers(mods, keyCodes, downKeys)
            return releaseGate.allow(inputsDown, matches, blockCurrentPress)
        }

        override fun copyWith(keyCodes: IntArray?, mouseBtns: IntArray?, mods: Byte): OneConfigKeybind =
            ExactModifierBind(keyCodes, mouseBtns, mods, durationNanos, action).also {
                it.name = name
                it.category = category
                it.defaultKeybind = defaultKeybind
            }
    }
}
