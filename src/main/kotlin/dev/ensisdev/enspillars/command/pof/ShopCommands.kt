package dev.ensisdev.enspillars.command.pof

import dev.ensisdev.enspillars.EnsPillarsPlugin
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/** /pof shop | /pof cosmetics [materyal] | /pof stats — mağaza ve profil.
 *
 * P0 düzeltmesi: `stats`/`leaderboard` artık `enspillars.stats` ister
 * (önceden yanlışlıkla cosmetics istiyordu). Console oyun menüsü
 * açamaz; `cmd.console-only` ile yanıt verir.
 */
class ShopCommands(private val plugin: EnsPillarsPlugin) {
    private fun can(s: CommandSender, node: String): Boolean =
        s.hasPermission(node) || s.hasPermission("enspillars.admin")

    fun execute(s: CommandSender, sub: String, a: List<String>): Boolean {
        if (sub == "stats") {
            if (!can(s, "enspillars.stats")) {
                deny(s)
                return true
            }
            val p = s as? Player ?: run { plugin.messages.send(s, "cmd.console-only"); return true }
            dev.ensisdev.enspillars.gui.StatsMenu(plugin).open(p)
            return true
        }
        if (!can(s, "enspillars.cosmetics")) {
            deny(s)
            return true
        }
        val p = s as? Player ?: run { plugin.messages.send(s, "cmd.console-only"); return true }
        when (sub) {
            "shop" -> {
                if (a.isEmpty()) {
                    plugin.shop.open(p)
                    return true
                }
                // `/pof shop <kategori>`: GUI kategorisine derin bağlantı.
                when (a.firstOrNull()?.lowercase()) {
                    "cages" -> dev.ensisdev.enspillars.gui.CageShopMenu(plugin).open(p)
                    "killmessages", "kill-messages", "kill" -> dev.ensisdev.enspillars.gui.KillMessageShopMenu(plugin).open(p)
                    "deathcries", "death-cries", "death" -> dev.ensisdev.enspillars.gui.DeathCryShopMenu(plugin).open(p)
                    else -> plugin.messages.send(s, "shop.usage")
                }
                return true
            }
            "cosmetics" -> {
                val v = a.firstOrNull()
                if (v == null) {
                    plugin.shop.open(p)
                    return true
                }
                if (plugin.cosmetics.select(p.uniqueId, v)) plugin.messages.send(s, "cosmetics.selected", mapOf("value" to v.uppercase()))
                else plugin.messages.send(s, "cosmetics.invalid")
            }
            else -> plugin.messages.send(s, "unknown-command")
        }
        return true
    }

    fun tabComplete(s: CommandSender, a: List<String>, sub: String = "shop"): List<String> {
        if (sub == "stats") {
            if (!can(s, "enspillars.stats")) return emptyList()
            return emptyList()
        }
        if (!can(s, "enspillars.cosmetics")) return emptyList()
        if (a.size == 1) {
            val subs = mutableListOf("cages", "killmessages", "deathcries")
            subs += try {
                plugin.shopCatalog.cages.keys + plugin.shopCatalog.killMessages.keys + plugin.shopCatalog.deathCries.keys
            } catch (_: Exception) { emptySet() }
            return subs.distinct().filter { it.startsWith(a[0], true) }
        }
        return emptyList()
    }

    private fun deny(s: CommandSender) = plugin.messages.send(s, "no-permission")
}
