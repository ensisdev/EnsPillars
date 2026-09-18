package dev.ensisdev.enspillars.command

import dev.ensisdev.enspillars.EnsPillarsPlugin
import dev.ensisdev.enspillars.command.pof.ArenaAdminCommands
import dev.ensisdev.enspillars.command.pof.GameFlowCommands
import dev.ensisdev.enspillars.command.pof.PartyCommands
import dev.ensisdev.enspillars.command.pof.ReplayCommands
import dev.ensisdev.enspillars.command.pof.ShopCommands
import dev.ensisdev.enspillars.command.pof.VoteCommands
import org.bukkit.command.*
import org.bukkit.entity.Player

/** /pof — ana oyun komutu. Yalın kullanım hub menüyü açar.
 *
 * Yetki modeli: `enspillars.admin` parent node'dur; [can] üzerinden tüm
 * admin dallarını açar (plugin.yml children ile senkron).
 * Renk sistemi: legacy `&` kodları (belgeli tercih, MiniMessage yok).
 */
class PofCommand(private val plugin: EnsPillarsPlugin) : CommandExecutor, TabCompleter {
    private val game = GameFlowCommands(plugin)
    private val vote = VoteCommands(plugin)
    private val party = PartyCommands(plugin)
    private val shop = ShopCommands(plugin)
    private val replay = ReplayCommands(plugin)
    private val arena = ArenaAdminCommands(plugin)
    private fun can(s: CommandSender, node: String): Boolean =
        s.hasPermission(node) || (node != "enspillars.admin" && s.hasPermission("enspillars.admin"))

    override fun onCommand(s: CommandSender, c: Command, l: String, a: Array<out String>): Boolean {
        if (!s.hasPermission("enspillars.use")) {
            deny(s)
            return true
        }
        if (!plugin.isCommandEnabled("pof")) {
            plugin.messages.send(s, "cmd.disabled")
            return true
        }
        if (a.isEmpty()) {
            val p = s as? Player
            if (p == null) {
                plugin.messages.list("help-pof").forEach(s::sendMessage)
                return true
            }
            dev.ensisdev.enspillars.gui.PofHubMenu(plugin).open(p)
            return true
        }
        when (a[0].lowercase()) {
            "help" -> plugin.messages.list("help-pof").forEach { msg ->
                s.sendMessage(if (s is Player) plugin.placeholders.apply(s, msg) else msg)
            }
            "join" -> game.execute(s, "join", a.drop(1))
            "leave" -> game.execute(s, "leave", emptyList())
            "menu" -> game.execute(s, "menu", emptyList())
            "spectate" -> game.execute(s, "spectate", a.drop(1))
            "autojoin" -> game.execute(s, "autojoin", emptyList())
            "forcestart" -> game.execute(s, "forcestart", emptyList())
            "vote" -> vote.execute(s, a.drop(1))
            "party" -> party.execute(s, a.drop(1))
            "cosmetics", "cosmetic" -> shop.execute(s, "cosmetics", a.drop(1))
            "shop" -> shop.execute(s, "shop", a.drop(1))
            "leaderboard", "stats" -> shop.execute(s, "stats", emptyList())
            "arena" -> arena.execute(s, a.drop(1))
            "setup" -> arena.setupAlias(s, a.drop(1))
            "setspawn" -> arena.setspawn(s, a.drop(1))
            "replay" -> replay.execute(s, a.drop(1))
            else -> plugin.messages.send(s, "unknown-command")
        }
        return true
    }

    private fun deny(s: CommandSender) = plugin.messages.send(s, "no-permission")

    override fun onTabComplete(s: CommandSender, c: Command, l: String, a: Array<out String>): MutableList<String>? {
        if (!s.hasPermission("enspillars.use")) return mutableListOf()
        if (a.size == 1) {
            val subs = mutableListOf("help", "join", "leave", "menu", "spectate", "autojoin")
            // P0: stats/leaderboard artik cosmetics degil stats capability ister.
            if (can(s, "enspillars.stats")) subs += listOf("stats", "leaderboard")
            if (can(s, "enspillars.cosmetics")) subs += listOf("shop", "cosmetics")
            if (can(s, "enspillars.vote")) subs += "vote"
            if (can(s, "enspillars.party")) subs += "party"
            if (can(s, "enspillars.forcestart")) subs += "forcestart"
            if (can(s, "enspillars.admin.arena")) subs += "arena"
            // P0: yetkisiz kullaniciya admin dallari sizdirilmasin (setup dahil).
            if (can(s, "enspillars.admin.setup")) subs += listOf("setspawn", "setup")
            if (can(s, "enspillars.replay")) subs += "replay"
            return subs.filter { it.startsWith(a[0], true) }.toMutableList()
        }
        return when (a[0].lowercase()) {
            "join" -> game.tabComplete(s, "join", a.drop(1)).toMutableList()
            "spectate" -> game.tabComplete(s, "spectate", a.drop(1)).toMutableList()
            "vote" -> vote.tabComplete(s, a.drop(1)).toMutableList()
            "party" -> party.tabComplete(s, a.drop(1)).toMutableList()
            "shop", "cosmetics" -> shop.tabComplete(s, a.drop(1), "shop").toMutableList()
            "stats", "leaderboard" -> shop.tabComplete(s, a.drop(1), "stats").toMutableList()
            "arena" -> arena.tabComplete(s, a.drop(1)).toMutableList()
            "setup" -> arena.tabComplete(s, listOf("setup") + a.drop(1)).toMutableList()
            "replay" -> replay.tabComplete(s, a.drop(1)).toMutableList()
            else -> mutableListOf()
        }
    }
}
