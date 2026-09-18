package dev.ensisdev.enspillars.api

import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/** Oyuncu istatistiği değiştiğinde ateşlenir (skorboard/shop tazeleme için). */
class PlayerStatsChangeEvent(val player: Player, val stat: StatType) : Event() {
    override fun getHandlers() = HANDLERS
    companion object { @JvmStatic val HANDLERS = HandlerList(); @JvmStatic fun getHandlerList() = HANDLERS }
}

enum class StatType {
    GAMES, WINS, DRAWS, LOSSES, KILLS, DEATHS, COINS, POINTS, WIN_STREAK;

    companion object {
        fun fromString(name: String) = values().firstOrNull { it.name.equals(name, true) }
    }
}
