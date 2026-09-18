package dev.ensisdev.enspillars.gui

import dev.ensisdev.enspillars.EnsPillarsPlugin
import fr.mrmicky.fastboard.FastBoard
import org.bukkit.ChatColor
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * FastBoard tabanlı skorboard. Şablonlar scoreboards.yml'den okunur,
 * içerik değişmedikçe paket göndermez. Maç sonu/çıkışta silinir.
 */
class ScoreboardService(private val plugin: EnsPillarsPlugin) {
    private val boards = ConcurrentHashMap<UUID, FastBoard>()
    private val lastLines = ConcurrentHashMap<UUID, List<String>>()
    private var title = "§b§lEnsPillars"
    private var templates = mapOf<String, List<String>>()
    private val enabled get() = plugin.config.getBoolean("scoreboard.enabled", true)

    init { reload() }

    fun reload() {
        runCatching {
            val y = YamlConfiguration.loadConfiguration(File(plugin.dataFolder, "scoreboards.yml"))
            title = y.getString("title", title) ?: title
            val map = mutableMapOf<String, List<String>>()
            y.getKeys(false).filter { it != "enabled" && it != "title" }.forEach { k ->
                map[k.uppercase()] = y.getStringList(k)
            }
            if (map.isNotEmpty()) templates = map
        }.onFailure { plugin.logger.warning("scoreboards.yml okunamadı: ${it.message}") }
    }

    /** %anahtar% yer tutucularını doldurup durum şablonunu döndürür. */
    fun render(state: String, ph: Map<String, String>): List<String> {
        val t = templates[state.uppercase()] ?: templates["ACTIVE"] ?: return emptyList()
        return t.map { line ->
            var s = line
            ph.forEach { (k, v) -> s = s.replace("%$k%", v) }
            s
        }
    }

    fun show(p: Player, lines: List<String>) {
        if (!enabled) return
        hide(p)
        if (lines.isEmpty()) return
        runCatching {
            val b = FastBoard(p)
            b.updateTitle(color(title))
            b.updateLines(lines.map { color(it) })
            boards[p.uniqueId] = b
            lastLines[p.uniqueId] = lines
        }.onFailure { plugin.logger.warning("Skorboard açılamadı: ${it.message}") }
    }

    fun update(p: Player, lines: List<String>) {
        if (!enabled) return
        if (lines.isEmpty()) {
            hide(p)
            return
        }
        if (lastLines[p.uniqueId] == lines) return
        val b = boards[p.uniqueId]
        if (b == null || b.isDeleted) {
            boards.remove(p.uniqueId)
            show(p, lines)
            return
        }
        runCatching {
            b.updateTitle(color(title))
            b.updateLines(lines.map { color(it) })
            lastLines[p.uniqueId] = lines
        }.onFailure { hide(p) }
    }

    fun hide(p: Player) {
        boards.remove(p.uniqueId)?.let { runCatching { if (!it.isDeleted) it.delete() } }
        lastLines.remove(p.uniqueId)
    }

    fun hideAll() {
        boards.keys.toList().forEach { u ->
            plugin.server.getPlayer(u)?.let(::hide) ?: run {
                boards.remove(u)?.let { runCatching { if (!it.isDeleted) it.delete() } }
                lastLines.remove(u)
            }
        }
    }

    private fun color(s: String) = ChatColor.translateAlternateColorCodes('&', s)
}
