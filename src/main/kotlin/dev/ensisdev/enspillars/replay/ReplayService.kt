package dev.ensisdev.enspillars.replay

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.ensisdev.enspillars.EnsPillarsPlugin
import dev.ensisdev.enspillars.game.GameEngine
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.Executors
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.math.min
import org.bukkit.configuration.file.YamlConfiguration

/**
 * EnsPillars native .ens replay engine.
 *
 * The format is intentionally server-owned: the browser never needs to understand
 * Minecraft internals. The plugin turns the compact binary stream into a viewer
 * friendly JSON representation over the optional local HTTP service.
 */
class ReplayService(private val plugin: EnsPillarsPlugin) {
    companion object {
        private const val MAGIC = 0x454E5332 // ENS2
        private const val VERSION = 3
        private const val REC_SNAPSHOT: Byte = 1
        private const val REC_EVENT: Byte = 2
        private const val REC_BLOCKS: Byte = 3
        private const val REC_DELTA: Byte = 4
        private const val REC_CHECKPOINT: Byte = 5
        private const val REC_END: Byte = 127
        private const val MASK_POS: Int = 1
        private const val MASK_VITALS: Int = 2
        private const val MASK_STATE: Int = 4
        private const val MASK_INV: Int = 8
        private const val MASK_FX: Int = 16
    }

    private data class LastState(
        val x: Double, val y: Double, val z: Double, val yaw: Float, val pitch: Float,
        val hp: Float, val food: Int, val fire: Int,
        val sneak: Boolean, val sprint: Boolean, val gm: String, val held: Int,
        val invHash: Int, val fxHash: Int
    )

    private data class Active(
        val id: String,
        val arena: String,
        val file: File,
        val out: DataOutputStream,
        val startedAt: Long,
        var tick: Long = 0L,
        var lastKeyTick: Long = -1L,
        var winner: String? = null,
        val names: Map<String, String> = emptyMap(),
        val last: MutableMap<String, LastState> = LinkedHashMap()
    )

    data class ReplayMeta(
        val arena: String, val startedAt: Long, val durationTicks: Long,
        val partial: Boolean, val winner: String?, val players: List<Pair<String, String>>
    )

