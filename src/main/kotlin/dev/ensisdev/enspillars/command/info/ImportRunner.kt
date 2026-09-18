package dev.ensisdev.enspillars.command.info

import dev.ensisdev.enspillars.EnsPillarsPlugin
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.UUID

/** YAML dosyalarından SQL backend'e tek seferlik taşıma.
 *
 * P0: tek-yönlü veri taşıma olduğu için iki-aşamalı onay ister:
 * `/enspillars import` ilk çağrıda uyarır, aynı komut 60 sn içinde
 * tekrar edilirse (veya `confirm` eklenirse) başlar.
 */
class ImportRunner(private val plugin: EnsPillarsPlugin) {
    fun run(s: CommandSender, a: List<String> = emptyList()) {
        if (!plugin.dbReady()) {
            plugin.messages.send(s, "import.backend-off")
            return
        }
        // P0: onaysız import yasak.
        val explicit = a.any { it.equals("confirm", true) }
        if (!plugin.confirms.check(s, "import", "yaml-to-sql", explicit)) return
        plugin.logger.info("AUDIT actor=${s.name} action=import scope=yaml-to-sql")
        plugin.messages.send(s, "import.started")
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            var n = 0
            runCatching {
                val yf = File(plugin.dataFolder, "players.yml")
                if (yf.isFile) {
                    val y = YamlConfiguration.loadConfiguration(yf)
                    y.getKeys(false).forEach { k ->
                        runCatching {
                            val u = UUID.fromString(k)
                            val st = dev.ensisdev.enspillars.stats.PlayerStats(y.getInt("$k.games"), y.getInt("$k.wins"), y.getInt("$k.draws"), y.getInt("$k.losses"), y.getInt("$k.kills"), y.getInt("$k.deaths"), y.getInt("$k.coins"), y.getInt("$k.points"), y.getInt("$k.win-streak"), y.getInt("$k.max-win-streak"), y.getLong("$k.play-seconds"), y.getLong("$k.last-game"))
                            val name = runCatching { org.bukkit.Bukkit.getOfflinePlayer(u).name ?: "" }.getOrDefault("")
                            dev.ensisdev.enspillars.storage.PlayerTable.save(plugin.db, u, name, st, "", "", "")
                            n++
                        }
                    }
                }
                val cf = File(plugin.dataFolder, "cosmetics.yml")
                if (cf.isFile) {
                    val cm = YamlConfiguration.loadConfiguration(cf)
                    cm.getKeys(false).forEach { k ->
                        runCatching {
                            val u = UUID.fromString(k)
                            dev.ensisdev.enspillars.storage.PlayerTable.updateCosmetics(plugin.db, u, cm.getString("$k.cage") ?: "", cm.getString("$k.killmessage") ?: "", cm.getString("$k.deathcry") ?: "")
                            val owned = cm.getStringList("$k.owned")
                            owned.groupBy { it.substringBefore(":") }.forEach { (type, ids) ->
                                dev.ensisdev.enspillars.storage.OwnedTable.sync(plugin.db, u, type, ids.map { it.substringAfter(":") }.toSet())
                            }
                        }
                    }
                }
            }.onFailure { plugin.logger.warning("Import hatası: ${it.message}") }
            plugin.messages.send(s, "import.done", mapOf("count" to n))
        })
    }
}
