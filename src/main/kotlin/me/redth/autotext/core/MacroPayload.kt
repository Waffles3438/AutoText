package me.redth.autotext.core

sealed interface MacroPayload {
    data class Command(val value: String) : MacroPayload
    data class Message(val value: String) : MacroPayload
}

sealed interface PayloadValidation {
    data class Invalid(val reason: Reason) : PayloadValidation
    data class Valid(val payload: MacroPayload) : PayloadValidation

    enum class Reason {
        EMPTY,
        CONTROL_CHARACTER,
        TOO_LONG,
        EMPTY_COMMAND,
    }
}

object MacroPayloadParser {
    const val MAX_CHAT_LENGTH = 256

    fun parse(raw: String): PayloadValidation {
        if (raw.any {
                it == '\u00a7' || it == '\u0000' || it == '\r' || it == '\n' || Character.isISOControl(it)
            }) {
            return PayloadValidation.Invalid(PayloadValidation.Reason.CONTROL_CHARACTER)
        }

        val normalized = normalizeSpaces(raw)
        if (normalized.isEmpty()) return PayloadValidation.Invalid(PayloadValidation.Reason.EMPTY)
        if (normalized.length > MAX_CHAT_LENGTH) return PayloadValidation.Invalid(PayloadValidation.Reason.TOO_LONG)

        if (!normalized.startsWith('/')) return PayloadValidation.Valid(MacroPayload.Message(normalized))
        val command = normalized.substring(1)
        return if (command.isBlank()) {
            PayloadValidation.Invalid(PayloadValidation.Reason.EMPTY_COMMAND)
        } else {
            PayloadValidation.Valid(MacroPayload.Command(command))
        }
    }

    private fun normalizeSpaces(value: String): String {
        val out = StringBuilder(value.length)
        var pendingSpace = false
        value.forEach { char ->
            if (!char.isWhitespace() && !Character.isSpaceChar(char)) {
                if (pendingSpace) out.append(' ')
                out.append(char)
                pendingSpace = false
            } else if (out.isNotEmpty()) {
                pendingSpace = true
            }
        }
        return out.toString()
    }
}
