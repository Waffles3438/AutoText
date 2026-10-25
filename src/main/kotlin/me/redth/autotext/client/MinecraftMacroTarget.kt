package me.redth.autotext.client

import me.redth.autotext.core.MacroDispatchTarget
import net.minecraft.client.Minecraft

object MinecraftMacroTarget : MacroDispatchTarget {
    override fun sendMessage(message: String) {
        Minecraft.getInstance().player?.sendChat(message)
    }

    override fun sendCommand(command: String) {
        Minecraft.getInstance().player?.sendChat("/$command")
    }
}
