package dev.ensisdev.enspillars.snapshot

import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

/**
 * Oyuncunun oyun öncesi durumunun tam görüntüsü.
 * prepare() oyuncunun envanterini sildiği için join anında alınır,
 * oyundan her çıkışta (leave/reset/quit/reconnect) geri yüklenir.
 */
data class PlayerSnapshot(
    val contents: Array<ItemStack?>,
    val armor: Array<ItemStack?>,
    val offHand: ItemStack?,
    val effects: List<EffectData>,
    val level: Int,
    val exp: Float,
    val totalExp: Int,
    val health: Double,
    val food: Int,
    val saturation: Float,
    val gameMode: GameMode,
    val allowFlight: Boolean,
    val flying: Boolean,
    val flySpeed: Float,
    val fireTicks: Int,
    val location: Location?
) {
    companion object {
        fun capture(p: Player) = PlayerSnapshot(
            p.inventory.storageContents.map { it?.clone() }.toTypedArray(),
            p.inventory.armorContents.map { it?.clone() }.toTypedArray(),
            p.inventory.itemInOffHand.clone(),
            p.activePotionEffects.map { EffectData(it.type.name, it.amplifier, it.duration, it.isAmbient, it.hasParticles(), it.hasIcon()) },
            p.level, p.exp, p.totalExperience,
            p.health, p.foodLevel, p.saturation,
            p.gameMode, p.allowFlight, p.isFlying, p.flySpeed,
            p.fireTicks, p.location.clone()
        )
    }

    /** Oyun sırasında edinilen her şeyi silip oyun öncesi durumu geri yükler. */
    fun restore(p: Player) {
        p.inventory.storageContents = contents.map { it?.clone() }.toTypedArray()
        p.inventory.armorContents = armor.map { it?.clone() }.toTypedArray()
        p.inventory.setItemInOffHand(offHand?.clone())
        p.activePotionEffects.forEach { runCatching { p.removePotionEffect(it.type) } }
        effects.forEach { e ->
            val type = PotionEffectType.getByName(e.name) ?: return@forEach
            runCatching { p.addPotionEffect(PotionEffect(type, e.duration, e.amplifier, e.ambient, e.particles, e.icon)) }
        }
        p.level = level; p.exp = exp
        runCatching { p.totalExperience = totalExp }
        p.health = health.coerceAtLeast(0.0).coerceAtMost(p.maxHealth)
        p.foodLevel = food.coerceIn(0, 20); p.saturation = saturation.coerceIn(0f, 20f)
        p.gameMode = gameMode
        p.allowFlight = allowFlight
        runCatching { if (!allowFlight && flying) p.isFlying = false else p.isFlying = flying }
        p.flySpeed = flySpeed.coerceIn(-1f, 1f)
        p.fireTicks = 0; p.fallDistance = 0f
    }

    data class EffectData(val name: String, val amplifier: Int, val duration: Int, val ambient: Boolean, val particles: Boolean, val icon: Boolean)
}
