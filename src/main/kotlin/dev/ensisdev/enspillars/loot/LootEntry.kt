package dev.ensisdev.enspillars.loot

import org.bukkit.Material

data class LootEntry(val material: Material, val minAmount: Int, val maxAmount: Int, val weight: Double) {
    fun amount(random: java.util.Random): Int = if (maxAmount <= minAmount) minAmount else random.nextInt(maxAmount - minAmount + 1) + minAmount
}
