package dev.ensisdev.enspillars.command.pof

import dev.ensisdev.enspillars.EnsPillarsPlugin
import dev.ensisdev.enspillars.api.GameState
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/** /pof vote game|map <değer> — chat üzerinden oy.
 *
 * Enum kaynağı: ArenaSetupMenu oyun/harita döngüleri + ModifierService
 * mod listesiyle senkron tutulur (SHRINKING_BORDER dahil).
 */
class VoteCommands(private val plugin: EnsPillarsPlugin) {
    companion object {
        /** Oyun modları (ArenaSetupMenu L23 ile senkron). */
        val GAME_MODES = listOf("NORMAL", "SHUFFLE", "SWAP")

        /** Harita modları (ArenaSetupMenu L24 + ModifierService ile senkron). */
        val MAP_MODES = listOf("NORMAL", "LAVA_RISE", "FRAGILE", "TNT_RAIN", "ABLOCKALYPSE", "SHRINKING_BORDER")
    }
    fun execute(s: CommandSender, a: List<String>): Boolean {
        if (!s.hasPermission("enspillars.vote")) {
            deny(s)
            return true
        }
        val p = s as? Player ?: run { plugin.messages.send(s, "join.not-player"); return true }
        val en = plugin.arenas.engineFor(p.uniqueId) ?: run { plugin.messages.send(s, "vote.not-in-game"); return true }
        if (en.state() !in setOf(GameState.WAITING, GameState.STARTING, GameState.PRE_GAME, GameState.CAGED)) {
            plugin.messages.send(s, "vote.closed")
            return true
        }
        if (!en.vote.tryCooldown(p.uniqueId)) {
            plugin.messages.send(s, "vote.cooldown")
            return true
        }
        when (a.firstOrNull()?.lowercase()) {
            "game" -> {
                val v = a.getOrNull(1) ?: run { plugin.messages.send(s, "vote.usage"); return true }
                if (en.vote.voteGame(p.uniqueId, v)) plugin.messages.send(s, "vote.game-ok", mapOf("value" to v.uppercase()))
                else plugin.messages.send(s, "vote.invalid")
            }
            "map" -> {
                val v = a.getOrNull(1) ?: run { plugin.messages.send(s, "vote.usage"); return true }
                if (en.vote.voteMap(p.uniqueId, v)) plugin.messages.send(s, "vote.map-ok", mapOf("value" to v.uppercase()))
                else plugin.messages.send(s, "vote.invalid")
            }
            else -> plugin.messages.send(s, "vote.usage")
        }
        return true
    }

    fun tabComplete(s: CommandSender, a: List<String>): List<String> {
        if (!s.hasPermission("enspillars.vote") && !s.hasPermission("enspillars.admin")) return emptyList()
        if (a.size == 1) return listOf("game", "map").filter { it.startsWith(a[0], true) }
        if (a.size == 2) {
            // P0: SHRINKING_BORDER eksikti; MAP_MODES tek kaynaktan beslenir.
            val pool = if (a[0].equals("game", true)) GAME_MODES else MAP_MODES
            if (!a[0].equals("game", true) && !a[0].equals("map", true)) return emptyList()
            return pool.filter { it.startsWith(a[1], true) }
        }
        return emptyList()
    }

    private fun deny(s: CommandSender) = plugin.messages.send(s, "no-permission")
}
