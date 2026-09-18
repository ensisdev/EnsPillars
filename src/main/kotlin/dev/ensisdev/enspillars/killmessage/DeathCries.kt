package dev.ensisdev.enspillars.killmessage

import dev.ensisdev.enspillars.EnsPillarsPlugin
import dev.ensisdev.enspillars.platform.VersionHelper
import org.bukkit.entity.Player

/** Kurbanın seçili ölüm sesini arenadaki herkese çalar. */
class DeathCries(private val plugin: EnsPillarsPlugin) {
    fun play(hearers: Collection<Player>, victim: Player) {
        val id = plugin.cosmetics.selectedDeathCry(victim.uniqueId).takeIf { it.isNotEmpty() } ?: return
        val def = runCatching { plugin.shopCatalog.deathCries[id.lowercase()] }.getOrNull() ?: return
        val soundName = def.extra["sound"]?.takeIf { it.isNotEmpty() } ?: return
        val sound = VersionHelper.sound(soundName)
        val pitch = def.extra["pitch"]?.toFloatOrNull() ?: 1f
        hearers.forEach { p -> runCatching { p.playSound(p.location, sound, 1f, pitch) } }
    }
}
