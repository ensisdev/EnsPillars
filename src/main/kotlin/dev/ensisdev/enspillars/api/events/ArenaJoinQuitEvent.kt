package dev.ensisdev.enspillars.api.events

import dev.ensisdev.enspillars.game.Arena
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/** Oyuncu bir arenaya katıldığında ateşlenir. İptal edilebilir. */
class ArenaJoinEvent(val arena: Arena, val player: Player) : Event(), Cancellable {
    private var cancelled = false
    override fun isCancelled() = cancelled
    override fun setCancelled(cancel: Boolean) { cancelled = cancel }
    override fun getHandlers() = HANDLERS
    companion object { @JvmStatic val HANDLERS = HandlerList(); @JvmStatic fun getHandlerList() = HANDLERS }
}

/** Oyuncu arenadan ayrıldığında ateşlenir. */
class ArenaQuitEvent(val arena: Arena, val player: Player) : Event() {
    override fun getHandlers() = HANDLERS
    companion object { @JvmStatic val HANDLERS = HandlerList(); @JvmStatic fun getHandlerList() = HANDLERS }
}
