package dev.ensisdev.enspillars.api.events

import dev.ensisdev.enspillars.game.Arena
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.event.entity.EntityDamageEvent

/** Oyuncu elendiğinde ateşlenir. Kazanan null olabilir (void/ayrılma). */
class PlayerEliminationEvent(
    val arena: Arena,
    val player: Player,
    val killer: Player?,
    val cause: EntityDamageEvent.DamageCause?
) : Event() {
    override fun getHandlers() = HANDLERS
    companion object { @JvmStatic val HANDLERS = HandlerList(); @JvmStatic fun getHandlerList() = HANDLERS }
}

/** Maç bittiğinde ateşlenir. Kazanan null ise beraberedir. */
class WinEvent(val arena: Arena, val winner: Player?) : Event() {
    override fun getHandlers() = HANDLERS
    companion object { @JvmStatic val HANDLERS = HandlerList(); @JvmStatic fun getHandlerList() = HANDLERS }
}
