package dev.ensisdev.enspillars.config

import org.bukkit.configuration.file.FileConfiguration

data class ValidationIssue(val path: String, val message: String, val fatal: Boolean)

object ArenaConfigValidator {
    fun validate(config: FileConfiguration): List<ValidationIssue> = buildList {
        val id = config.getString("id")
        if (id.isNullOrBlank()) add(ValidationIssue("id", "Arena id is missing.", true))
        val min = config.getInt("settings.min-players", -1)
        val max = config.getInt("settings.max-players", -1)
        if (min < 1) add(ValidationIssue("settings.min-players", "Must be >= 1.", true))
        if (max < min.coerceAtLeast(1)) add(ValidationIssue("settings.max-players", "Must be >= min-players.", true))
        val minY = config.getInt("settings.height-min", -64)
        val maxY = config.getInt("settings.height-max", 320)
        if (minY >= maxY) add(ValidationIssue("settings.height-*", "height-min must be below height-max.", true))
        if (config.getDouble("settings.border-size", 50.0) <= 0.0) add(ValidationIssue("settings.border-size", "Must be > 0.", true))
        if (config.getInt("settings.duration-seconds", 900) <= 0) add(ValidationIssue("settings.duration-seconds", "Must be > 0.", true))
        if (config.getInt("settings.starting-countdown", 15) < 0) add(ValidationIssue("settings.starting-countdown", "Must be >= 0.", true))
        if (config.getInt("settings.item-delay-seconds", 5) <= 0) add(ValidationIssue("settings.item-delay-seconds", "Must be > 0.", true))
        if (config.getInt("settings.pre-game-time", 5) < 0) add(ValidationIssue("settings.pre-game-time", "Must be >= 0.", true))
        if (!config.contains("locations.lobby")) add(ValidationIssue("locations.lobby", "Lobby konumu eksik.", false))
        if (!config.contains("locations.spectator")) add(ValidationIssue("locations.spectator", "Spectator konumu eksik.", false))
        if (!config.contains("locations.center")) add(ValidationIssue("locations.center", "Center konumu eksik.", true))
        if (config.getStringList("locations.spawns").isEmpty()) add(ValidationIssue("locations.spawns", "En az bir spawn gerekli, oyun başlayamaz.", true))
        val shrink = config.getBoolean("modifiers.shrinking-border.enabled", false)
        if (shrink && config.getInt("modifiers.shrinking-border.duration-seconds", 180) <= 0) add(ValidationIssue("modifiers.shrinking-border.duration-seconds", "Must be > 0.", true))
    }
}
