package dev.ensisdev.enspillars.integration

import dev.ensisdev.enspillars.EnsPillarsPlugin
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer

/**
 * %enspillars_<key>% placeholder'ları. PAPI varsa açlışta kaydolur.
 * Anahtarlar: kills, deaths, wins, games, draws, points, coins,
 * streak, max_streak, arena, arena_state, arena_players
 */
class EnsPillarsExpansion(private val plugin: EnsPillarsPlugin) : PlaceholderExpansion() {
    override fun getIdentifier() = "enspillars"
    override fun getAuthor() = "EnsStudios"
    override fun getVersion() = plugin.description.version
    override fun persist() = true

    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        if (player == null) return ""
        return when (params.lowercase()) {
            "kills" -> stat(player) { it.kills.toString() }
            "deaths" -> stat(player) { it.deaths.toString() }
            "wins" -> stat(player) { it.wins.toString() }
            "games" -> stat(player) { it.games.toString() }
            "draws" -> stat(player) { it.draws.toString() }
            "points" -> stat(player) { it.points.toString() }
            "coins" -> stat(player) { it.coins.toString() }
            "streak" -> stat(player) { it.winStreak.toString() }
            "max_streak" -> stat(player) { it.maxWinStreak.toString() }
            "arena" -> plugin.arenas.engineFor(player.uniqueId)?.arenaIdForReplay() ?: ""
            "arena_state" -> plugin.arenas.engineFor(player.uniqueId)?.state()?.name ?: ""
            "arena_players" -> plugin.arenas.engineFor(player.uniqueId)?.playerCount()?.toString() ?: "0"
            else -> null
        }
    }

    private inline fun stat(player: OfflinePlayer, f: (dev.ensisdev.enspillars.stats.PlayerStats) -> String) =
        runCatching { f(plugin.stats.load(player.uniqueId)) }.getOrDefault("0")
}
