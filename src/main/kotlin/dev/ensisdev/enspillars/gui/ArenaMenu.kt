package dev.ensisdev.enspillars.gui

import dev.ensisdev.enspillars.EnsPillarsPlugin
import dev.ensisdev.enspillars.api.GameState
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory

/**
 * Arena seçici: duruma göre renkli ikon, doluluk + oy bilgisi.
 * Devam eden maça tıklama izleyici olarak ışınlar.
 */
class ArenaMenu(plugin: EnsPillarsPlugin) : Menu(plugin, "§8§lArenalar", 54) {
    private val displaySlots = (10..16).toList() + (19..25).toList() + (28..34).toList() + (37..43).toList()

    private fun stateMaterial(s: GameState) = when (s) {
        GameState.WAITING -> Material.LIME_CONCRETE
        GameState.STARTING, GameState.PRE_GAME, GameState.CAGED -> Material.YELLOW_CONCRETE
        GameState.ACTIVE, GameState.FINAL -> Material.RED_CONCRETE
        GameState.ENDING, GameState.RESETTING -> Material.LIGHT_BLUE_CONCRETE
        GameState.DISABLED -> Material.BEDROCK
    }

    override fun fill(inv: Inventory, viewer: Player, page: Int) {
        val arenas = plugin.arenas.all().sortedWith(
            compareBy({ statePriority(it.state) }, { it.id })
        )
        val pages = maxOf(1, (arenas.size + displaySlots.size - 1) / displaySlots.size)
        val p = page.coerceIn(1, pages)
        arenas.drop((p - 1) * displaySlots.size).take(displaySlots.size).forEachIndexed { i, a ->
            val en = plugin.arenas.engine(a.id)
            val votes = en?.let { it.vote.gameCounts().values.sum() + it.vote.mapCounts().values.sum() } ?: 0
            inv.setItem(displaySlots[i], item(
                stateMaterial(a.state),
                "§b${a.displayName}",
                listOf(
                    "§7Durum: §f${a.state}",
                    "§7Oyuncular: §e${a.playerCount()}§8/§e${a.maxPlayers}",
                    "§7Oy: §d$votes",
                    "",
                    if (a.state == GameState.WAITING) "§aKatılmak için tıkla"
                    else if (a.state in setOf(GameState.ACTIVE, GameState.FINAL)) "§eİzlemek için tıkla"
                    else "§7Şu an girilemez"
                ),
                a.state == GameState.WAITING
            ))
        }
        if (p > 1) inv.setItem(45, item(Material.ARROW, "§eÖnceki sayfa"))
        if (p < pages) inv.setItem(53, item(Material.ARROW, "§eSonraki sayfa"))
        inv.setItem(47, item(Material.CHEST, "§eİstatistikler"))
        inv.setItem(49, item(Material.BARRIER, "§cKapat"))
        inv.setItem(51, item(Material.EMERALD, "§aMağaza"))
        filler(inv)
    }

    private fun statePriority(s: GameState) = when (s) {
        GameState.WAITING -> 0
        GameState.STARTING, GameState.PRE_GAME, GameState.CAGED -> 1
        GameState.ACTIVE, GameState.FINAL -> 2
        else -> 3
    }

    override fun click(e: InventoryClickEvent) {
        super.click(e)
        val p = e.whoClicked as? Player ?: return
        val page = Menu.pageOf(e)
        val arenas = plugin.arenas.all().sortedWith(compareBy({ statePriority(it.state) }, { it.id }))
        when (e.rawSlot) {
            45 -> open(p, page - 1)
            53 -> open(p, page + 1)
            47 -> { p.closeInventory(); StatsMenu(plugin).open(p) }
            49 -> p.closeInventory()
            51 -> { p.closeInventory(); plugin.shop.open(p) }
            else -> {
                val idx = displaySlots.indexOf(e.rawSlot)
                if (idx < 0) return
                val arena = arenas.drop((page - 1) * displaySlots.size).getOrNull(idx) ?: return
                p.closeInventory()
                when (arena.state) {
                    GameState.WAITING -> {
                        if (!plugin.arenas.join(p, arena.id))
                            plugin.messages.send(p, "join.fail")
                    }
                    GameState.ACTIVE, GameState.FINAL -> {
                        val ok = plugin.arenas.engine(arena.id)?.spectate(p) == true
                        if (ok) plugin.messages.send(p, "spectate.joined", mapOf("arena" to arena.displayName))
                        else plugin.messages.send(p, "spectate.no-point")
                    }
                    else -> plugin.messages.send(p, "join.fail")
                }
            }
        }
    }
}
