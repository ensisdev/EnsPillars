package dev.ensisdev.enspillars.api

import dev.ensisdev.enspillars.game.Arena

/** Stable public API surface. Implementations remain internal to the plugin. */
interface EnsPillarsAPI {
    fun arenas(): Collection<ArenaSnapshot>
    fun arena(id: String): ArenaSnapshot?
    fun version(): String
}
