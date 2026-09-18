package dev.ensisdev.enspillars.command.pof

import dev.ensisdev.enspillars.EnsPillarsPlugin
import dev.ensisdev.enspillars.api.GameState
import org.bukkit.GameMode
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/** /pof join|leave|menu|spectate|autojoin|forcestart — oyun akış komutları. */
class GameFlowCommands(private val plugin: EnsPillarsPlugin) {
    fun execute(s: CommandSender, sub: String, a: List<String>): Boolean {
        when (sub) {
            "join" -> join(s, a.getOrNull(0))
            "leave" -> leave(s)
            "menu" -> menu(s)
            "spectate" -> spectate(s, a.getOrNull(0))
            "autojoin" -> autojoin(s)
            "forcestart" -> forcestart(s)
        }
        return true
    }

    private fun join(s: CommandSender, id: String?) {
        val p = s as? Player ?: run { plugin.messages.send(s, "join.not-player"); return }
        val arenaId = id ?: plugin.arenas.all().firstOrNull { z -> z.state == GameState.WAITING }?.id
        if (arenaId == null) {
            plugin.messages.send(s, "join.none")
            return
        }
        if (plugin.arenas.engineFor(p.uniqueId) != null) {
            plugin.messages.send(s, "join.already")
            return
        }
        if (!plugin.arenas.join(p, arenaId)) plugin.messages.send(s, "join.fail")
    }

    private fun leave(s: CommandSender) {
        val p = s as? Player ?: run { plugin.messages.send(s, "join.not-player"); return }
        plugin.arenas.leave(p)
        plugin.messages.send(s, "leave.ok")
    }

    private fun menu(s: CommandSender) {
        val p = s as? Player ?: run { plugin.messages.send(s, "cmd.console-only"); return }
        plugin.menu.open(p)
    }

    private fun spectate(s: CommandSender, targetName: String?) {
        val p = s as? Player ?: run { plugin.messages.send(s, "join.not-player"); return }
        val target = targetName?.let { plugin.server.getPlayer(it) }
        if (target == null) {
            if (targetName != null) {
                plugin.messages.send(s, "spectate.player-gone")
                return
            }
            val en = plugin.arenas.engineFor(p.uniqueId)
            if (en == null) {
                plugin.messages.send(s, "vote.not-in-game")
                return
            }
            dev.ensisdev.enspillars.gui.SpectateMenu(plugin).open(p)
            return
        }
        val en = plugin.arenas.engineFor(target.uniqueId) ?: run { plugin.messages.send(s, "spectate.player-gone"); return }
        if (!en.isAlive(target.uniqueId)) {
            plugin.messages.send(s, "spectate.player-gone")
            return
        }
        p.gameMode = GameMode.SPECTATOR
        p.teleport(target.location)
        plugin.messages.send(s, "spectate.joined", mapOf("arena" to en.arenaIdForReplay()))
    }

    private fun autojoin(s: CommandSender) {
        val p = s as? Player ?: run { plugin.messages.send(s, "join.not-player"); return }
        if (plugin.arenas.engineFor(p.uniqueId) != null) {
            plugin.messages.send(s, "join.already")
            return
        }
        val arena = plugin.arenas.all().filter { it.state == GameState.WAITING }.randomOrNull()
        if (arena == null) {
            plugin.messages.send(s, "auto.none")
            return
        }
        if (plugin.arenas.autojoin(p)) plugin.messages.send(s, "auto.joined", mapOf("arena" to arena.displayName))
        else plugin.messages.send(s, "join.fail")
    }

    private fun forcestart(s: CommandSender) {
        if (!s.hasPermission("enspillars.forcestart") && !s.hasPermission("enspillars.admin")) {
            deny(s)
            return
        }
        val p = s as? Player ?: run { plugin.messages.send(s, "join.not-player"); return }
        val en = plugin.arenas.engineFor(p.uniqueId) ?: run { plugin.messages.send(s, "vote.not-in-game"); return }
        if (en.forceStart()) plugin.messages.send(s, "force.started") else plugin.messages.send(s, "force.fail")
    }

    fun tabComplete(s: CommandSender, sub: String, a: List<String>): List<String> {
        if (sub == "join") {
            return plugin.arenas.all().filter { it.state == GameState.WAITING }.map { it.id }
                .filter { it.startsWith(a.getOrNull(0) ?: "", true) }
        }
        if (sub == "spectate") {
            val inGame = plugin.server.onlinePlayers.filter { plugin.arenas.engineFor(it.uniqueId) != null }
            return inGame.map { it.name }.filter { it.startsWith(a.getOrNull(0) ?: "", true) }
        }
        return emptyList()
    }

    private fun deny(s: CommandSender) = plugin.messages.send(s, "no-permission")
}
