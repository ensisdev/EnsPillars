package dev.ensisdev.enspillars.loot

import dev.ensisdev.enspillars.EnsPillarsPlugin
import org.bukkit.Material
import org.bukkit.entity.Player

class LootService(private val plugin: EnsPillarsPlugin) {
    private var entries: List<LootEntry> = emptyList()

    fun reload() {
        val section = plugin.config.getConfigurationSection("loot.table")
        if (section == null) { plugin.logger.warning("loot.table bulunamadı, loot devre dışı."); entries = emptyList(); return }
        val loaded = mutableListOf<LootEntry>()
        section.getKeys(false).forEach { key ->
            val material = Material.matchMaterial(key)
            if (material == null) { plugin.logger.warning("Geçersiz loot materyali atlandı: $key"); return@forEach }
            val min = section.getInt("$key.min", 1).coerceAtLeast(1)
            val max = section.getInt("$key.max", min).coerceAtLeast(min)
            val weight = section.getDouble("$key.weight", 1.0).coerceAtLeast(0.01)
            loaded += LootEntry(material, min, max, weight)
        }
        entries = loaded
        if (loaded.isEmpty()) plugin.logger.warning("Loot tablosu boş, oyunlarda eşya verilmeyecek.")
    }

    fun giveRandom(player: Player): LootEntry? {
        val (chosen, stack) = roll() ?: return null
        give(player, stack)
        return chosen
    }

    /** Zar atar ama vermez (event ile değiştirme/iptal için). */
    fun roll(): Pair<LootEntry, org.bukkit.inventory.ItemStack>? {
        if (entries.isEmpty()) return null
        val total = entries.sumOf { it.weight }
        var roll = java.util.concurrent.ThreadLocalRandom.current().nextDouble() * total
        var chosen = entries.last()
        for (entry in entries) {
            roll -= entry.weight
            if (roll <= 0.0) { chosen = entry; break }
        }
        val stack = org.bukkit.inventory.ItemStack(chosen.material, chosen.amount(java.util.concurrent.ThreadLocalRandom.current()))
        return chosen to stack
    }

    fun give(player: Player, stack: org.bukkit.inventory.ItemStack) {
        val left = player.inventory.addItem(stack)
        if (left.isNotEmpty()) left.values.forEach { player.world.dropItemNaturally(player.location, it) }
    }
}
