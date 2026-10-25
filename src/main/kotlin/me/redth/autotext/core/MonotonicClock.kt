package me.redth.autotext.core

fun interface MonotonicClock {
    fun nowMillis(): Long
}
