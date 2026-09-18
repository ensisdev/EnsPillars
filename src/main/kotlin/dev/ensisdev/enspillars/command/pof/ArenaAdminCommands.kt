package dev.ensisdev.enspillars.command.pof

import dev.ensisdev.enspillars.EnsPillarsPlugin
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/** /pof arena list|info|create|delete|enable|disable|tp|setup|edit|rollback + /pof setspawn + /pof setup.
 *
 * Yıkıcı dallar (delete/rollback/setspawn) iki-aşamalı onay ister:
 * ilk çağrı uyarır, aynı komut 60 sn içinde tekrar edilirse (veya satır
 * sonunda `confirm` yazılırsa) uygulanır. Ayrıntılar [ConfirmService].
 * Renk sistemi: legacy `&` (belgeli tercih).
 */
class ArenaAdminCommands(private val plugin: EnsPillarsPlugin) {
    private fun can(s: CommandSender, node: String): Boolean =
        s.hasPermission(node) || (node != "enspillars.admin" && s.hasPermission("enspillars.admin"))

    fun execute(s: CommandSender, a: List<String>): Boolean {
        if (!can(s, "enspillars.admin.arena")) {
            deny(s)
            return true
        }
        when (a.firstOrNull()?.lowercase()) {
            "list" -> {
                plugin.messages.send(s, "arena.list-header", mapOf("count" to plugin.arenas.size))
                plugin.arenas.all().forEach { z ->
                    plugin.messages.send(s, "arena.list-row", mapOf("id" to z.id, "state" to z.state, "count" to z.playerCount(), "max" to z.maxPlayers))
                }
            }
            "info" -> {
                val id = a.getOrNull(1) ?: run { plugin.messages.send(s, "arena-admin.usage"); return true }
                val info = plugin.arenas.get(id)
                if (info == null) plugin.messages.send(s, "arena-admin.not-found")
                else plugin.messages.send(s, "arena.info-row", mapOf("id" to info.id, "state" to info.state, "count" to info.playerCount(), "max" to info.maxPlayers, "spawns" to info.spawnCount(), "gamemode" to info.defaultGameMode, "mapmode" to info.defaultMapMode))
            }
            "create" -> {
                val id = a.getOrNull(1) ?: run { plugin.messages.send(s, "arena-admin.usage"); return true }
                if (!plugin.setup.create(id)) {
                    plugin.messages.send(s, "arena-admin.create-fail")
                    return true
                }
                plugin.arenas.reloadSingle(id)
                plugin.messages.send(s, "arena-admin.created")
            }
            "delete" -> {
                val raw = a.getOrNull(1) ?: run { plugin.messages.send(s, "arena-admin.delete-usage"); return true }
                // `confirm` kelimesi satır sonunda da kabul edilir.
                val explicit = a.drop(2).any { it.equals("confirm", true) }
                val id = if (raw.equals("confirm", true)) {
                    plugin.messages.send(s, "arena-admin.delete-usage"); return true
                } else raw
                val en = plugin.arenas.engine(id)
                if (en != null && en.playerCount() > 0) {
                    plugin.messages.send(s, "arena-admin.active-block")
                    return true
                }
                // P0: dosya silme onaysız yapılamaz (iki-aşamalı onay).
                if (!plugin.confirms.check(s, "arena-delete", id, explicit)) return true
                plugin.logger.info("AUDIT actor=${s.name} action=arena-delete scope=$id")
                plugin.messages.send(
                    s,
                    if (plugin.setup.remove(id)) {
                        plugin.arenas.removeArena(id)
                        "arena-admin.deleted"
                    } else "arena-admin.not-found"
                )
            }
            "enable", "disable" -> {
                val id = a.getOrNull(1) ?: run { plugin.messages.send(s, "arena-admin.usage"); return true }
                val en = plugin.arenas.engine(id)
                if (en != null && en.playerCount() > 0) {
                    plugin.messages.send(s, "arena-admin.active-block")
                    return true
                }
                plugin.messages.send(
                    s,
                    if (plugin.setup.setEnabled(id, a[0].equals("enable", true))) {
                        plugin.arenas.reloadSingle(id)
                        "arena-admin.state-updated"
                    } else "arena-admin.not-found"
                )
            }
            "tp" -> {
                val p = s as? Player ?: run { plugin.messages.send(s, "join.not-player"); return true }
                val id = a.getOrNull(1) ?: run { plugin.messages.send(s, "arena-admin.usage"); return true }
                val info = plugin.arenas.get(id)
                if (info == null) {
                    plugin.messages.send(s, "arena-admin.not-found")
                    return true
                }
                val dest = info.center() ?: info.spawn(0)
                if (dest == null) {
                    plugin.messages.send(s, "setup.incomplete", mapOf("missing" to "center"))
                    return true
                }
                p.teleport(dest)
                plugin.messages.send(s, "setup.teleported", mapOf("n" to id))
            }
            "setup", "edit" -> {
                val p = s as? Player ?: run { plugin.messages.send(s, "join.not-player"); return true }
                if (!can(s, "enspillars.admin.setup")) {
                    deny(s)
                    return true
                }
                val id = a.getOrNull(1) ?: run { plugin.messages.send(s, "arena-admin.setup-usage"); return true }
                if (plugin.arenas.get(id) == null) {
                    plugin.messages.send(s, "arena-admin.not-found")
                    return true
                }
                if (plugin.arenas.engine(id)?.playerCount()?.let { it > 0 } == true) {
                    plugin.messages.send(s, "arena-admin.active-block")
                    return true
                }
                if (plugin.setupMode.inSetup(p.uniqueId)) {
                    plugin.setupMode.exit(p)
                    return true
                }
                plugin.setupMode.enter(p, id)
            }
            "rollback" -> {
                val raw = a.getOrNull(1) ?: run { plugin.messages.send(s, "arena-admin.rollback-usage"); return true }
                val explicit = a.drop(2).any { it.equals("confirm", true) }
                val id = if (raw.equals("confirm", true)) {
                    plugin.messages.send(s, "arena-admin.rollback-usage"); return true
                } else raw
                val en = plugin.arenas.engine(id)
                if (en == null) {
                    plugin.messages.send(s, "arena-admin.not-found")
                    return true
                }
                if (en.playerCount() > 0) {
                    plugin.messages.send(s, "arena-admin.rollback-busy")
                    return true
                }
                val tracked = en.trackedBlocks()
                if (tracked <= 0) {
                    plugin.messages.send(s, "arena-admin.rollback-empty")
                    return true
                }
                // P0: blok geri-yükleme onaysız yapılamaz (iki-aşamalı onay).
                if (!plugin.confirms.check(s, "arena-rollback", "$id:$tracked", explicit)) return true
                plugin.logger.info("AUDIT actor=${s.name} action=arena-rollback scope=$id:$tracked")
                if (!en.rollbackBlocks()) {
                    plugin.messages.send(s, "arena-admin.rollback-busy")
                    return true
                }
                plugin.messages.send(s, "arena-admin.rollback-done", mapOf("blocks" to tracked))
            }
            else -> plugin.messages.send(s, "arena-admin.usage")
        }
        return true
    }

