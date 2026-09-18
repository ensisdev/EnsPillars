package dev.ensisdev.enspillars.api.events

import dev.ensisdev.enspillars.game.Arena
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.inventory.ItemStack

/**
 * Loot dağıtılmadan önce ateşlenir. İptal edilebilir, eşya listesi
 * değiştirilebilir, envanter temizliği kapatılabilir.
 */
class ArenaItemGiveEvent(
    val arena: Arena,
    val player: Player,
    var items: MutableList<ItemStack>,
    val gameMode: String,
    var clearInventory: Boolean
) : Event(), Cancellable {
    private var cancelled = false
    override fun isCancelled() = cancelled
    override fun setCancelled(cancel: Boolean) { cancelled = cancel }
    override fun getHandlers() = HANDLERS
    companion object { @JvmStatic val HANDLERS = HandlerList(); @JvmStatic fun getHandlerList() = HANDLERS }
}
