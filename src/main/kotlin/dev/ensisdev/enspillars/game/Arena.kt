package dev.ensisdev.enspillars.game

import dev.ensisdev.enspillars.api.*
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.configuration.file.FileConfiguration
import java.util.UUID

class Arena(val id: String, private val config: FileConfiguration) {
    val displayName = config.getString("name", id) ?: id
    val enabled = config.getBoolean("enabled", false)
    val minPlayers = config.getInt("settings.min-players", 2).coerceIn(1, 100)
    val maxPlayers = config.getInt("settings.max-players", 16).coerceIn(minPlayers, 100)
    val startingCountdown = config.getInt("settings.starting-countdown", 15).coerceIn(0, 3600)
    val preGameTime = config.getInt("settings.pre-game-time", 5).coerceIn(0, 3600)
    val durationSeconds = config.getInt("settings.duration-seconds", 900).coerceIn(1, 86400)
    val itemDelaySeconds = config.getInt("settings.item-delay-seconds", 5).coerceIn(1, 3600)
    val borderSize = config.getDouble("settings.border-size", 50.0).coerceIn(1.0, 10000.0)
    val borderShrinkEnabled = config.getBoolean("modifiers.shrinking-border.enabled", false)
    val borderShrinkFinal = config.getDouble("modifiers.shrinking-border.final-size", 8.0).coerceAtLeast(1.0)
    val borderShrinkSeconds = config.getInt("modifiers.shrinking-border.duration-seconds", 180).coerceAtLeast(1)
    val heightMin = config.getInt("settings.height-min", -64)
    val heightMax = config.getInt("settings.height-max", 320)
    val allowBuild = config.getBoolean("rules.allow-build", true)
    val allowBreak = config.getBoolean("rules.allow-break", true)
    val pvp = config.getBoolean("rules.pvp", true)
    val naturalRegeneration = config.getBoolean("rules.natural-regeneration", true)
    val keepInventory = config.getBoolean("rules.keep-inventory", false)
    val lootPerPlayer = config.getBoolean("loot.per-player", true)
    val defaultGameMode = config.getString("modes.default-game-mode", "NORMAL") ?: "NORMAL"
    val defaultMapMode = config.getString("modes.default-map-mode", "NORMAL") ?: "NORMAL"
    val voteEnabled = config.getBoolean("voting.enabled", true)
    val voteTime = config.getInt("voting.duration-seconds", 10).coerceAtLeast(1)
    val enabledModes = config.getStringList("voting.game-modes").ifEmpty { listOf("NORMAL", "SHUFFLE", "SWAP") }
    val enabledMapModes = config.getStringList("voting.map-modes").ifEmpty { listOf("NORMAL", "LAVA_RISE", "FRAGILE") }
    private val players = linkedSetOf<UUID>()
    var state = if (enabled) GameState.WAITING else GameState.DISABLED; private set
    fun addPlayer(uuid: UUID) = if (enabled && state == GameState.WAITING && players.size < maxPlayers) players.add(uuid) else false
    fun removePlayer(uuid: UUID) { players.remove(uuid) }
    fun playerCount() = players.size
    fun playerIds(): Set<UUID> = players.toSet()
    fun transition(to: GameState) { require(valid(state, to)) { "Invalid arena transition: $state -> $to" }; state = to }
    fun tryTransition(to: GameState): Boolean { if (!valid(state, to)) return false; state = to; return true }
    fun forceState(to: GameState) { state = to }
    fun snapshot() = ArenaSnapshot(id, displayName, state, players.size, maxPlayers, enabled)
    fun lobby(): Location? = location("locations.lobby")
    fun spectator(): Location? = location("locations.spectator")
    fun center(): Location? = location("locations.center")
    fun spawn(index: Int): Location? { val list=config.getStringList("locations.spawns"); if(index !in list.indices) return null; return parseEncoded(list[index]) ?: parsePath("locations.spawns.$index") }
    fun spawnCount() = config.getStringList("locations.spawns").size
    private fun location(path:String):Location? { val value=config.get(path); if(value is List<*>) { val parts=value.mapNotNull{it?.toString()}; if(parts.size>=4)return parseParts(parts) }; return parsePath(path) }
    private fun parsePath(path:String):Location? { val wn=config.getString("$path.world")?:return null; val w=Bukkit.getWorld(wn)?:return null; return Location(w,config.getDouble("$path.x"),config.getDouble("$path.y"),config.getDouble("$path.z"),config.getDouble("$path.yaw",0.0).toFloat(),config.getDouble("$path.pitch",0.0).toFloat()) }
    private fun parseEncoded(value:String):Location?=parseParts(value.split("|"))
    private fun parseParts(parts:List<String>):Location? { if(parts.size<4)return null; val w=Bukkit.getWorld(parts[0])?:return null; return runCatching{Location(w,parts[1].toDouble(),parts[2].toDouble(),parts[3].toDouble(),parts.getOrNull(4)?.toFloat()?:0f,parts.getOrNull(5)?.toFloat()?:0f)}.getOrNull() }
    private fun valid(a:GameState,b:GameState)=when(a){GameState.DISABLED->b==GameState.WAITING;GameState.WAITING->b==GameState.STARTING||b==GameState.DISABLED;GameState.STARTING->b==GameState.PRE_GAME||b==GameState.WAITING;GameState.PRE_GAME->b==GameState.CAGED||b==GameState.WAITING;GameState.CAGED->b==GameState.ACTIVE||b==GameState.WAITING;GameState.ACTIVE->b==GameState.FINAL||b==GameState.ENDING;GameState.FINAL->b==GameState.ENDING;GameState.ENDING->b==GameState.RESETTING;GameState.RESETTING->b==GameState.WAITING||b==GameState.DISABLED}
}
