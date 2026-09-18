package dev.ensisdev.enspillars.gui

import dev.ensisdev.enspillars.EnsPillarsPlugin
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack

/**
 * Tüm menülerin tabanı: holder-tabanlı yönlendirme, filler, sayfalama yardımcısı.
 * Açık menü GameListener üzerinden [dispatch] ile ilgili menüye gider.
 */
abstract class Menu(protected val plugin: EnsPillarsPlugin, val title: String, val size: Int) {
    class Holder(val menu: Menu, val page: Int = 1) : InventoryHolder {
        private var shown: Inventory? = null
        fun attach(inv: Inventory) {
            shown = inv
        }
        override fun getInventory(): Inventory =
            shown ?: throw IllegalStateException("menu not built")
    }

    open fun open(viewer: Player, page: Int = 1) {
        viewer.openInventory(build(viewer, page))
    }

    open fun build(viewer: Player, page: Int = 1): Inventory {
        val holder = Holder(this, page)
        val inv = Bukkit.createInventory(holder, size, title)
        holder.attach(inv)
        fill(inv, viewer, page)
        return inv
    }

    protected abstract fun fill(inv: Inventory, viewer: Player, page: Int)

    open fun click(e: InventoryClickEvent) {
        e.isCancelled = true
    }

    protected fun filler(inv: Inventory, mat: Material = Material.GRAY_STAINED_GLASS_PANE) {
        val p = ItemStack(mat).apply {
            itemMeta = itemMeta?.apply { setDisplayName(" ") }
        }
        for (i in 0 until inv.size) if (inv.getItem(i) == null) inv.setItem(i, p)
    }

    protected fun item(mat: Material, name: String, lore: List<String> = emptyList(), glow: Boolean = false): ItemStack =
        ItemStack(mat).apply {
            itemMeta = itemMeta?.apply {
                setDisplayName(color(name))
                if (lore.isNotEmpty()) setLore(lore.map { color(it) })
                if (glow) {
                    addEnchant(Enchantment.DURABILITY, 1, true)
                    addItemFlags(ItemFlag.HIDE_ENCHANTS)
                }
                addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
            }
        }

    protected fun color(s: String) =
        org.bukkit.ChatColor.translateAlternateColorCodes('&', s)

    protected fun head(name: String, owner: Player, lore: List<String> = emptyList()): ItemStack =
        ItemStack(Material.PLAYER_HEAD).apply {
            itemMeta = (itemMeta as? org.bukkit.inventory.meta.SkullMeta)?.apply {
                setDisplayName(color(name))
                if (lore.isNotEmpty()) setLore(lore.map { color(it) })
                setOwningPlayer(owner)
            }
        }

    companion object {
        fun dispatch(e: InventoryClickEvent) {
            val holder = e.view.topInventory?.holder as? Holder ?: return
            holder.menu.click(e)
        }

        fun pageOf(e: InventoryClickEvent) =
            (e.view.topInventory?.holder as? Holder)?.page ?: 1
    }
}
