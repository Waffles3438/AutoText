package me.redth.autotext

import me.redth.autotext.client.AutoTextRuntime
import me.redth.autotext.client.MacroEditorTree
import me.redth.autotext.config.ModConfig
import net.fabricmc.api.ClientModInitializer
import org.polyfrost.oneconfig.api.config.v1.ConfigManager
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import org.polyfrost.oneconfig.api.event.v1.events.WorldEvent

object AutoText : ClientModInitializer {
    const val MODID = "autotext"
    const val NAME = "AutoText"

    override fun onInitializeClient() {
        ModConfig.preload()
        MacroEditorTree.syncRegistry()
        ConfigManager.addProfileChangeListener { AutoTextRuntime.onProfileChanged() }
        AutoTextRuntime.initialize()

        eventHandler { _: TickEvent.Start -> AutoTextRuntime.beginTick() }
        eventHandler { _: TickEvent.End -> AutoTextRuntime.syncIfChanged() }
        eventHandler { _: WorldEvent.Load -> AutoTextRuntime.clearSession() }
        eventHandler { _: WorldEvent.Unload -> AutoTextRuntime.clearSession() }
    }
}
