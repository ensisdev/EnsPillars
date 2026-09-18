package dev.ensisdev.enspillars.gui

import dev.ensisdev.enspillars.EnsPillarsPlugin
import dev.ensisdev.enspillars.game.Arena
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory

/**
 * Arena yönetim paneli: sayı ayarlayıcılar, mod döngüleri,
 * tek-tık konum yakalama istekleri, spawn sayacı, kaydet&etkinleştir.
 */
class ArenaSetupMenu(plugin: EnsPillarsPlugin, private val arenaId: String, private val armedDelete: Boolean = false, private val armedClear: Boolean = false) : Menu(plugin, "§8§lArena Kurulum", 54) {

    private data class NumField(
        val slot: Int, val path: String, val mat: Material, val title: String, val unit: String,
        val min: Double, val max: Double, val small: Double, val big: Double, val dbl: Boolean
    )

    private val numbers = listOf(
        NumField(10, "min-players", Material.RED_DYE, "§cMin Oyuncu", "", 1.0, 100.0, 1.0, 5.0, false),
        NumField(11, "max-players", Material.LIME_DYE, "§aMax Oyuncu", "", 1.0, 100.0, 1.0, 5.0, false),
        NumField(12, "border-size", Material.GRASS_BLOCK, "§bBorder", " blok", 8.0, 10000.0, 5.0, 25.0, true),
        NumField(13, "starting-countdown", Material.CLOCK, "§eBaşlama Sayacı", "sn", 0.0, 3600.0, 1.0, 5.0, false),
        NumField(14, "pre-game-time", Material.REDSTONE_TORCH, "§eHazırlık", "sn", 0.0, 3600.0, 1.0, 5.0, false),
        NumField(15, "duration-seconds", Material.ENDER_PEARL, "§dSüre", "sn", 30.0, 86400.0, 5.0, 60.0, false),
        NumField(16, "item-delay-seconds", Material.CHEST, "§6Eşya Aralığı", "sn", 1.0, 3600.0, 1.0, 5.0, false)
    )

    private fun arena(): Arena? = plugin.arenas.get(arenaId)

    private fun missing(a: Arena): List<String> = buildList {
        if (a.center() == null) add("center")
        val need = maxOf(2, a.minPlayers)
        if (a.spawnCount() < need) add("spawn (${a.spawnCount()}/$need)")
    }

