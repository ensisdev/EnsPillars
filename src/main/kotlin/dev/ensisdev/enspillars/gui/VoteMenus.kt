package dev.ensisdev.enspillars.gui

import dev.ensisdev.enspillars.EnsPillarsPlugin
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory

/** Oy hub'ı: oyun modları / harita modları alt menülerine dallanır. */
class VoteMenu(plugin: EnsPillarsPlugin) : Menu(plugin, "§8§lOy Ver", 27) {
    override fun fill(inv: Inventory, viewer: Player, page: Int) {
        val en = plugin.arenas.engineFor(viewer.uniqueId)
        val gameVotes = en?.vote?.gameCounts()?.values?.sum() ?: 0
        val mapVotes = en?.vote?.mapCounts()?.values?.sum() ?: 0
        inv.setItem(11, item(Material.WRITABLE_BOOK, "§bOyun Modları", listOf("§7Toplam oy: §e$gameVotes", "", "§eSeçmek için tıkla")))
        inv.setItem(15, item(Material.FILLED_MAP, "§dHarita Modları", listOf("§7Toplam oy: §e$mapVotes", "", "§eSeçmek için tıkla")))
        inv.setItem(22, item(Material.BARRIER, "§cKapat"))
        filler(inv)
    }

    override fun click(e: InventoryClickEvent) {
        super.click(e)
        val p = e.whoClicked as? Player ?: return
        when (e.rawSlot) {
            11 -> GameModeVoteMenu(plugin).open(p)
            15 -> MapModeVoteMenu(plugin).open(p)
            22 -> p.closeInventory()
        }
    }
}

private fun voteWindowOk(plugin: EnsPillarsPlugin, p: Player): dev.ensisdev.enspillars.game.GameEngine? {
    if (!p.hasPermission("enspillars.vote")) {
        plugin.messages.send(p, "no-permission")
        return null
    }
    val en = plugin.arenas.engineFor(p.uniqueId)
    if (en == null) {
        plugin.messages.send(p, "vote.not-in-game")
        return null
    }
    if (en.state() !in setOf(
            dev.ensisdev.enspillars.api.GameState.WAITING,
            dev.ensisdev.enspillars.api.GameState.STARTING,
            dev.ensisdev.enspillars.api.GameState.PRE_GAME,
            dev.ensisdev.enspillars.api.GameState.CAGED
        )
    ) {
        plugin.messages.send(p, "vote.closed")
        return null
    }
    if (!en.vote.tryCooldown(p.uniqueId)) {
        plugin.messages.send(p, "vote.cooldown")
        return null
    }
    return en
}

/** Oyun modu oy menüsü (menüden oy). */
class GameModeVoteMenu(plugin: EnsPillarsPlugin) : Menu(plugin, "§8§lOyun Modu", 36) {
    override fun fill(inv: Inventory, viewer: Player, page: Int) {
        val en = plugin.arenas.engineFor(viewer.uniqueId) ?: return
        val arena = plugin.arenas.all().firstOrNull { it.id == en.arenaIdForReplay() }
        val counts = en.vote.gameCounts()
        val mine = en.vote.myGame(viewer.uniqueId)
        val mats = mapOf("NORMAL" to Material.EMERALD, "SHUFFLE" to Material.ENDER_CHEST, "SWAP" to Material.CHORUS_FRUIT)
        (arena?.enabledModes ?: listOf("NORMAL", "SHUFFLE", "SWAP")).forEachIndexed { i, mode ->
            val m = mode.uppercase()
            inv.setItem(10 + i * 2, item(
                mats[m] ?: Material.PAPER,
                "§b$m",
                listOf("§7Toplam oy: §e${counts[m] ?: 0}", "§7Oyunuz: §e${if (mine == m) "✔" else "—"}", "", "§eOy vermek için tıkla"),
                mine == m
            ))
        }
        inv.setItem(27, item(Material.ARROW, "§7Geri dön"))
        inv.setItem(35, item(Material.BARRIER, "§cKapat"))
        filler(inv)
    }

