package dev.ensisdev.enspillars.hotbar

import dev.ensisdev.enspillars.EnsPillarsPlugin
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Durum bazlı hotbar eşyaları (lobby/game/spectate/winner).
 * Eşya kullanımı komut çalıştırır; çıkışta envanterden temizlenir.
 */
class HotbarService(private val plugin: EnsPillarsPlugin) {
    data class HotbarItem(val material: Material, val name: String, val lore: List<String>, val slot: Int, val command: String)

    private val given = ConcurrentHashMap<UUID, MutableList<ItemStack>>()
    private var cache = mapOf<String, List<HotbarItem>>()

    init { reload() }

    fun reload() {
        val f = File(plugin.dataFolder, "hotbar.yml")
        if (!f.isFile) plugin.saveResource("hotbar.yml", false)
        val y = runCatching { YamlConfiguration.loadConfiguration(f) }.getOrNull()
        if (y == null) {
            cache = emptyMap()
            return
        }
        cache = listOf("lobby", "game", "spectate", "winner", "setup").associateWith { section ->
            y.getMapList(section).mapNotNull { m ->
                val mat = Material.matchMaterial(m["material"]?.toString() ?: return@mapNotNull null) ?: return@mapNotNull null
                HotbarItem(
                    mat,
                    m["name"]?.toString() ?: mat.name,
                    (m["lore"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
                    (m["slot"] as? Number)?.toInt()?.coerceIn(0, 40) ?: 0,
                    m["command"]?.toString() ?: ""
                )
            }
        }
    }

    fun give(p: Player, section: String) {
        cleanup(p)
        val items = cache[section] ?: return
        val track = mutableListOf<ItemStack>()
        items.forEach { def ->
            val stack = ItemStack(def.material).apply {
                itemMeta = itemMeta?.apply {
                    setDisplayName(color(def.name))
                    if (def.lore.isNotEmpty()) setLore(def.lore.map { color(it) })
                }
            }
            p.inventory.setItem(def.slot, stack)
            track += stack.clone()
        }
        if (track.isNotEmpty()) given[p.uniqueId] = track
    }

    /** Verilen eşyaları envanterden kaldırır. */
    fun cleanup(p: Player) {
        val list = given.remove(p.uniqueId) ?: return
        val inv = p.inventory
        for (i in 0 until inv.size) {
            val cur = inv.getItem(i) ?: continue
            if (list.any { it.isSimilar(cur) }) inv.setItem(i, null)
        }
    }

    /** Hotbar eşyası kullanıldıysa komutu çalıştırır, true döner. */
    fun use(e: PlayerInteractEvent): Boolean {
        val item = e.item ?: return false
        if (item.type.isAir) return false
        val list = given[e.player.uniqueId] ?: return false
        if (list.none { it.isSimilar(item) }) return false
        e.isCancelled = true
        val cmd = sectionCommand(item)?.takeIf { it.isNotBlank() } ?: return true
        runCatching { e.player.performCommand(cmd) }
        return true
    }

    private fun sectionCommand(item: ItemStack): String? {
        cache.values.flatten().firstOrNull {
            it.material == item.type && color(it.name) == (item.itemMeta?.displayName ?: "")
        }?.let { return it.command }
        return null
    }

    private fun color(s: String) = ChatColor.translateAlternateColorCodes('&', s)
}