    /** Doc-drift çözümü (43/44 EKLE): `/pof setup <id>` → arena setup alias'ı. */
    fun setupAlias(s: CommandSender, a: List<String>): Boolean {
        if (!can(s, "enspillars.admin.setup")) {
            deny(s)
            return true
        }
        val id = a.getOrNull(0)
        if (id == null || id.equals("confirm", true)) {
            plugin.messages.send(s, "arena-admin.setup-usage")
            return true
        }
        return execute(s, listOf("arena", "setup", id) + a.drop(1))
    }

    fun setspawn(s: CommandSender, a: List<String>): Boolean {
        val p = s as? Player ?: run { plugin.messages.send(s, "join.not-player"); return true }
        if (!can(s, "enspillars.admin.setup")) {
            deny(s)
            return true
        }
        // P0: global dönüş noktası tek adımda ezilemez (iki-aşamalı onay).
        val explicit = a.any { it.equals("confirm", true) }
        val w = p.location.world
        val detail = "${w?.name}:${p.location.blockX},${p.location.blockY},${p.location.blockZ}"
        if (!plugin.confirms.check(s, "setspawn", detail, explicit)) return true
        plugin.logger.info("AUDIT actor=${s.name} action=setspawn scope=$detail")
        if (w == null) return true
        plugin.config.set("global-lobby", listOf(w.name, p.location.x, p.location.y, p.location.z, p.location.yaw, p.location.pitch))
        plugin.saveConfig()
        plugin.messages.send(s, "setup.setspawn-ok")
        return true
    }

    fun tabComplete(s: CommandSender, a: List<String>): List<String> {
        if (!can(s, "enspillars.admin.arena") && !can(s, "enspillars.admin.setup")) return emptyList()
        if (a.size == 1) {
            return listOf("list", "info", "create", "delete", "enable", "disable", "tp", "setup", "edit", "rollback")
                .filter { it.startsWith(a[0], true) }
        }
        if (a.size == 2 && a[0].lowercase() !in setOf("list", "create")) {
            return plugin.arenas.all().map { it.id }.filter { it.startsWith(a[1], true) }
        }
        return emptyList()
    }

    private fun deny(s: CommandSender) = plugin.messages.send(s, "no-permission")
}
