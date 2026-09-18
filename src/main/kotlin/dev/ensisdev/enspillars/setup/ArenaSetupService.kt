package dev.ensisdev.enspillars.setup

import dev.ensisdev.enspillars.EnsPillarsPlugin
import dev.ensisdev.enspillars.api.LocationData
import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

class ArenaSetupService(private val plugin: EnsPillarsPlugin) {
    private val dir get() = File(plugin.dataFolder, plugin.configuration.arenaDirectory)
    private fun safeId(id: String): String? { val s = id.lowercase().replace(Regex("[^a-z0-9_-]"), "-").trim('-'); if (s.isBlank() || s.length > 32 || s == ".." || s.contains("..")) return null; return s }

    fun create(id: String): Boolean {
        val safe = safeId(id) ?: return false
        dir.mkdirs()
        val file = File(dir, "$safe.yml")
        if (file.exists()) return false
        val cfg = YamlConfiguration()
        cfg.set("id", safe)
        cfg.set("name", safe)
        cfg.set("enabled", false)
        cfg.set("settings.min-players", 2)
        cfg.set("settings.max-players", 16)
        cfg.set("settings.starting-countdown", 15)
        cfg.set("settings.pre-game-time", 5)
        cfg.set("settings.duration-seconds", 900)
        cfg.set("settings.item-delay-seconds", 5)
        cfg.set("settings.border-size", 50.0)
        cfg.set("settings.height-min", -64)
        cfg.set("settings.height-max", 320)
        cfg.set("rules.pvp", true)
        cfg.set("rules.allow-build", true)
        cfg.set("rules.allow-break", true)
        cfg.set("rules.natural-regeneration", true)
        cfg.set("rules.keep-inventory", false)
        cfg.set("loot.per-player", true)
        cfg.set("modes.default-game-mode", "NORMAL")
        cfg.set("modes.default-map-mode", "NORMAL")
        cfg.set("voting.enabled", true)
        cfg.set("voting.duration-seconds", 10)
        cfg.set("voting.game-modes", listOf("NORMAL", "SHUFFLE", "SWAP"))
        cfg.set("voting.map-modes", listOf("NORMAL", "LAVA_RISE", "FRAGILE"))
        cfg.set("modifiers.shrinking-border.enabled", false)
        cfg.set("modifiers.shrinking-border.final-size", 8.0)
        cfg.set("modifiers.shrinking-border.duration-seconds", 180)
        cfg.set("locations.spawns", emptyList<String>())
        cfg.save(file)
        return true
    }

    fun setLocation(id: String, key: String, location: Location): Boolean {
        val safe = safeId(id) ?: return false
        if (key !in setOf("lobby", "spectator", "center")) return false
        val world = location.world ?: return false
        val file = File(dir, "$safe.yml")
        if (!file.exists()) return false
        val cfg = YamlConfiguration.loadConfiguration(file)
        cfg.set("locations.$key", listOf(world.name, location.x, location.y, location.z, location.yaw, location.pitch))
        cfg.save(file)
        return true
    }

    fun addSpawn(id: String, location: Location): Int? {
        val safe = safeId(id) ?: return null
        val world = location.world ?: return null
        val file = File(dir, "$safe.yml")
        if (!file.exists()) return null
        val cfg = YamlConfiguration.loadConfiguration(file)
        val list = cfg.getStringList("locations.spawns").toMutableList()
        list += encode(location)
        cfg.set("locations.spawns", list)
        cfg.save(file)
        return list.lastIndex
    }

    fun setEnabled(id: String, enabled: Boolean): Boolean {
        val safe = safeId(id) ?: return false
        val file = File(dir, "$safe.yml")
        if (!file.exists()) return false
        val cfg = YamlConfiguration.loadConfiguration(file)
        cfg.set("enabled", enabled)
        cfg.save(file)
        return true
    }

    /** GUI sayı ayarlayıcıları için genel ayar yazımı (settings.*). */
    fun setSetting(id: String, path: String, value: Number): Boolean {
        if (!path.matches(Regex("[a-z0-9-]+"))) return false
        val safe = safeId(id) ?: return false
        val file = File(dir, "$safe.yml")
        if (!file.exists()) return false
        val cfg = YamlConfiguration.loadConfiguration(file)
        cfg.set("settings.$path", value)
        cfg.save(file)
        return true
    }

    /** Varsayılan mod döngüsü için (modes.default-game-mode / default-map-mode). */
    fun setMode(id: String, key: String, value: String): Boolean {
        if (key != "default-game-mode" && key != "default-map-mode") return false
        val safe = safeId(id) ?: return false
        val file = File(dir, "$safe.yml")
        if (!file.exists()) return false
        val cfg = YamlConfiguration.loadConfiguration(file)
        cfg.set("modes.$key", value.uppercase())
        cfg.save(file)
        return true
    }

    fun clearSpawns(id: String): Boolean {
        val safe = safeId(id) ?: return false
        val file = File(dir, "$safe.yml")
        if (!file.exists()) return false
        val cfg = YamlConfiguration.loadConfiguration(file)
        cfg.set("locations.spawns", emptyList<String>())
        cfg.save(file)
        return true
    }

    fun spawnCount(id: String): Int {
        val safe = safeId(id) ?: return 0
        val file = File(dir, "$safe.yml")
        if (!file.exists()) return 0
        return YamlConfiguration.loadConfiguration(file).getStringList("locations.spawns").size
    }

    fun remove(id: String): Boolean { val safe = safeId(id) ?: return false; return File(dir, "$safe.yml").delete() }

    private fun encode(l: Location) = listOf(l.world?.name ?: "world", l.x, l.y, l.z, l.yaw, l.pitch).joinToString("|")
}
