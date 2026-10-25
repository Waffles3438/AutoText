package me.redth.autotext.core

enum class TriggerResult {
    DISPATCHED,
    DISABLED,
    INVALID,
    NOT_IN_GAME,
    SCREEN_OPEN,
    CONFLICTING_BIND,
    COOLDOWN,
    TICK_LIMIT,
}
