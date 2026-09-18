package dev.ensisdev.enspillars.gui

import dev.ensisdev.enspillars.EnsPillarsPlugin
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory

/** Placeholder destekli istatistik paneli. */
class StatsMenu(plugin: EnsPillarsPlugin) : Menu(plugin, "§8§lİstatistikler", 54) {
    override fun fill(inv: Inventory, viewer: Player, page: Int) {
        val s = plugin.stats.load(viewer.uniqueId)
        val kd = "%.2f".format(s.kills.toDouble() / maxOf(1, s.deaths))
        inv.setItem(13, head("§b${viewer.name}", viewer, listOf("§7Oyun: §f${s.games}", "§7Coin: §e${s.coins}")))
        inv.setItem(21, item(Material.NETHERITE_SWORD, "§cKill", listOf("§f${s.kills}")))
        inv.setItem(22, item(Material.SKELETON_SKULL, "§7Ölüm", listOf("§f${s.deaths}")))
        inv.setItem(23, item(Material.BOW, "§eK/D", listOf("§f$kd")))
        inv.setItem(29, item(Material.PLAYER_HEAD, "§7Mağlubiyet", listOf("§f${s.losses}")))
        inv.setItem(30, item(Material.GOLDEN_APPLE, "§aGalibiyet", listOf("§f${s.wins}"), true))
        inv.setItem(31, item(Material.EMERALD, "§dPuan", listOf("§f${s.points}")))
        inv.setItem(32, item(Material.GOLD_INGOT, "§6Coin", listOf("§f${s.coins}")))
        inv.setItem(33, item(Material.FIREWORK_ROCKET, "§bSeri", listOf("§f${s.winStreak} §7(en iyi ${s.maxWinStreak})")))
        inv.setItem(49, item(Material.BARRIER, "§cKapat"))
        filler(inv)
    }

    override fun click(e: InventoryClickEvent) {
        super.click(e)
        if (e.rawSlot == 49) (e.whoClicked as? Player)?.closeInventory()
    }
}

/** Canlı oyuncuların kafaları; tıklayınca ışınlar. */
class SpectateMenu(plugin: EnsPillarsPlugin) : Menu(plugin, "§8§lİzle", 54) {
    private val slots = (10..16).toList() + (19..25).toList() + (28..34).toList() + (37..43).toList()

    override fun fill(inv: Inventory, viewer: Player, page: Int) {
        val en = plugin.arenas.engineFor(viewer.uniqueId) ?: return
        val alive = plugin.server.onlinePlayers.filter { en.isAlive(it.uniqueId) && it.uniqueId != viewer.uniqueId }
        alive.drop((page - 1) * slots.size).take(slots.size).forEachIndexed { i, target ->
            inv.setItem(slots[i], head("§f${target.name}", target, listOf("", "§aİzlemek için tıkla")))
        }
        if (page > 1) inv.setItem(45, item(Material.ARROW, "§eÖnceki sayfa"))
        if (alive.size > page * slots.size) inv.setItem(53, item(Material.ARROW, "§eSonraki sayfa"))
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
                val en = plugin.arenas.engineFor(p.uniqueId) ?: return
                val alive = plugin.server.onlinePlayers.filter { en.isAlive(it.uniqueId) && it.uniqueId != p.uniqueId }
                val target = alive.drop((page - 1) * slots.size).getOrNull(idx) ?: return
                p.closeInventory()
                p.teleport(target.location)
            }
        }
    }
}
