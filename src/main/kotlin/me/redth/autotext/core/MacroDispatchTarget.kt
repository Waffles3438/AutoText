package me.redth.autotext.core

interface MacroDispatchTarget {
    fun sendMessage(message: String)
    fun sendCommand(command: String)
}