    override fun fill(inv: Inventory, viewer: Player, page: Int) {
        val a = arena() ?: return
        val miss = missing(a)
        inv.setItem(4, item(
            if (miss.isEmpty()) Material.LIME_TERRACOTTA else Material.RED_TERRACOTTA,
            "§b${a.displayName} §8[${a.state}]",
            (if (miss.isEmpty()) listOf("§aKurulum tamam!") else listOf("§cEksikler:") + miss.map { "§7- §f$it" }) +
                listOf(
                    "",
                    "§7ID: §f${a.id}",
                    "§7Oyuncu: §e${a.playerCount()}§8/§e${a.maxPlayers} §8(min ${a.minPlayers})",
                    "§7Spawn: §e${a.spawnCount()} §8| §7Border: §e${a.borderSize.toInt()}",
                    "§7Sayaçlar: §e${a.startingCountdown}§8/§e${a.preGameTime}§8/§e${a.durationSeconds}sn",
                    "§7Mod: §f${a.defaultGameMode}§8/§f${a.defaultMapMode}"
                )
        ))
        numbers.forEach { f ->
            inv.setItem(f.slot, item(f.mat, f.title, listOf("§7Mevcut: §a${cur(a, f)}${f.unit}", "§7Sol: §f+§8/§7Sağ: §f- §8(Shift: ${f.big.toInt()})")))
        }
        inv.setItem(19, item(Material.GRASS_BLOCK, "§aMerkez", locLine(a.center() != null, a.center()?.let { "${it.blockX}, ${it.blockY}, ${it.blockZ}" } ?: "")))
        inv.setItem(20, item(Material.ENDER_EYE, "§bİzleyici", locLine(a.spectator() != null, "Ayarlı (yoksa merkez)")))
        inv.setItem(21, item(
            Material.BEACON, "§eSpawnlar",
            listOf("§7Kayıtlı: §e${a.spawnCount()}§8/§e${a.maxPlayers}", "§7Sol tık: §fyakalamaya başla") +
                if (armedClear) listOf("§4§lEMİN MİSİN?", "§7Sağ tıkla: tümünü temizle (onay)") else listOf("§7Sağ tık: §ctümünü temizle (2 kez)")
        ))
        inv.setItem(22, item(Material.GLASS, "§dKafesleri Gör", listOf("§7Spawnları ışınlanarak denetle")))
        inv.setItem(23, item(Material.DIAMOND_SWORD, "§cOyun Modu: §f${a.defaultGameMode}", listOf("§7Tıkla: değiştir")))
        inv.setItem(24, item(Material.FILLED_MAP, "§dHarita Modu: §f${a.defaultMapMode}", listOf("§7Tıkla: değiştir")))
        inv.setItem(40, item(
            if (miss.isEmpty()) Material.LIME_TERRACOTTA else Material.RED_TERRACOTTA,
            if (miss.isEmpty()) "§aKaydet & Etkinleştir" else "§cEksikler var",
            if (miss.isEmpty()) listOf("§7Arenayı aktif et") else miss.map { "§7- §f$it" }
        ))
        inv.setItem(39, item(
            if (a.enabled) Material.LIME_DYE else Material.GRAY_DYE,
            if (a.enabled) "§aEtkin §7(tıkla: kapat)" else "§7Kapalı §7(tıkla: aç)"
        ))
        inv.setItem(41, item(Material.ENDER_PEARL, "§bMerkeze Işınlan", listOf("§7Arena merkezine git")))
        inv.setItem(43, item(
            Material.TNT, "§cArenayı Sil",
            if (armedDelete) listOf("§4§lEMİN MİSİN?", "§7Onaylamak için tekrar tıkla") else listOf("§7İki kez tıkla")
        ))
        inv.setItem(25, item(Material.COMPASS, "§eArenalar", listOf("§7Arena listesine dön")))
        inv.setItem(48, item(Material.BARRIER, "§cKurulumdan Çık", listOf("§7Envanter iade edilir")))
        inv.setItem(49, item(Material.OAK_DOOR, "§7Kapat"))
        filler(inv)
    }

    private fun locLine(ok: Boolean, info: String) =
        if (ok) listOf("§aAyarlı" + if (info.isNotEmpty()) " §8($info)" else "", "§7Tıkla: değiştir")
        else listOf("§cAyarlı değil", "§7Tıkla: ayarla")

    private fun cur(a: Arena, f: NumField): String = when (f.path) {
        "min-players" -> a.minPlayers.toString()
        "max-players" -> a.maxPlayers.toString()
        "border-size" -> a.borderSize.toString()
        "starting-countdown" -> a.startingCountdown.toString()
        "pre-game-time" -> a.preGameTime.toString()
        "duration-seconds" -> a.durationSeconds.toString()
        "item-delay-seconds" -> a.itemDelaySeconds.toString()
        else -> "?"
    }

