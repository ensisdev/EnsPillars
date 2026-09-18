package dev.ensisdev.enspillars.snapshot

import dev.ensisdev.enspillars.EnsPillarsPlugin
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Oyuncu snapshot'ları: bellekte + disk yedeğiyle (snapshots.yml).
 * Disk yedeği, crash/reboot sonrası rejoin'de eşyaların geri yüklenmesini sağlar.
 * Geri yükleme her zaman önce mevcut durumu temizler: crash'te player.dat
 * oyun öncesi haliyle kalmışsa bile çift eşya oluşmaz.
 */
class SnapshotStore(private val plugin: EnsPillarsPlugin) {
    private val memory = ConcurrentHashMap<UUID, PlayerSnapshot>()
    private val file get() = File(plugin.dataFolder, "snapshots.yml")

    fun save(p: Player) {
        val snap = runCatching { PlayerSnapshot.capture(p) }.onFailure {
            plugin.logger.warning("Snapshot alınamadı ${p.name}: ${it.message}")
        }.getOrNull() ?: return
        memory[p.uniqueId] = snap
        runCatching { writeDisk(p.uniqueId, snap) }.onFailure {
            plugin.logger.warning("Snapshot diske yazılamadı ${p.name}: ${it.message}")
        }
    }

    /** Varsa geri yükler ve kaydı siler; kayıt yoksa false döner (hiçbir şeye dokunmaz). */
    fun restore(p: Player): Boolean {
        val snap = memory.remove(p.uniqueId) ?: readDisk(p.uniqueId) ?: return false
        deleteDisk(p.uniqueId)
        return runCatching {
            p.inventory.clear()
            p.inventory.armorContents = arrayOfNulls(4)
            p.inventory.setItemInOffHand(null)
            snap.restore(p)
            true
        }.onFailure { plugin.logger.warning("Snapshot geri yüklenemedi ${p.name}: ${it.message}") }.getOrDefault(false)
    }

    private fun writeDisk(u: UUID, snap: PlayerSnapshot) {
        val y = YamlConfiguration.loadConfiguration(file)
        y.set("${u}.at", System.currentTimeMillis())
        y.set("${u}.data", encode(snap))
        y.save(file)
    }

    private fun readDisk(u: UUID): PlayerSnapshot? {
        val f = file
        if (!f.isFile) return null
        val data = YamlConfiguration.loadConfiguration(f).getString("$u.data") ?: return null
        return runCatching { decode(data) }.onFailure {
            plugin.logger.warning("Bozuk snapshot kaydı silindi: $u")
            deleteDisk(u)
        }.getOrNull()
    }

    private fun deleteDisk(u: UUID) {
        val f = file
        if (!f.isFile) return
        runCatching {
            val y = YamlConfiguration.loadConfiguration(f)
            y.set(u.toString(), null)
            y.save(f)
        }
    }

    private fun encode(s: PlayerSnapshot): String {
        val bytes = ByteArrayOutputStream().use { bos ->
            BukkitObjectOutputStream(bos).use { out ->
                out.writeObject(s.contents)
                out.writeObject(s.armor)
                out.writeObject(s.offHand)
                out.writeInt(s.effects.size)
                s.effects.forEach { e ->
                    out.writeUTF(e.name); out.writeInt(e.amplifier); out.writeInt(e.duration)
                    out.writeBoolean(e.ambient); out.writeBoolean(e.particles); out.writeBoolean(e.icon)
                }
                out.writeInt(s.level); out.writeFloat(s.exp); out.writeInt(s.totalExp)
                out.writeDouble(s.health); out.writeInt(s.food); out.writeFloat(s.saturation)
                out.writeUTF(s.gameMode.name)
                out.writeBoolean(s.allowFlight); out.writeBoolean(s.flying); out.writeFloat(s.flySpeed)
                out.writeInt(s.fireTicks)
                val loc = s.location
                out.writeBoolean(loc != null && loc.world != null)
                if (loc != null && loc.world != null) {
                    out.writeUTF(loc.world.name); out.writeDouble(loc.x); out.writeDouble(loc.y); out.writeDouble(loc.z)
                    out.writeFloat(loc.yaw); out.writeFloat(loc.pitch)
                }
            }
            bos.toByteArray()
        }
        return Base64.getEncoder().encodeToString(bytes)
    }

    private fun decode(data: String): PlayerSnapshot {
        val bytes = Base64.getDecoder().decode(data)
        ByteArrayInputStream(bytes).use { bis ->
            BukkitObjectInputStream(bis).use { inp ->
                @Suppress("UNCHECKED_CAST")
                val contents = inp.readObject() as Array<ItemStack?>
                @Suppress("UNCHECKED_CAST")
                val armor = inp.readObject() as Array<ItemStack?>
                val offHand = inp.readObject() as ItemStack?
                val effectCount = inp.readInt()
                val effects = ArrayList<PlayerSnapshot.EffectData>(effectCount)
                repeat(effectCount) {
                    effects += PlayerSnapshot.EffectData(inp.readUTF(), inp.readInt(), inp.readInt(), inp.readBoolean(), inp.readBoolean(), inp.readBoolean())
                }
                val level = inp.readInt(); val exp = inp.readFloat(); val totalExp = inp.readInt()
                val health = inp.readDouble(); val food = inp.readInt(); val saturation = inp.readFloat()
                val gameMode = runCatching { org.bukkit.GameMode.valueOf(inp.readUTF()) }.getOrDefault(org.bukkit.GameMode.SURVIVAL)
                val allowFlight = inp.readBoolean(); val flying = inp.readBoolean(); val flySpeed = inp.readFloat()
                val fireTicks = inp.readInt()
                val hasLoc = inp.readBoolean()
                val loc = if (hasLoc) {
                    val world = plugin.server.getWorld(inp.readUTF())
                    val x = inp.readDouble(); val y = inp.readDouble(); val z = inp.readDouble()
                    val yaw = inp.readFloat(); val pitch = inp.readFloat()
                    world?.let { org.bukkit.Location(it, x, y, z, yaw, pitch) }
                } else null
                return PlayerSnapshot(contents, armor, offHand, effects, level, exp, totalExp, health, food, saturation, gameMode, allowFlight, flying, flySpeed, fireTicks, loc)
            }
        }
    }
}
