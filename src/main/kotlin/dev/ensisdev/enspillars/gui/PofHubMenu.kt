package dev.ensisdev.enspillars.gui

import dev.ensisdev.enspillars.EnsPillarsPlugin
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory

/** /pof ana hub menüsü: katıl + oy + mağaza + istatistik + hızlı katıl. */
class PofHubMenu(plugin: EnsPillarsPlugin) : Menu(plugin, "§8§lPillars", 27) {
    override fun fill(inv: Inventory, viewer: Player, page: Int) {
        inv.setItem(10, item(Material.COMPASS, "§bArenalara Katıl", listOf("§7Arena seçiciyi aç")))
        inv.setItem(12, item(Material.WRITABLE_BOOK, "§eOy Ver", listOf("§7Mod ve harita oylaması")))
        inv.setItem(14, item(Material.EMERALD, "§aMağaza", listOf("§7Kozmetikler")))
        inv.setItem(16, head("§dİstatistikler", viewer, listOf("§7Detaylı paneli aç")))
        inv.setItem(22, item(Material.NETHER_STAR, "§6Hızlı Katıl", listOf("§7Rastgele arenaya gir")))
        filler(inv)
    }

    override fun click(e: InventoryClickEvent) {
        super.click(e)
        val p = e.whoClicked as? Player ?: return
        when (e.rawSlot) {
            10 -> ArenaMenu(plugin).open(p)
            12 -> {
                if (plugin.arenas.engineFor(p.uniqueId) == null) {
                    plugin.messages.send(p, "vote.not-in-game")
                    return
                }
                VoteMenu(plugin).open(p)
            }
            14 -> {
                if (!p.hasPermission("enspillars.cosmetics")) {
                    plugin.messages.send(p, "no-permission")
                    return
                }
                plugin.shop.open(p)
            }
            16 -> StatsMenu(plugin).open(p)
            22 -> {
                p.closeInventory()
                if (plugin.arenas.engineFor(p.uniqueId) != null) {
                    plugin.messages.send(p, "join.already")
                    return
                }
                if (!plugin.arenas.autojoin(p)) plugin.messages.send(p, "auto.none")
            }
        }
    }
}
