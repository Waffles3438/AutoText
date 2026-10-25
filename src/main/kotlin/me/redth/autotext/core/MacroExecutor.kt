package me.redth.autotext.core

import me.redth.autotext.config.Macro

class MacroExecutor(
    private val target: MacroDispatchTarget,
    private val clock: MonotonicClock = MonotonicClock { System.nanoTime() / 1_000_000L },
) {
    private val lastDispatch = HashMap<String, Long>()
    private var actionCompletedThisTick = false

    fun trigger(
        macro: Macro,
        context: MacroContext,
        globallyEnabled: Boolean,
        conflictingBind: Boolean,
        cooldownMillis: Long,
    ): TriggerResult {
        if (!globallyEnabled || !macro.enabled) return TriggerResult.DISABLED
        if (conflictingBind) return TriggerResult.CONFLICTING_BIND
        if (!context.playerReady || !context.connectionReady) return TriggerResult.NOT_IN_GAME
        if (context.screenOpen) return TriggerResult.SCREEN_OPEN
        if (actionCompletedThisTick) return TriggerResult.TICK_LIMIT

        val parsed = MacroPayloadParser.parse(macro.text)
        if (parsed !is PayloadValidation.Valid) return TriggerResult.INVALID

        val now = clock.nowMillis()
        val previous = lastDispatch[macro.id]
        if (previous != null && now - previous < cooldownMillis.coerceAtLeast(0L)) {
            return TriggerResult.COOLDOWN
        }

        when (val payload = parsed.payload) {
            is MacroPayload.Message -> target.sendMessage(payload.value)
            is MacroPayload.Command -> target.sendCommand(payload.value)
        }
        lastDispatch[macro.id] = now
        actionCompletedThisTick = true
        return TriggerResult.DISPATCHED
    }

    fun beginTick() {
        actionCompletedThisTick = false
    }

    fun clearSession() {
        lastDispatch.clear()
        actionCompletedThisTick = false
    }
}