    override fun click(e: InventoryClickEvent) {
        super.click(e)
        val p = e.whoClicked as? Player ?: return
        var a = arena() ?: run { p.closeInventory(); return }
        numbers.firstOrNull { it.slot == e.rawSlot }?.let { f ->
            val step = when (e.click) {
                ClickType.LEFT -> f.small
                ClickType.RIGHT -> -f.small
                ClickType.SHIFT_LEFT -> f.big
                ClickType.SHIFT_RIGHT -> -f.big
                else -> 0.0
            }
            if (step != 0.0) {
                val curVal = cur(a, f).toDoubleOrNull() ?: 0.0
                var next = (curVal + step).coerceIn(f.min, f.max)
                if (f.path == "min-players" && next > a.maxPlayers) {
                    plugin.setup.setSetting(arenaId, "max-players", next.toInt())
                }
                if (f.path == "max-players" && next < a.minPlayers) {
                    plugin.setup.setSetting(arenaId, "min-players", next.toInt())
                }
                plugin.setup.setSetting(arenaId, f.path, if (f.dbl) next else next.toInt())
                plugin.arenas.reloadSingle(arenaId)
                a = arena() ?: run { p.closeInventory(); return }
                p.playSound(p.location, Sound.UI_BUTTON_CLICK, 1f, 1.5f)
                open(p)
            }
            return
        }
        when (e.rawSlot) {
            19 -> plugin.setupMode.requestSingle(p, "center")
            20 -> plugin.setupMode.requestSingle(p, "spectator")
            21 -> {
                if (e.click == ClickType.RIGHT) {
                    // P0: GUI'den tek tıkla tüm spawn silme onaysız yapılamaz.
                    if (!armedClear) {
                        ArenaSetupMenu(plugin, arenaId, armedDelete, armedClear = true).open(p)
                        p.playSound(p.location, Sound.UI_BUTTON_CLICK, 1f, 0.6f)
                        return
                    }
                    plugin.setup.clearSpawns(arenaId)
                    plugin.arenas.reloadSingle(arenaId)
                    plugin.messages.send(p, "setup.spawns-cleared")
                    open(p)
                } else {
                    p.closeInventory()
                    plugin.setupMode.startCapture(p)
                }
            }
            22 -> CageViewMenu(plugin, arenaId).open(p)
            23 -> {
                cycle(p, listOf("NORMAL", "SHUFFLE", "SWAP"), a.defaultGameMode, "default-game-mode")
                open(p)
            }
            24 -> {
                cycle(p, listOf("NORMAL", "LAVA_RISE", "FRAGILE", "TNT_RAIN", "ABLOCKALYPSE", "SHRINKING_BORDER"), a.defaultMapMode, "default-map-mode")
                open(p)
            }
            40 -> {
                val miss = missing(a)
                if (miss.isEmpty()) {
                    plugin.setup.setEnabled(arenaId, true)
                    plugin.arenas.reloadSingle(arenaId)
                    plugin.messages.send(p, "arena-admin.state-updated")
                    p.playSound(p.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
                    p.closeInventory()
                } else {
                    plugin.messages.send(p, "setup.incomplete", mapOf("missing" to miss.joinToString(", ")))
                    p.playSound(p.location, Sound.ENTITY_VILLAGER_NO, 1f, 0.8f)
                }
            }
            39 -> {
                val en = plugin.arenas.engine(arenaId)
                if (en != null && en.playerCount() > 0) {
                    plugin.messages.send(p, "arena-admin.active-block")
                    return
                }
                plugin.setup.setEnabled(arenaId, !a.enabled)
                plugin.arenas.reloadSingle(arenaId)
                plugin.messages.send(p, "arena-admin.state-updated")
                p.playSound(p.location, Sound.UI_BUTTON_CLICK, 1f, 1.2f)
                open(p)
            }
            41 -> {
                val dest = a.center() ?: a.spawn(0)
                if (dest == null) {
                    plugin.messages.send(p, "setup.incomplete", mapOf("missing" to "center"))
                    return
                }
                p.closeInventory()
                p.teleport(dest)
                plugin.messages.send(p, "setup.teleported", mapOf("n" to arenaId))
            }
            43 -> {
                if (!armedDelete) {
                    ArenaSetupMenu(plugin, arenaId, armedDelete = true).open(p)
                    p.playSound(p.location, Sound.UI_BUTTON_CLICK, 1f, 0.6f)
                    return
                }
                val en = plugin.arenas.engine(arenaId)
                if (en != null && en.playerCount() > 0) {
                    plugin.messages.send(p, "arena-admin.active-block")
                    return
                }
                p.closeInventory()
                if (plugin.setup.remove(arenaId)) {
                    plugin.arenas.removeArena(arenaId)
                    plugin.setupMode.exit(p, teleportBack = false)
                    plugin.messages.send(p, "arena-admin.deleted")
                } else plugin.messages.send(p, "arena-admin.not-found")
            }
            25 -> ArenaMenu(plugin).open(p)
            48 -> plugin.setupMode.exit(p)
            49 -> p.closeInventory()
        }
    }

    private fun cycle(p: Player, options: List<String>, current: String, key: String) {
        val next = options[(options.indexOf(current.uppercase()).takeIf { it >= 0 } ?: -1).plus(1) % options.size]
        plugin.setup.setMode(arenaId, key, next)
        plugin.arenas.reloadSingle(arenaId)
        p.playSound(p.location, Sound.UI_BUTTON_CLICK, 1f, 1.2f)
    }
}
