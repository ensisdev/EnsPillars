package dev.ensisdev.enspillars.game

import dev.ensisdev.enspillars.EnsPillarsPlugin
import dev.ensisdev.enspillars.api.GameState
import dev.ensisdev.enspillars.config.ArenaConfigValidator
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class ArenaManager(private val plugin: EnsPillarsPlugin) {
    private val arenas = ConcurrentHashMap<String, Arena>()
    private val engines = ConcurrentHashMap<String, GameEngine>()
    val size get() = arenas.size
    private fun key(id: String) = id.lowercase(Locale.ROOT)

    fun loadAll() {
        engines.values.forEach { it.shutdown() }; engines.clear(); arenas.clear()
        val dir = File(plugin.dataFolder, plugin.configuration.arenaDirectory); dir.mkdirs()
        dir.listFiles { f -> f.isFile && f.extension.equals("yml", true) }?.forEach { f -> runCatching {
            val cfg = YamlConfiguration.loadConfiguration(f)
            val fatals = ArenaConfigValidator.validate(cfg).filter { it.fatal }
            fatals.forEach { issue -> plugin.logger.warning("Arena ${f.name}: ${issue.path}: ${issue.message}") }
            if (fatals.isNotEmpty()) {
                plugin.logger.warning("Arena ${f.name} atlandı (fatal config hatası).")
                return@forEach
            }
            val id = cfg.getString("id")?.trim().takeUnless { it.isNullOrEmpty() } ?: f.nameWithoutExtension
            val arena = Arena(id, cfg); arenas[key(id)] = arena; if (arena.enabled) engines[key(id)] = GameEngine(plugin, arena)
        }.onFailure { plugin.logger.warning("Arena ${f.name} failed: ${it.message}") } }
    }

    /** Tek arena dosyasını yeniden yükler, diğer aktif oyunlara dokunmaz. */
    fun reloadSingle(id: String): Boolean {
        val file = File(File(plugin.dataFolder, plugin.configuration.arenaDirectory), "${key(id)}.yml")
        if (!file.isFile) return false
        engines[key(id)]?.shutdown()
        engines.remove(key(id)); arenas.remove(key(id))
        return runCatching {
            val cfg = YamlConfiguration.loadConfiguration(file)
            val fatals = ArenaConfigValidator.validate(cfg).filter { it.fatal }
            if (fatals.isNotEmpty()) {
                fatals.forEach { plugin.logger.warning("Arena $id: ${it.path}: ${it.message}") }
                return false
            }
            val arenaId = cfg.getString("id")?.trim().takeUnless { it.isNullOrEmpty() } ?: id
            val arena = Arena(arenaId, cfg)
            arenas[key(arenaId)] = arena
            if (arena.enabled) engines[key(arenaId)] = GameEngine(plugin, arena)
            true
        }.onFailure { plugin.logger.warning("Arena $id reload failed: ${it.message}") }.getOrDefault(false)
    }
    fun all() = arenas.values.sortedBy { it.id.lowercase(Locale.ROOT) }
    fun get(id: String) = arenas[key(id)]
    fun engine(id: String) = engines[key(id)]
    fun engineFor(uuid: java.util.UUID) = engines.values.firstOrNull { it.isInGame(uuid) }
    fun hasActiveGames() = engines.values.any { it.playerCount() > 0 }
    /** Tek arenayı kaldırır, diğer oyunlara dokunmaz. */
    fun removeArena(id: String): Boolean {
        val k = key(id)
        engines.remove(k)?.shutdown()
        return arenas.remove(k) != null
    }
    fun join(player: Player, id: String): Boolean {
        if (engineFor(player.uniqueId) != null) return false
        return engine(id)?.join(player) ?: false
    }
    /** Maç sonu dönüş zinciri: arena lobby → spawn[0] → global setspawn. */
    fun endLocation(arena: Arena): org.bukkit.Location? =
        arena.lobby() ?: arena.spawn(0) ?: plugin.configuration.globalLobby
    /** Rastgele bekleyen arenaya katılır. */
    fun autojoin(player: Player): Boolean {
        if (engineFor(player.uniqueId) != null) return false
        val arena = all().filter { it.state == GameState.WAITING }.randomOrNull() ?: return false
        return join(player, arena.id)
    }
    fun leave(player: Player) { engines.values.firstOrNull { it.isInGame(player.uniqueId) }?.leave(player.uniqueId) }
    fun shutdown() { engines.values.forEach { it.shutdown() }; arenas.values.forEach { it.forceState(GameState.RESETTING) }; engines.clear(); arenas.clear() }

    /** Aynı dünyayı paylaşan aktif arenalar border çakışması yaratır; açılışta uyar. */
    fun warnSharedWorlds() {
        arenas.values.filter { it.enabled }.groupBy { it.center()?.world?.uid }.filterKeys { it != null }
            .filterValues { it.size > 1 }.forEach { (_, list) ->
                plugin.logger.warning("Aynı dünyada birden fazla aktif arena border çakışması yaratır: ${list.joinToString { it.id }}")
            }
    }

    /**
     * Crash/reboot sonrası spawn noktalarında kalmış kafes camlarını temizler.
     * Bilinçli olarak SADECE cam bloklara dokunur: yanlışlıkla oyuncu
     * yapısını silmemek için lava veya diğer bloklar temizlenmez.
     * Main thread'de çağrılmalıdır.
     */
    fun cleanupResidue(): Int {
        var cleaned = 0
        arenas.values.forEach { arena ->
            for (i in 0 until arena.spawnCount()) {
                val spawn = arena.spawn(i) ?: continue
                val world = spawn.world ?: continue
                val base = spawn.block.location
                for (x in -1..1) for (y in 0..3) for (z in -1..1) {
                    if (x != 0 || z != 0 || y == 3) {
                        val block = world.getBlockAt(base.blockX + x, base.blockY + y, base.blockZ + z)
                        if (block.type == Material.GLASS || block.type.name.endsWith("STAINED_GLASS")) {
                            block.type = Material.AIR; cleaned++
                        }
                    }
                }
            }
        }
        if (cleaned > 0) plugin.logger.warning("Temizlenen kafes kalıntısı: $cleaned blok")
        return cleaned
    }
}