    override fun click(e: InventoryClickEvent) {
        super.click(e)
        val p = e.whoClicked as? Player ?: return
        when (e.rawSlot) {
            27 -> VoteMenu(plugin).open(p)
            35 -> p.closeInventory()
            else -> {
                if ((e.rawSlot - 10) % 2 != 0 || e.rawSlot < 10) return
                val en = voteWindowOk(plugin, p) ?: return
                val arena = plugin.arenas.all().firstOrNull { it.id == en.arenaIdForReplay() }
                val mode = (arena?.enabledModes ?: listOf("NORMAL", "SHUFFLE", "SWAP"))
                    .map { it.uppercase() }.getOrNull((e.rawSlot - 10) / 2) ?: return
                if (en.vote.voteGame(p.uniqueId, mode)) {
                    plugin.messages.send(p, "vote.game-ok", mapOf("value" to mode))
                    plugin.server.getPlayer(p.uniqueId)?.playSound(p.location, org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 1f)
                    GameModeVoteMenu(plugin).open(p)
                } else plugin.messages.send(p, "vote.invalid")
            }
        }
    }
}

/** Harita modu oy menüsü (menüden oy). */
class MapModeVoteMenu(plugin: EnsPillarsPlugin) : Menu(plugin, "§8§lHarita Modu", 36) {
    override fun fill(inv: Inventory, viewer: Player, page: Int) {
        val en = plugin.arenas.engineFor(viewer.uniqueId) ?: return
        val arena = plugin.arenas.all().firstOrNull { it.id == en.arenaIdForReplay() }
        val counts = en.vote.mapCounts()
        val mine = en.vote.myMap(viewer.uniqueId)
        val mats = mapOf("NORMAL" to Material.GRASS_BLOCK, "LAVA_RISE" to Material.LAVA_BUCKET, "FRAGILE" to Material.TINTED_GLASS, "TNT_RAIN" to Material.TNT, "SHRINKING_BORDER" to Material.ENDER_PEARL)
        (arena?.enabledMapModes ?: listOf("NORMAL", "LAVA_RISE", "FRAGILE")).forEachIndexed { i, mode ->
            val m = mode.uppercase()
            inv.setItem(10 + i * 2, item(
                mats[m] ?: Material.PAPER,
                "§d$m",
                listOf("§7Toplam oy: §e${counts[m] ?: 0}", "§7Oyunuz: §e${if (mine == m) "✔" else "—"}", "", "§eOy vermek için tıkla"),
                mine == m
            ))
        }
        inv.setItem(27, item(Material.ARROW, "§7Geri dön"))
        inv.setItem(35, item(Material.BARRIER, "§cKapat"))
        filler(inv)
    }

    override fun click(e: InventoryClickEvent) {
        super.click(e)
        val p = e.whoClicked as? Player ?: return
        when (e.rawSlot) {
            27 -> VoteMenu(plugin).open(p)
            35 -> p.closeInventory()
            else -> {
                if ((e.rawSlot - 10) % 2 != 0 || e.rawSlot < 10) return
                val en = voteWindowOk(plugin, p) ?: return
                val arena = plugin.arenas.all().firstOrNull { it.id == en.arenaIdForReplay() }
                val mode = (arena?.enabledMapModes ?: listOf("NORMAL", "LAVA_RISE", "FRAGILE"))
                    .map { it.uppercase() }.getOrNull((e.rawSlot - 10) / 2) ?: return
                if (en.vote.voteMap(p.uniqueId, mode)) {
                    plugin.messages.send(p, "vote.map-ok", mapOf("value" to mode))
                    p.playSound(p.location, org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 1f)
                    MapModeVoteMenu(plugin).open(p)
                } else plugin.messages.send(p, "vote.invalid")
            }
        }
    }
}
