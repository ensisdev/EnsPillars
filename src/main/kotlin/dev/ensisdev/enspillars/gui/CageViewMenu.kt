package dev.ensisdev.enspillars.gui

import dev.ensisdev.enspillars.EnsPillarsPlugin
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory

/** Kurulu spawnlar: tıkla → ışınlan, kafesi yerinde denetle. */
class CageViewMenu(plugin: EnsPillarsPlugin, private val arenaId: String) : Menu(plugin, "§8§lSpawnlar", 54) {
    private val slots = (10..16).toList() + (19..25).toList() + (28..34).toList() + (37..43).toList()

    override fun fill(inv: Inventory, viewer: Player, page: Int) {
        val a = plugin.arenas.get(arenaId) ?: return
        val total = a.spawnCount()
        val pages = maxOf(1, (total + slots.size - 1) / slots.size)
        val pg = page.coerceIn(1, pages)
        (0 until minOf(slots.size, total - (pg - 1) * slots.size)).forEach { i ->
            val idx = (pg - 1) * slots.size + i
            val loc = a.spawn(idx)
            inv.setItem(slots[i], item(
                Material.LIME_STAINED_GLASS_PANE,
                "§bSpawn #${idx + 1}",
                if (loc != null) listOf("§7${loc.blockX}, ${loc.blockY}, ${loc.blockZ}", "", "§aIşınlanmak için tıkla")
                else listOf("§cOkunamadı")
            ))
        }
        if (pg > 1) inv.setItem(45, item(Material.ARROW, "§eÖnceki sayfa"))
        if (pg < pages) inv.setItem(53, item(Material.ARROW, "§eSonraki sayfa"))
        inv.setItem(49, item(Material.BARRIER, "§cKapat"))
        filler(inv)
    }

    override fun click(e: InventoryClickEvent) {
        super.click(e)
        val p = e.whoClicked as? Player ?: return
        val page = Menu.pageOf(e)
        when (e.rawSlot) {
            45 -> open(p, page - 1)
            53 -> open(p, page + 1)
            49 -> p.closeInventory()
            else -> {
                val idx = slots.indexOf(e.rawSlot)
                if (idx < 0) return
                val a = plugin.arenas.get(arenaId) ?: return
                val loc = a.spawn((page - 1) * slots.size + idx) ?: return
                p.closeInventory()
                p.teleport(loc)
                plugin.messages.send(p, "setup.teleported", mapOf("n" to ((page - 1) * slots.size + idx + 1)))
            }
        }
    }
}
