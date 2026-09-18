package dev.ensisdev.enspillars.gui

import dev.ensisdev.enspillars.EnsPillarsPlugin
import org.bukkit.ChatColor
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Eşya geri-sayım bossbar'ı. Maç sonu/çıkışta kapatılır. */
class BossBarService(private val plugin: EnsPillarsPlugin) {
    private val bars = ConcurrentHashMap<UUID, org.bukkit.boss.BossBar>()
    private val enabled get() = plugin.config.getBoolean("bossbar.enabled", true)

    fun show(p: Player, text: String, progress: Double) {
        if (!enabled) return
        hide(p)
        runCatching {
            val bar = plugin.server.createBossBar(color(text), BarColor.YELLOW, BarStyle.SOLID)
            bar.addPlayer(p)
            bar.progress = progress.coerceIn(0.0, 1.0)
            bars[p.uniqueId] = bar
        }
    }

    fun update(p: Player, text: String, progress: Double) {
        val bar = bars[p.uniqueId] ?: run { show(p, text, progress); return }
        runCatching {
            bar.setTitle(color(text))
            bar.progress = progress.coerceIn(0.0, 1.0)
        }.onFailure { hide(p) }
    }

    fun hide(p: Player) {
        bars.remove(p.uniqueId)?.let { runCatching { it.removeAll() } }
    }

    fun hideAll() {
        bars.keys.toList().forEach { u -> plugin.server.getPlayer(u)?.let(::hide) ?: bars.remove(u)?.let { runCatching { it.removeAll() } } }
    }

    private fun color(s: String) = ChatColor.translateAlternateColorCodes('&', s)
}
