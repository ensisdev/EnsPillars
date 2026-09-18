package dev.ensisdev.enspillars.shop

import dev.ensisdev.enspillars.EnsPillarsPlugin
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection

/** shop.yml'deki tek kozmetik tanımı. */
data class ShopEntry(
    val id: String,
    val name: String,
    val material: Material,
    val lore: List<String>,
    val price: Int,
    val permission: String,
    val glow: Boolean,
    val extra: Map<String, String> = emptyMap()
)

/** shop.yml yükleyici: cages / killmessages / deathcries bölümleri. */
class ShopCatalog(private val plugin: EnsPillarsPlugin) {
    var cages: Map<String, ShopEntry> = emptyMap(); private set
    var killMessages: Map<String, ShopEntry> = emptyMap(); private set
    var deathCries: Map<String, ShopEntry> = emptyMap(); private set
    var killTemplates: Map<String, Map<String, String>> = emptyMap(); private set

    init { reload() }

    fun reload() {
        val f = java.io.File(plugin.dataFolder, "shop.yml")
        if (!f.isFile) plugin.saveResource("shop.yml", false)
        val y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f)
        cages = loadSection(y.getConfigurationSection("cages"))
        killMessages = loadSection(y.getConfigurationSection("killmessages"))
        val (entries, templates) = loadKillMessages(y.getConfigurationSection("killmessages"))
        killMessages = entries
        killTemplates = templates
        deathCries = loadSection(y.getConfigurationSection("deathcries"))
    }

    private fun loadSection(s: ConfigurationSection?): Map<String, ShopEntry> {
        if (s == null) return emptyMap()
        return s.getKeys(false).mapNotNull { id ->
            val mat = Material.matchMaterial(s.getString("$id.material", "STONE") ?: "STONE") ?: Material.STONE
            id.lowercase() to ShopEntry(
                id.lowercase(),
                s.getString("$id.name", id) ?: id,
                mat,
                s.getStringList("$id.lore"),
                s.getInt("$id.price", 0).coerceAtLeast(0),
                s.getString("$id.permission", "") ?: "",
                s.getBoolean("$id.glow", false),
                mapOf(
                    "sound" to (s.getString("$id.sound", "") ?: ""),
                    "pitch" to (s.getString("$id.pitch", "1.0") ?: "1.0")
                )
            )
        }.toMap()
    }

    private fun loadKillMessages(s: ConfigurationSection?): Pair<Map<String, ShopEntry>, Map<String, Map<String, String>>> {
        val entries = loadSection(s)
        val templates = mutableMapOf<String, Map<String, String>>()
        s?.getKeys(false)?.forEach { id ->
            val msgs = s.getConfigurationSection("$id.messages")
            if (msgs != null) templates[id.lowercase()] = msgs.getKeys(false).associateWith { k -> msgs.getString(k, "") ?: "" }
        }
        return entries to templates
    }
}
