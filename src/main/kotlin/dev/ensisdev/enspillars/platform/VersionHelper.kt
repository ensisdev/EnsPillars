package dev.ensisdev.enspillars.platform

import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.potion.PotionEffectType

/**
 * Çok sürümlü uyumluluk yardımcıları. Config'den gelen isimleri
 * sürüme dayanıklı şekilde çözer; bulunamazsa güvenli varsayılan döner.
 */
object VersionHelper {
    /** "BLOCK_CRACK" gibi particle adı + opsiyonel blok datası ile güvenli çözüm. */
    fun particle(name: String): Particle =
        runCatching { Particle.valueOf(name.uppercase()) }.getOrDefault(Particle.CRIT)

    fun sound(name: String): Sound =
        runCatching { Sound.valueOf(name.uppercase()) }.getOrDefault(Sound.UI_BUTTON_CLICK)

    fun potionEffect(name: String): PotionEffectType? =
        runCatching { PotionEffectType.getByName(name.uppercase()) }.getOrNull()

    /** Sunucu minör sürümü (1.20 -> 20). */
    fun minorVersion(serverVersion: String): Int =
        runCatching { serverVersion.substringBefore('-').substringBefore('+').split('.')[1].filter { it.isDigit() }.toInt() }.getOrDefault(20)
}
