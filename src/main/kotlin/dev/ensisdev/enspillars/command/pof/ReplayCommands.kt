package dev.ensisdev.enspillars.command.pof

import dev.ensisdev.enspillars.EnsPillarsPlugin
import org.bukkit.command.CommandSender

/** /pof replay list|latest|open|download|delete — tekrar yönetimi.
 *
 * `delete` yıkıcıdır: iki-aşamalı onay ister (bkz. [ConfirmService]).
 * `latest [arena]` opsiyonel arena filtresi alır; completion L2'de arena
 * ve replay kimlikleri önerilir.
 */
class ReplayCommands(private val plugin: EnsPillarsPlugin) {
    fun execute(s: CommandSender, a: List<String>): Boolean {
        if (!s.hasPermission("enspillars.replay") && !s.hasPermission("enspillars.admin")) {
            deny(s)
            return true
        }
        val m = plugin.messages
        when (a.firstOrNull()?.lowercase()) {
            "list" -> {
                val all = plugin.replay.list()
                all.take(50).forEach { f -> plugin.messages.send(s, "replay.list-row", mapOf("id" to f.nameWithoutExtension, "kb" to (f.length() / 1024))) }
                if (all.isEmpty()) m.send(s, "replay.none")
            }
            "latest" -> {
                val f = plugin.replay.latest(a.getOrNull(1))
                if (f == null) m.send(s, "replay.none")
                else {
                    val url = plugin.replay.openUrl(f)
                    val port = plugin.config.getInt("replay.web.port", 8765)
                    m.send(s, "replay.header", mapOf("id" to f.nameWithoutExtension))
                    if (url != null) m.send(s, "replay.open-url", mapOf("url" to url))
                    else s.sendMessage(m.get("replay.no-url-port", mapOf("port" to port)))
                }
            }
            "open" -> {
                val id = a.getOrNull(1) ?: run { m.send(s, "replay.usage"); return true }
                val f = plugin.replay.byId(id)
                if (f == null) m.send(s, "replay.none")
                else {
                    val url = plugin.replay.openUrl(f)
                    if (url != null) m.send(s, "replay.open-url", mapOf("url" to url))
                    else m.send(s, "replay.open-id", mapOf("id" to f.nameWithoutExtension))
                }
            }
            "download" -> {
                val id = a.getOrNull(1) ?: run { m.send(s, "replay.usage"); return true }
                val f = plugin.replay.byId(id)
                if (f == null) m.send(s, "replay.none")
                else {
                    val url = plugin.replay.downloadUrl(f)
                    if (url != null) m.send(s, "replay.open-url", mapOf("url" to url))
                    else m.send(s, "replay.open-id", mapOf("id" to f.nameWithoutExtension))
                }
            }
            "delete" -> {
                if (!s.hasPermission("enspillars.admin.arena") && !s.hasPermission("enspillars.admin")) {
                    deny(s)
                    return true
                }
                val raw = a.getOrNull(1) ?: run { m.send(s, "replay.usage"); return true }
                val explicit = a.drop(2).any { it.equals("confirm", true) }
                val id = if (raw.equals("confirm", true)) {
                    m.send(s, "replay.usage"); return true
                } else raw
                // P0: replay dosyası onaysız silinemez (iki-aşamalı onay).
                if (!plugin.confirms.check(s, "replay-delete", id, explicit)) return true
                plugin.logger.info("AUDIT actor=${s.name} action=replay-delete scope=$id")
                m.send(s, if (plugin.replay.deleteReplay(id)) "replay.deleted" else "replay.none")
            }
            else -> m.send(s, "replay.usage")
        }
        return true
    }

    fun tabComplete(s: CommandSender, a: List<String>): List<String> {
        if (!s.hasPermission("enspillars.replay") && !s.hasPermission("enspillars.admin")) return emptyList()
        if (a.size == 1) {
            val subs = mutableListOf("list", "latest", "open", "download")
            // P0: delete admin dalıdır; yetkisize completion'da sizdirilmasin.
            if (s.hasPermission("enspillars.admin.arena") || s.hasPermission("enspillars.admin")) subs += "delete"
            return subs.filter { it.startsWith(a[0], true) }
        }
        if (a.size == 2) {
            return when (a[0].lowercase()) {
                // P0: L2 hep boştu; arena filtresi + replay kimlikleri önerilir.
                "latest" -> plugin.arenas.all().map { it.id }.filter { it.startsWith(a[1], true) }
                "open", "download", "delete" -> {
                    if (a[0].equals("delete", true) && !s.hasPermission("enspillars.admin.arena") && !s.hasPermission("enspillars.admin")) return emptyList()
                    plugin.replay.list().map { it.nameWithoutExtension }.filter { it.startsWith(a[1], true) }
                }
                else -> emptyList()
            }
        }
        if (a.size == 3 && a[0].lowercase() in setOf("delete", "open", "download")) {
            return listOf("confirm").filter { it.startsWith(a[2], true) }
        }
        return emptyList()
    }

    private fun deny(s: CommandSender) = plugin.messages.send(s, "no-permission")
}
