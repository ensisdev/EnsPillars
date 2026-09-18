package dev.ensisdev.enspillars.command.pof

import dev.ensisdev.enspillars.EnsPillarsPlugin
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/** /pof party create|invite|accept|decline|leave|join — parti yönetimi. */
class PartyCommands(private val plugin: EnsPillarsPlugin) {
    fun execute(s: CommandSender, a: List<String>): Boolean {
        if (!s.hasPermission("enspillars.party")) {
            deny(s)
            return true
        }
        val p = s as? Player ?: run { plugin.messages.send(s, "join.not-player"); return true }
        val m = plugin.messages
        when (a.firstOrNull()?.lowercase()) {
            "create" -> m.send(p, if (plugin.parties.create(p.uniqueId)) "party.created" else "party.already")
            "invite" -> {
                val t = a.getOrNull(1)?.let { plugin.server.getPlayer(it) }
                    ?: run { m.send(s, "party.invite-usage"); return true }
                if (plugin.parties.invite(p.uniqueId, t.uniqueId)) m.send(s, "party.invite-sent", mapOf("player" to t.name))
                else m.send(s, "party.invite-fail")
            }
            "accept" -> {
                val o = a.getOrNull(1)?.let { plugin.server.getPlayer(it)?.uniqueId }
                    ?: plugin.parties.pendingInvites(p.uniqueId).firstOrNull()
                if (o == null) {
                    m.send(s, "party.no-invite")
                    return true
                }
                m.send(s, if (plugin.parties.accept(p.uniqueId, o)) "party.accepted" else "party.accept-fail")
            }
            "decline" -> {
                val o = a.getOrNull(1)?.let { plugin.server.getPlayer(it)?.uniqueId }
                    ?: plugin.parties.pendingInvites(p.uniqueId).firstOrNull()
                if (o == null) {
                    m.send(s, "party.no-invite")
                    return true
                }
                plugin.parties.decline(p.uniqueId, o)
                m.send(s, "party.declined")
            }
            "leave" -> {
                plugin.parties.leave(p.uniqueId)
                m.send(s, "party.left")
            }
            "join" -> {
                // Bilinçli karar: join onaysızdır — tersinir işlemdir (leave ile geri alınır), confirm kapısı gerekmez.
                val arena = a.getOrNull(1) ?: run { m.send(s, "party.join-usage"); return true }
                var ok = 0
                plugin.parties.members(p.uniqueId).mapNotNull { plugin.server.getPlayer(it) }.forEach {
                    if (plugin.arenas.join(it, arena)) ok++
                }
                m.send(s, "party.joined", mapOf("count" to ok))
            }
            else -> m.send(s, "party.usage")
        }
        return true
    }

    fun tabComplete(s: CommandSender, a: List<String>): List<String> {
        if (!s.hasPermission("enspillars.party")) return emptyList()
        val p = s as? Player
        if (a.size == 1) {
            return listOf("create", "invite", "accept", "decline", "leave", "join")
                .filter { it.startsWith(a[0], true) }
        }
        if (a.size == 2) {
            return when (a[0].lowercase()) {
                "invite" -> plugin.server.onlinePlayers
                    .filter { it.uniqueId != p?.uniqueId }
                    .map { it.name }.filter { it.startsWith(a[1], true) }
                "accept", "decline" -> plugin.parties.pendingInvites(p?.uniqueId ?: return emptyList())
                    .mapNotNull { plugin.server.getPlayer(it)?.name }
                    .filter { it.startsWith(a[1], true) }
                "join" -> plugin.arenas.all().map { it.id }.filter { it.startsWith(a[1], true) }
                else -> emptyList()
            }
        }
        return emptyList()
    }

    private fun deny(s: CommandSender) = plugin.messages.send(s, "no-permission")
}
