package dev.ensisdev.enspillars.killmessage

import dev.ensisdev.enspillars.EnsPillarsPlugin
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageEvent

/**
 * Kill mesajları: katilin seçili paketindeki şablon, sebebe göre seçilir.
 * Şablonlar shop.yml killmessages.<id>.messages içindedir.
 */
class KillMessages(private val plugin: EnsPillarsPlugin) {
    fun format(killer: Player?, victim: Player, cause: EntityDamageEvent.DamageCause?): String {
        val pack = killer?.let { plugin.cosmetics.selectedKillMessage(it.uniqueId) }?.takeIf { it.isNotEmpty() }
        val templates = pack?.let {
            runCatching { plugin.shopCatalog.killTemplates[it.lowercase()] }.getOrNull()
        }
        val key = when (cause) {
            EntityDamageEvent.DamageCause.VOID -> "void"
            EntityDamageEvent.DamageCause.FALL -> "fall"
            EntityDamageEvent.DamageCause.PROJECTILE -> "projectile"
            EntityDamageEvent.DamageCause.FIRE, EntityDamageEvent.DamageCause.FIRE_TICK,
            EntityDamageEvent.DamageCause.LAVA -> "fire"
            EntityDamageEvent.DamageCause.MAGIC -> "magic"
            EntityDamageEvent.DamageCause.WITHER -> "wither"
            EntityDamageEvent.DamageCause.BLOCK_EXPLOSION, EntityDamageEvent.DamageCause.ENTITY_EXPLOSION -> "explosion"
            else -> "kill"
        }
        val template = templates?.get(key) ?: templates?.get("kill")
            ?: if (killer != null) "&b%killer% &7adlı oyuncuyu öldürdü &b%victim%" else "&c%victim% &7elendi"
        return ChatColor.translateAlternateColorCodes('&',
            template.replace("%killer%", killer?.name ?: "?").replace("%victim%", victim.name))
    }
}