    private val jsonCache = object : LinkedHashMap<String, Pair<Long, String>>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, String>>?) = size > 5
    }

    private val active = LinkedHashMap<String, Active>()
    private val dir get() = File(plugin.dataFolder, plugin.config.getString("replay.directory", "replays") ?: "replays")
    private val viewerDir get() = File(plugin.dataFolder, "replay-viewer")
    private var http: HttpServer? = null
    private var executor: java.util.concurrent.ExecutorService? = null
    private var tickTask: org.bukkit.scheduler.BukkitTask? = null

    init {
        dir.mkdirs()
        installViewer()
        if (plugin.config.getBoolean("replay.web.enabled", true)) startWeb()
        tickTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable { tick() }, 1L, 1L)
        runCatching { cleanOld() }
        plugin.server.scheduler.runTaskTimer(plugin, Runnable { runCatching { cleanOld() } }, 1728000L, 1728000L)
    }

    fun start(arena: String, players: Collection<UUID>) {
        if (!plugin.config.getBoolean("replay.enabled", true)) return
        stop(arena)
        dir.mkdirs()
        val id = "${arena.lowercase()}-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}"
        val file = File(dir, "$id.ens")
        val raw = DataOutputStream(BufferedOutputStream(file.outputStream(), 64 * 1024))
        val out = DataOutputStream(GZIPOutputStream(raw, 64 * 1024))
        out.writeInt(MAGIC)
        out.writeInt(VERSION)
        out.writeLong(System.currentTimeMillis())
        out.writeUTF(arena)
        out.writeInt(players.size)
        players.forEach { uuid ->
            out.writeUTF(uuid.toString())
            out.writeUTF(Bukkit.getPlayer(uuid)?.name ?: uuid.toString().take(8))
        }
        out.flush()
        val names = LinkedHashMap<String, String>()
        players.forEach { uuid ->
            names[uuid.toString()] = Bukkit.getPlayer(uuid)?.name ?: uuid.toString().take(8)
        }
        val recording = Active(id, arena, file, out, System.currentTimeMillis(), names = names)
        active[arena.lowercase()] = recording
        writeInitialMap(recording)
        event(arena, "MATCH_START", "players=${players.size}")
        plugin.logger.info("Started .ens replay $id")
    }

    fun event(arena: String, type: String, payload: String) {
        val r = active[arena.lowercase()] ?: return
        if (type == "FINISH" && payload != "DRAW") r.winner = payload
        synchronized(r) {
            r.out.writeByte(REC_EVENT.toInt())
            r.out.writeLong(r.tick)
            r.out.writeUTF(type)
            r.out.writeUTF(payload)
        }
    }

    private fun keyframeInterval() = plugin.config.getInt("replay.keyframe-interval", 100).coerceIn(10, 3600)
    private fun checkpointInterval() = plugin.config.getInt("replay.checkpoint-interval", 600).coerceIn(100, 86400)

    private fun tick() {
        if (active.isEmpty()) return
        active.values.toList().forEach { r ->
            val engine = plugin.arenas.engine(r.arena) ?: return@forEach
            r.tick++
            maybeSnapshot(r, engine)
            // Toplu flush: her tick flush TPS düşürür, crash'te en fazla ~1 sn kayıp olur.
            if (r.tick % 20L == 0L) synchronized(r) { runCatching { r.out.flush() } }
            if (r.tick % checkpointInterval() == 0L) checkpoint(r)
        }
    }

    /** Değişmeyen tick'lerde yazmaz; her keyframe'de tam snapshot, arada delta yazar. */
    private fun maybeSnapshot(r: Active, engine: GameEngine) {
        val ids = engine.playerIdsForReplay()
        val cur = ids.mapNotNull { uuid -> Bukkit.getPlayer(uuid)?.let { uuid.toString() to it } }
        if (cur.isEmpty()) return
        val changed = cur.filter { (uuid, p) -> stateOf(p) != r.last[uuid] }
        val needKey = r.lastKeyTick < 0 || r.tick - r.lastKeyTick >= keyframeInterval()
        if (changed.isEmpty() && !needKey) return
        synchronized(r) {
            if (needKey) {
                writeSnapshot(r, engine)
                r.lastKeyTick = r.tick
                cur.forEach { (uuid, p) -> r.last[uuid] = stateOf(p) }
            } else {
                writeDelta(r, changed)
                changed.forEach { (uuid, p) -> r.last[uuid] = stateOf(p) }
            }
        }
    }

    private fun stateOf(p: Player): LastState {
        var invHash = 1
        p.inventory.contents.forEach { item ->
            invHash = 31 * invHash + (item?.let { it.type.name.hashCode() * 31 + it.amount } ?: 0)
        }
        var fxHash = p.activePotionEffects.size
        p.activePotionEffects.forEach { fxHash = 31 * fxHash + (fxHash + it.type.key.key.hashCode() + it.amplifier * 7 + it.duration) }
        return LastState(
            p.location.x, p.location.y, p.location.z, p.location.yaw, p.location.pitch,
            p.health.toFloat(), p.foodLevel, p.fireTicks,
            p.isSneaking, p.isSprinting, p.gameMode.name, p.inventory.heldItemSlot,
            invHash, fxHash
        )
    }

    private fun writeDelta(r: Active, changed: List<Pair<String, Player>>) {
        r.out.writeByte(REC_DELTA.toInt())
        r.out.writeLong(r.tick)
        r.out.writeInt(changed.size)
        changed.forEach { (uuid, p) ->
            val prev = r.last[uuid]
            val cur = stateOf(p)
            var mask = 0
            if (prev == null || prev.x != cur.x || prev.y != cur.y || prev.z != cur.z || prev.yaw != cur.yaw || prev.pitch != cur.pitch) mask = mask or MASK_POS
            if (prev == null || prev.hp != cur.hp || prev.food != cur.food || prev.fire != cur.fire) mask = mask or MASK_VITALS
            if (prev == null || prev.sneak != cur.sneak || prev.sprint != cur.sprint || prev.gm != cur.gm || prev.held != cur.held) mask = mask or MASK_STATE
            if (prev == null || prev.invHash != cur.invHash) mask = mask or MASK_INV
            if (prev == null || prev.fxHash != cur.fxHash) mask = mask or MASK_FX
            if (mask == 0) mask = MASK_POS
            r.out.writeUTF(uuid)
            r.out.writeByte(mask)
            if (mask and MASK_POS != 0) {
                r.out.writeDouble(p.location.x); r.out.writeDouble(p.location.y); r.out.writeDouble(p.location.z)
                r.out.writeFloat(p.location.yaw); r.out.writeFloat(p.location.pitch)
            }
            if (mask and MASK_VITALS != 0) {
                r.out.writeFloat(p.health.toFloat()); r.out.writeInt(p.foodLevel); r.out.writeInt(p.fireTicks)
            }
            if (mask and MASK_STATE != 0) {
                r.out.writeBoolean(p.isSneaking); r.out.writeBoolean(p.isSprinting)
                r.out.writeUTF(p.gameMode.name); r.out.writeInt(p.inventory.heldItemSlot)
            }
            if (mask and MASK_INV != 0) writeInventory(r.out, p)
            if (mask and MASK_FX != 0) {
                r.out.writeInt(p.activePotionEffects.size)
                p.activePotionEffects.forEach { effect ->
                    r.out.writeUTF(effect.type.key.key)
                    r.out.writeInt(effect.amplifier)
                    r.out.writeInt(effect.duration)
                }
            }
        }
    }

    private fun checkpoint(r: Active) {
        synchronized(r) {
            runCatching {
                r.out.writeByte(REC_CHECKPOINT.toInt())
                r.out.writeLong(r.tick)
                r.out.flush()
            }
        }
    }

    private fun writeSnapshot(r: Active, engine: GameEngine) {
        synchronized(r) {
            r.out.writeByte(REC_SNAPSHOT.toInt())
            r.out.writeLong(r.tick)
            val ids = engine.playerIdsForReplay()
            r.out.writeInt(ids.size)
            ids.forEach { uuid ->
                val p = Bukkit.getPlayer(uuid) ?: return@forEach
                r.out.writeUTF(uuid.toString())
                r.out.writeDouble(p.location.x)
                r.out.writeDouble(p.location.y)
                r.out.writeDouble(p.location.z)
                r.out.writeFloat(p.location.yaw)
                r.out.writeFloat(p.location.pitch)
                r.out.writeFloat(p.health.toFloat())
                r.out.writeInt(p.foodLevel)
                r.out.writeInt(p.fireTicks)
                r.out.writeBoolean(p.isSneaking)
                r.out.writeBoolean(p.isSprinting)
                r.out.writeUTF(p.gameMode.name)
                r.out.writeInt(p.inventory.heldItemSlot)
                writeInventory(r.out, p)
                r.out.writeInt(p.activePotionEffects.size)
                p.activePotionEffects.forEach { effect ->
                    r.out.writeUTF(effect.type.key.key)
                    r.out.writeInt(effect.amplifier)
                    r.out.writeInt(effect.duration)
                }
            }
        }
    }

    private fun writeInventory(out: DataOutputStream, p: Player) {
        val contents = p.inventory.contents
        out.writeInt(contents.size)
        contents.forEachIndexed { slot, item ->
            if (item == null || item.type == Material.AIR) {
                out.writeBoolean(false)
            } else {
                out.writeBoolean(true)
                out.writeInt(slot)
                out.writeUTF(item.type.name)
                out.writeInt(item.amount)
                out.writeBoolean(item.hasItemMeta())
                out.writeUTF(item.itemMeta?.displayName ?: "")
            }
        }
    }

    private fun writeInitialMap(r: Active) {
        val arena = plugin.arenas.get(r.arena) ?: return
        val center = arena.center() ?: return
        val world = center.world ?: return
        val radius = plugin.config.getInt("replay.map-radius", (arena.borderSize / 2.0).toInt().coerceAtLeast(8)).coerceAtMost(64)
        val vertical = plugin.config.getInt("replay.map-vertical-range", 24).coerceIn(4, 48)
        val minY = (center.blockY - vertical).coerceAtLeast(world.minHeight)
        val maxY = (center.blockY + vertical).coerceAtMost(world.maxHeight - 1)
        val maxBlocks = plugin.config.getInt("replay.max-initial-blocks", 120_000).coerceIn(1_000, 250_000)
        data class Cell(val dx: Int, val dy: Int, val dz: Int, val mat: String)
        val cells = ArrayList<Cell>()
        // Tarama bütçesi: yüklenmemiş chunk'lar atlanır (chunk üretimi dondurur),
        // toplam tarama 800k bloğu aşarsa durulur (main-thread freeze önleme).
        var scanned = 0
        outer@ for (x in center.blockX - radius..center.blockX + radius) {
            for (z in center.blockZ - radius..center.blockZ + radius) {
                if (!world.isChunkLoaded(x shr 4, z shr 4)) continue
                for (y in minY..maxY) {
                    scanned++
                    if (scanned > 800_000) break@outer
                    val b = world.getBlockAt(x, y, z)
                    if (!b.type.isAir && b.type != Material.VOID_AIR) {
                        cells += Cell(x - center.blockX, y - center.blockY, z - center.blockZ, b.type.name)
                        if (cells.size >= maxBlocks) break@outer
                    }
                }
            }
        }
        synchronized(r) {
            r.out.writeByte(REC_BLOCKS.toInt())
            r.out.writeInt(center.blockX)
            r.out.writeInt(center.blockY)
            r.out.writeInt(center.blockZ)
            r.out.writeUTF(world.name)
            r.out.writeInt(cells.size)
            cells.forEach {
                r.out.writeUTF(it.mat)
                r.out.writeInt(0)
            }
            r.out.writeInt(cells.size)
            cells.forEach {
                r.out.writeShort(it.dx.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()))
                r.out.writeShort(it.dy.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()))
                r.out.writeShort(it.dz.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()))
            }
            r.out.flush()
        }
    }

    fun stop(arena: String): File? {
        val r = active.remove(arena.lowercase()) ?: return latest(arena)
        synchronized(r) {
            runCatching {
                r.out.writeByte(REC_END.toInt())
                r.out.writeLong(r.tick)
                r.out.writeLong(System.currentTimeMillis() - r.startedAt)
                r.out.flush()
                r.out.close()
            }
        }
        writeIndex(r, partial = false)
        plugin.logger.info("Finished .ens replay ${r.id} (${r.file.length()} bytes)")
        return r.file
    }

    private fun indexFile() = File(dir, "index.yml")

    private fun writeIndex(r: Active, partial: Boolean) {
        runCatching {
            val y = YamlConfiguration.loadConfiguration(indexFile())
            y.set("${r.id}.arena", r.arena)
            y.set("${r.id}.started", r.startedAt)
            y.set("${r.id}.duration", r.tick)
            y.set("${r.id}.partial", partial)
            y.set("${r.id}.winner", r.winner ?: "")
            y.set("${r.id}.players", r.names.map { "${it.key}=${it.value}" })
            y.save(indexFile())
        }.onFailure { plugin.logger.warning("Replay index yazılamadı: ${it.message}") }
    }

    private fun readIndex(): Map<String, ReplayMeta> {
        val f = indexFile()
        if (!f.isFile) return emptyMap()
        return runCatching {
            val y = YamlConfiguration.loadConfiguration(f)
            y.getKeys(false).associateWith { id ->
                ReplayMeta(
                    y.getString("$id.arena", "unknown") ?: "unknown",
                    y.getLong("$id.started"),
                    y.getLong("$id.duration"),
                    y.getBoolean("$id.partial"),
                    y.getString("$id.winner")?.takeIf { it.isNotEmpty() },
                    y.getStringList("$id.players").mapNotNull {
                        val i = it.indexOf('=')
                        if (i <= 0) null else it.substring(0, i) to it.substring(i + 1)
                    }
                )
            }
        }.getOrDefault(emptyMap())
    }

    private fun removeIndex(id: String) {
        val f = indexFile()
        if (!f.isFile) return
        runCatching {
            val y = YamlConfiguration.loadConfiguration(f)
            y.set(id, null)
            y.save(f)
        }
    }

    /** Saklama politikası: eski kayıtları ve kota aşımını temizler. */
    fun cleanOld(): Int {
        val days = plugin.config.getInt("replay.retention-days", 30).coerceAtLeast(0)
        val maxMb = plugin.config.getInt("replay.max-total-mb", 1024).coerceAtLeast(0)
        if (days <= 0 && maxMb <= 0) return 0
        var removed = 0
        val cutoff = if (days > 0) System.currentTimeMillis() - days * 86400000L else Long.MAX_VALUE
        list().filter { it.lastModified() < cutoff }.forEach {
            if (deleteFile(it)) removed++
        }
        if (maxMb > 0) {
            val budget = maxMb * 1024L * 1024L
            var total = list().sumOf { it.length() }
            list().sortedBy { it.lastModified() }.forEach {
                if (total <= budget) return@forEach
                total -= it.length()
                if (deleteFile(it)) removed++
            }
        }
        if (removed > 0) plugin.logger.info("Replay saklama temizliği: $removed kayıt silindi")
        return removed
    }

    private fun deleteFile(f: File): Boolean {
        val ok = runCatching { f.delete() }.getOrDefault(false)
        if (ok) {
            removeIndex(f.nameWithoutExtension)
            synchronized(jsonCache) { jsonCache.remove(f.nameWithoutExtension) }
        }
        return ok
    }

    /** ID ile replay siler (.ens + index). */
    fun deleteReplay(id: String): Boolean {
        val f = byId(id) ?: return false
        return deleteFile(f)
    }

    fun latest(arena: String? = null): File? = dir.listFiles { f ->
        f.extension.equals("ens", true) && (arena == null || f.name.startsWith("${arena.lowercase()}-"))
    }?.maxByOrNull { it.lastModified() }

    fun list(): List<File> = dir.listFiles { f -> f.extension.equals("ens", true) }?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun byId(id: String): File? {
        val safe = File(id).name.trim().takeIf { it.isNotEmpty() } ?: return null
        if (safe.contains("..") || safe.contains('/') || safe.contains('\\')) return null
        val name = if (safe.endsWith(".ens", true)) safe else "$safe.ens"
        if (!name.matches(Regex("[A-Za-z0-9_.-]+\\.ens"))) return null
        val f = File(dir, name)
        return f.takeIf { it.isFile }
    }

    fun publicUrl(file: File): String? {
        val base = plugin.config.getString("replay.web.public-url")?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: return null
        val token = authToken()
        val suffix = if (token.isEmpty()) "" else "?token=" + java.net.URLEncoder.encode(token, StandardCharsets.UTF_8)
        return "$base/replay/${file.nameWithoutExtension}$suffix"
    }

    fun openUrl(file: File): String? = publicUrl(file)

    fun downloadUrl(file: File): String? {
        val base = publicUrl(file) ?: return null
        return if (base.contains("?")) "$base&download=1" else "$base?download=1"
    }

    private fun sendDownload(ex: HttpExchange, file: File) {
        if (!file.isFile) {
            sendJson(ex, "{\"error\":\"not_found\"}", 404)
            return
        }
        val bytes = runCatching { file.readBytes() }.getOrNull()
        if (bytes == null) {
            sendJson(ex, "{\"error\":\"server_error\"}", 500)
            return
        }
        ex.responseHeaders.add("Content-Type", "application/octet-stream")
        ex.responseHeaders.add("Content-Disposition", "attachment; filename=\"${file.name}\"")
        ex.responseHeaders.add("Cache-Control", "no-cache")
        ex.sendResponseHeaders(200, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
    }

    private fun startWeb() {
        val host = plugin.config.getString("replay.web.bind", "127.0.0.1") ?: "127.0.0.1"
        val port = plugin.config.getInt("replay.web.port", 8765).coerceIn(1024, 65535)
        val token = plugin.config.getString("replay.web.auth-token")?.trim() ?: ""
        if (token.isEmpty() && host !in setOf("127.0.0.1", "localhost", "::1")) {
            plugin.logger.warning("Replay web ($host:$port) auth-token olmadan dinliyor! replay.web.auth-token ayarlayın ya da bind adresini 127.0.0.1 yapın.")
        }
        runCatching {
            val server = HttpServer.create(InetSocketAddress(host, port), 0)
            server.createContext("/") { ex -> handle(ex) }
            executor = Executors.newCachedThreadPool { r -> Thread(r, "EnsPillars-ReplayWeb").apply { isDaemon = true } }
            server.executor = executor
            server.start()
            http = server
            plugin.logger.info("Replay viewer listening on $host:$port")
        }.onFailure { plugin.logger.warning("Replay web viewer disabled: ${it.message}") }
    }

    private fun authToken(): String = plugin.config.getString("replay.web.auth-token")?.trim() ?: ""

    private fun authorized(ex: HttpExchange): Boolean {
        val token = authToken()
        if (token.isEmpty()) return true
        val q = ex.requestURI.rawQuery ?: return false
        return q.split("&").any { part ->
            part.substringBefore("=") == "token" &&
                URLDecoder.decode(part.substringAfter("=", ""), StandardCharsets.UTF_8) == token
        }
    }

    private fun handle(ex: HttpExchange) {
        try {
            val path = URLDecoder.decode(ex.requestURI.path, StandardCharsets.UTF_8)
            val params = (ex.requestURI.rawQuery ?: "").split("&").mapNotNull {
                val i = it.indexOf('=')
                if (i <= 0) null else it.substring(0, i) to URLDecoder.decode(it.substring(i + 1), StandardCharsets.UTF_8)
            }.toMap()
            if ((path.startsWith("/api/") || path.startsWith("/replay/")) && !authorized(ex)) {
                sendJson(ex, "{\"error\":\"unauthorized\"}", 401); return
            }
            when {
                path == "/" || path == "/index.html" -> sendFile(ex, File(viewerDir, "index.html"), "text/html; charset=utf-8")
                path == "/app.js" -> sendFile(ex, File(viewerDir, "app.js"), "application/javascript; charset=utf-8")
                path == "/style.css" -> sendFile(ex, File(viewerDir, "style.css"), "text/css; charset=utf-8")
                path.startsWith("/vendor/") -> {
                    val rel = path.removePrefix("/vendor/")
                    if (rel.contains("..") || rel.contains("\\")) sendJson(ex, "{\"error\":\"not_found\"}", 404)
                    else {
                        val f = File(viewerDir, "vendor/$rel")
                        val ct = if (rel.endsWith(".css")) "text/css; charset=utf-8" else "application/javascript; charset=utf-8"
                        sendFile(ex, f, ct)
                    }
                }
                path == "/api/replays" -> sendJson(ex, listJson())
                path.startsWith("/api/replay/") -> {
                    val id = path.removePrefix("/api/replay/").substringBefore('?').substringBefore('#')
                    val file = byId(id)
                    if (file == null) {
                        sendJson(ex, "{\"error\":\"not_found\"}", 404)
                    } else if (params["summary"] == "1") {
                        sendJson(ex, replaySummary(file))
                    } else if (params["download"] == "1") {
                        sendDownload(ex, file)
                    } else {
                        val from = params["from"]?.toLongOrNull()?.coerceAtLeast(0) ?: 0L
                        val to = params["to"]?.toLongOrNull() ?: Long.MAX_VALUE
                        sendJson(ex, replayJson(file, from, to))
                    }
                }
                path.startsWith("/replay/") -> {
                    val id = path.removePrefix("/replay/").substringBefore('?').substringBefore('#')
                    val file = byId(id)
                    if (file != null) {
                        val html = File(viewerDir, "index.html").readText().replace("__REPLAY_ID__", escapeJs(file.nameWithoutExtension))
                        sendBytes(ex, html.toByteArray(StandardCharsets.UTF_8), "text/html; charset=utf-8")
                    } else sendJson(ex, "{\"error\":\"not_found\"}", 404)
                }
                else -> sendJson(ex, "{\"error\":\"not_found\"}", 404)
            }
        } catch (t: Throwable) {
            runCatching { sendJson(ex, "{\"error\":\"server_error\",\"message\":${json(t.message ?: "unknown")} }", 500) }
        } finally { ex.close() }
    }

    private fun listJson(): String = buildString {
        val index = readIndex()
        append('[')
        list().forEachIndexed { i, f ->
            if (i > 0) append(',')
            val m = index[f.nameWithoutExtension]
            append("{\"id\":${json(f.nameWithoutExtension)},\"size\":${f.length()},\"updated\":${f.lastModified()},\"url\":${json(publicUrl(f) ?: "")}")
            if (m != null) append(",\"arena\":${json(m.arena)},\"durationTicks\":${m.durationTicks},\"partial\":${m.partial},\"winner\":${json(m.winner ?: "")},\"players\":${m.players.size}")
            append('}')
        }
        append(']')
    }

    private data class ParsedReplay(
        val arena: String, val startedAt: Long, val players: List<Pair<String, String>>,
        val frames: String, val blocks: String, val durationTicks: Long, val partial: Boolean
    )

    private data class FramePlayer(
        var x: Double = 0.0, var y: Double = 0.0, var z: Double = 0.0,
        var yaw: Float = 0f, var pitch: Float = 0f, var hp: Float = 20f,
        var food: Int = 20, var fire: Int = 0, var sneak: Boolean = false,
        var sprint: Boolean = false, var gm: String = "SURVIVAL", var held: Int = 0,
        var inv: String = "", var fx: String = ""
    )

    private fun replayJson(file: File, from: Long = 0L, to: Long = Long.MAX_VALUE): String {
        if (from == 0L && to == Long.MAX_VALUE) {
            val key = file.nameWithoutExtension
            val stamp = file.lastModified() * 4096 + file.length()
            synchronized(jsonCache) {
                jsonCache[key]?.let { (s, j) -> if (s == stamp) return j }
            }
            val j = buildReplayJson(file, parse(file, 0, Long.MAX_VALUE))
            synchronized(jsonCache) { jsonCache[key] = stamp to j }
            return j
        }
        return buildReplayJson(file, parse(file, from, to))
    }

    private fun buildReplayJson(file: File, parsed: ParsedReplay): String {
        return "{\"id\":${json(file.nameWithoutExtension)},\"arena\":${json(parsed.arena)},\"startedAt\":${parsed.startedAt},\"durationTicks\":${parsed.durationTicks},\"partial\":${parsed.partial},\"players\":[${parsed.players.joinToString(",") { "{\"uuid\":${json(it.first)},\"name\":${json(it.second)}}" }}],\"blocks\":${parsed.blocks},\"frames\":[${parsed.frames}]}"
    }

    private fun replaySummary(file: File): String {
        val id = file.nameWithoutExtension
        readIndex()[id]?.let { m ->
            return "{\"id\":${json(id)},\"arena\":${json(m.arena)},\"startedAt\":${m.startedAt},\"durationTicks\":${m.durationTicks},\"partial\":${m.partial},\"winner\":${json(m.winner ?: "")},\"size\":${file.length()},\"players\":[${m.players.joinToString(",") { "{\"uuid\":${json(it.first)},\"name\":${json(it.second)}}" }}]}"
        }
        return runCatching {
            val parsed = parse(file, 0, -1)
            "{\"id\":${json(id)},\"arena\":${json(parsed.arena)},\"startedAt\":${parsed.startedAt},\"durationTicks\":${parsed.durationTicks},\"partial\":${parsed.partial},\"winner\":\"\",\"size\":${file.length()},\"players\":[${parsed.players.joinToString(",") { "{\"uuid\":${json(it.first)},\"name\":${json(it.second)}}" }}]}"
        }.getOrDefault("{\"id\":${json(id)},\"error\":\"unreadable\"}")
    }

    private fun parse(file: File, from: Long = 0L, to: Long = Long.MAX_VALUE): ParsedReplay {
        var arena = "unknown"
        var started = 0L
        val players = mutableListOf<Pair<String, String>>()
        val frameJson = StringBuilder()
        var blocksJson = "{\"world\":\"\",\"center\":[0,0,0],\"items\":[]}"
        var duration = 0L
        var partial = false
        var firstFrame = true
        fun emit(tick: Long, cur: Map<String, FramePlayer>) {
            if (tick < from || tick > to) return
            if (!firstFrame) frameJson.append(',') else firstFrame = false
            frameJson.append("{\"t\":$tick,\"players\":[")
            cur.entries.sortedBy { it.key }.forEachIndexed { idx, (uuid, f) ->
                if (idx > 0) frameJson.append(',')
                frameJson.append("{\"id\":${json(uuid)},\"x\":${f.x},\"y\":${f.y},\"z\":${f.z},\"yaw\":${f.yaw},\"pitch\":${f.pitch},\"hp\":${f.hp},\"food\":${f.food},\"fire\":${f.fire},\"sneak\":${f.sneak},\"sprint\":${f.sprint},\"gm\":${json(f.gm)},\"held\":${f.held},\"inv\":[${f.inv}],\"effects\":[${f.fx}]}")
            }
            frameJson.append("]}")
        }
        DataInputStream(BufferedInputStream(GZIPInputStream(file.inputStream()), 64 * 1024)).use { input ->
            require(input.readInt() == MAGIC) { "Invalid ENS replay" }
            val version = input.readInt()
            require(version == 2 || version == VERSION) { "Unsupported ENS replay" }
            started = input.readLong()
            arena = input.readUTF()
            repeat(input.readInt()) { players += input.readUTF() to input.readUTF() }
            val cur = LinkedHashMap<String, FramePlayer>()
            try {
                while (true) {
                    when (input.readByte()) {
                        REC_SNAPSHOT -> {
                            val tick = input.readLong()
                            duration = maxOf(duration, tick)
                            if (version == 2) {
                                if (!firstFrame) frameJson.append(',') else firstFrame = false
                                frameJson.append("{\"t\":$tick,\"players\":[")
                                repeat(input.readInt()) { idx ->
                                    if (idx > 0) frameJson.append(',')
                                    val (uuid, f) = readFullPlayer(input)
                                    frameJson.append("{\"id\":${json(uuid)},\"x\":${f.x},\"y\":${f.y},\"z\":${f.z},\"yaw\":${f.yaw},\"pitch\":${f.pitch},\"hp\":${f.hp},\"food\":${f.food},\"fire\":${f.fire},\"sneak\":${f.sneak},\"sprint\":${f.sprint},\"gm\":${json(f.gm)},\"held\":${f.held},\"inv\":[${f.inv}],\"effects\":[${f.fx}]}")
                                }
                                frameJson.append("]}")
                            } else {
                                cur.clear()
                                repeat(input.readInt()) {
                                    val (uuid, f) = readFullPlayer(input)
                                    cur[uuid] = f
                                }
                                emit(tick, cur)
                            }
                        }
                        REC_DELTA -> {
                            val tick = input.readLong()
                            duration = maxOf(duration, tick)
                            repeat(input.readInt()) {
                                val uuid = input.readUTF()
                                val mask = input.readByte().toInt()
                                val f = cur.getOrPut(uuid) { FramePlayer() }
                                if (mask and MASK_POS != 0) {
                                    f.x = input.readDouble(); f.y = input.readDouble(); f.z = input.readDouble()
                                    f.yaw = input.readFloat(); f.pitch = input.readFloat()
                                }
                                if (mask and MASK_VITALS != 0) {
                                    f.hp = input.readFloat(); f.food = input.readInt(); f.fire = input.readInt()
                                }
                                if (mask and MASK_STATE != 0) {
                                    f.sneak = input.readBoolean(); f.sprint = input.readBoolean()
                                    f.gm = input.readUTF(); f.held = input.readInt()
                                }
                                if (mask and MASK_INV != 0) f.inv = readInv(input)
                                if (mask and MASK_FX != 0) f.fx = readFx(input)
                            }
                            emit(tick, cur)
                        }
                        REC_EVENT -> {
                            val tick = input.readLong(); val event = input.readUTF(); val payload = input.readUTF()
                            duration = maxOf(duration, tick)
                            if (tick < from || tick > to) {
                                // Aralık dışı: yazma ama taramaya devam et.
                            } else {
                                if (!firstFrame) frameJson.append(',') else firstFrame = false
                                frameJson.append("{\"t\":$tick,\"event\":${json(event)},\"payload\":${json(payload)}}")
                            }
                        }
                        REC_BLOCKS -> {
                            val cx = input.readInt(); val cy = input.readInt(); val cz = input.readInt(); val world = input.readUTF(); val count = input.readInt()
                            val mats = ArrayList<Pair<String, Int>>(count)
                            repeat(count) { mats += input.readUTF() to input.readInt() }
                            val exact = input.readInt()
                            val arr = StringBuilder()
                            repeat(exact) { i ->
                                val dx = input.readShort().toInt(); val dy = input.readShort().toInt(); val dz = input.readShort().toInt()
                                if (i > 0) arr.append(',')
                                val mat = mats.getOrNull(i)?.first ?: "STONE"
                                arr.append("[$dx,$dy,$dz,${json(mat)}]")
                            }
                            blocksJson = "{\"world\":${json(world)},\"center\":[$cx,$cy,$cz],\"items\":[$arr]}"
                        }
                        REC_CHECKPOINT -> {
                            duration = maxOf(duration, input.readLong())
                        }
                        REC_END -> { duration = maxOf(duration, input.readLong()); input.readLong(); break }
                        else -> break
                    }
                }
            } catch (e: java.io.EOFException) {
                partial = true
            } catch (e: java.io.IOException) {
                partial = true
            }
        }
        return ParsedReplay(arena, started, players, frameJson.toString(), blocksJson, duration, partial)
    }

    private fun readFullPlayer(input: DataInputStream): Pair<String, FramePlayer> {
        val uuid = input.readUTF()
        val f = FramePlayer()
        f.x = input.readDouble(); f.y = input.readDouble(); f.z = input.readDouble()
        f.yaw = input.readFloat(); f.pitch = input.readFloat(); f.hp = input.readFloat(); f.food = input.readInt(); f.fire = input.readInt()
        f.sneak = input.readBoolean(); f.sprint = input.readBoolean(); f.gm = input.readUTF(); f.held = input.readInt()
        f.inv = readInv(input)
        f.fx = readFx(input)
        return uuid to f
    }

    private fun readInv(input: DataInputStream): String {
        val inv = StringBuilder()
        var invFirst = true
        repeat(input.readInt()) {
            if (input.readBoolean()) {
                val actualSlot = input.readInt(); val mat = input.readUTF(); val amount = input.readInt(); val hasMeta = input.readBoolean(); val display = input.readUTF()
                if (!invFirst) inv.append(',') else invFirst = false
                inv.append("{\"slot\":$actualSlot,\"mat\":${json(mat)},\"amount\":$amount,\"name\":${json(if (hasMeta) display else "")}}")
            }
        }
        return inv.toString()
    }

    private fun readFx(input: DataInputStream): String {
        val fx = StringBuilder()
        repeat(input.readInt()) { e ->
            if (e > 0) fx.append(',')
            fx.append("{\"type\":${json(input.readUTF())},\"amp\":${input.readInt()},\"duration\":${input.readInt()}}")
        }
        return fx.toString()
    }

    private fun installViewer() {
        viewerDir.mkdirs()
        val files = listOf("index.html", "app.js", "style.css", "vendor/three.module.js", "vendor/addons/OrbitControls.js")
        files.forEach { name -> if (!File(viewerDir, name).exists()) plugin.saveResource("replay-viewer/$name", false) }
    }

    private fun sendFile(ex: HttpExchange, file: File, contentType: String) { if (!file.exists()) { sendJson(ex, "{\"error\":\"not_found\"}", 404); return }; sendBytes(ex, file.readBytes(), contentType) }
    private fun sendJson(ex: HttpExchange, value: String, status: Int = 200) = sendBytes(ex, value.toByteArray(StandardCharsets.UTF_8), "application/json; charset=utf-8", status)
    private fun sendBytes(ex: HttpExchange, bytes: ByteArray, contentType: String, status: Int = 200) { ex.responseHeaders.add("Content-Type", contentType); ex.responseHeaders.add("Cache-Control", "no-cache"); ex.sendResponseHeaders(status, bytes.size.toLong()); ex.responseBody.use { it.write(bytes) } }
    private fun json(v: String): String = "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""
    private fun escapeJs(v: String) = v.replace("\\", "\\\\").replace("'", "\\'")

    fun shutdown() {
        tickTask?.cancel(); tickTask = null
        active.keys.toList().forEach(::stop)
        http?.stop(0); http = null
        executor?.shutdownNow(); executor = null
    }
}
