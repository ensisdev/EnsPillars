package dev.ensisdev.enspillars.api.events

import dev.ensisdev.enspillars.api.GameState
import dev.ensisdev.enspillars.game.Arena
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/** Arena durum değiştirdiğinde ateşlenir. */
class ArenaStateChangeEvent(val arena: Arena, val from: GameState, val to: GameState) : Event() {
    override fun getHandlers() = HANDLERS
    companion object { @JvmStatic val HANDLERS = HandlerList(); @JvmStatic fun getHandlerList() = HANDLERS }
}
