package dev.ensisdev.enspillars.setup

import dev.ensisdev.enspillars.EnsPillarsPlugin
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Komutsuz arena kurulum modu: envanter yedeklenir, hotbar'a kurulum
 * aletleri verilir, spawn/konum yakalama blok tıklamasıyla yapılır.
 */
class SetupMode(private val plugin: EnsPillarsPlugin) {
    sealed interface Capture {
        data object Idle : Capture
        data object Spawns : Capture
        data class Single(val key: String) : Capture
    }

    private data class Session(val arenaId: String, val entry: Location, var capture: Capture = Capture.Idle)

    private val sessions = ConcurrentHashMap<UUID, Session>()

    fun inSetup(u: UUID) = sessions.containsKey(u)
    fun arenaOf(u: UUID) = sessions[u]?.arenaId

    /** Kurulum moduna girer (envanter yedeklenir, aletler verilir). */
    fun enter(p: Player, arenaId: String) {
        if (sessions.containsKey(p.uniqueId)) exit(p, teleportBack = false)
        plugin.snapshots.save(p)
        sessions[p.uniqueId] = Session(arenaId, p.location.clone())
        p.inventory.clear()
        plugin.hotbar.give(p, "setup")
        plugin.messages.send(p, "setup.enter", mapOf("arena" to arenaId))
        dev.ensisdev.enspillars.gui.ArenaSetupMenu(plugin, arenaId).open(p)
    }

    /** Kurulum modundan çıkar (envanter iade, geri ışınlama). */
    fun exit(p: Player, teleportBack: Boolean = true) {
        val s = sessions.remove(p.uniqueId) ?: return
        plugin.hotbar.cleanup(p)
        plugin.snapshots.restore(p)
        if (teleportBack && s.entry.world != null) runCatching { p.teleport(s.entry) }
        plugin.messages.send(p, "setup.exit")
    }

    fun handleQuit(p: Player) {
        if (sessions.remove(p.uniqueId) != null) {
            plugin.hotbar.cleanup(p)
            plugin.snapshots.restore(p)
        }
    }

    /** Kurulum aletleri + blok yakalama. True dönerse event tüketilmiştir. */
    fun handleInteract(e: PlayerInteractEvent): Boolean {
        val s = sessions[e.player.uniqueId] ?: return false
        e.isCancelled = true
        val p = e.player
        val held = e.item?.type

        // Blok yakalama (sol tık).
        if (e.action == Action.LEFT_CLICK_BLOCK && e.clickedBlock != null) {
            captureBlock(p, s, e.clickedBlock!!.location)
            return true
        }
        // Sağ tık: spawn yakalamada erken bitir, tekli yakalamada iptal.
        if (e.action == Action.RIGHT_CLICK_AIR || e.action == Action.RIGHT_CLICK_BLOCK) {
            when (s.capture) {
                is Capture.Spawns -> finishSpawns(p, s, early = true)
                is Capture.Single -> {
                    s.capture = Capture.Idle
                    plugin.messages.send(p, "setup.capture-cancelled")
                    dev.ensisdev.enspillars.gui.ArenaSetupMenu(plugin, s.arenaId).open(p)
                }
                Capture.Idle -> toolUse(p, s, held)
            }
            return true
        }
        toolUse(p, s, held)
        return true
    }

    private fun toolUse(p: Player, s: Session, held: Material?) {
        when (held) {
            Material.COMPASS -> dev.ensisdev.enspillars.gui.ArenaSetupMenu(plugin, s.arenaId).open(p)
            Material.DIAMOND -> startSpawns(p, s)
            Material.ENDER_EYE -> dev.ensisdev.enspillars.gui.CageViewMenu(plugin, s.arenaId).open(p)
            Material.BARRIER -> exit(p)
            else -> Unit
        }
    }

    private fun startSpawns(p: Player, s: Session) {
        p.closeInventory()
        s.capture = Capture.Spawns
        showSpawnTitle(p, s)
    }

    /** Dışarıdan (yönetim menüsü) spawn yakalamayı başlatır. */
    fun startCapture(p: Player) {
        val s = sessions[p.uniqueId] ?: return
        startSpawns(p, s)
    }

    private fun captureBlock(p: Player, s: Session, blockLoc: Location) {
        val arena = plugin.arenas.get(s.arenaId) ?: run {
            plugin.messages.send(p, "arena-admin.not-found")
            exit(p)
            return
        }
        when (val cap = s.capture) {
            is Capture.Spawns -> {
                val h = plugin.configuration.spawnHeight
                val loc = blockLoc.clone().add(0.5, h.toDouble(), 0.5)
                loc.yaw = p.location.yaw
                loc.pitch = p.location.pitch
                plugin.setup.addSpawn(s.arenaId, loc)
                plugin.arenas.reloadSingle(s.arenaId)
                p.playSound(p.location, org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 1.2f)
                val remaining = arena.maxPlayers - plugin.setup.spawnCount(s.arenaId)
                if (remaining <= 0) finishSpawns(p, s, early = false)
                else showSpawnTitle(p, s)
            }
            is Capture.Single -> {
                val loc = blockLoc.clone().add(0.5, 1.0, 0.5)
                loc.yaw = p.location.yaw
                loc.pitch = p.location.pitch
                if (plugin.setup.setLocation(s.arenaId, cap.key, loc)) {
                    plugin.arenas.reloadSingle(s.arenaId)
                    plugin.messages.send(p, "setup.single-saved", mapOf("key" to cap.key))
                    p.playSound(p.location, org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 1.5f)
                } else plugin.messages.send(p, "setup-admin.fail")
                s.capture = Capture.Idle
                dev.ensisdev.enspillars.gui.ArenaSetupMenu(plugin, s.arenaId).open(p)
            }
            Capture.Idle -> Unit
        }
    }

    private fun showSpawnTitle(p: Player, s: Session) {
        val arena = plugin.arenas.get(s.arenaId)
        val remaining = (arena?.maxPlayers ?: 0) - plugin.setup.spawnCount(s.arenaId)
        p.sendTitle(
            color(plugin.messages.get("setup.spawn-title", mapOf("left" to maxOf(0, remaining)))),
            color(plugin.messages.get("setup.spawn-subtitle")),
            5, 30, 5
        )
    }

    private fun finishSpawns(p: Player, s: Session, early: Boolean) {
        s.capture = Capture.Idle
        val count = plugin.setup.spawnCount(s.arenaId)
        if (early) plugin.messages.send(p, "setup.spawns-early", mapOf("count" to count))
        else {
            plugin.messages.send(p, "setup.spawns-done", mapOf("count" to count))
            p.playSound(p.location, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
        }
        dev.ensisdev.enspillars.gui.ArenaSetupMenu(plugin, s.arenaId).open(p)
    }

    fun requestSingle(p: Player, key: String) {
        val s = sessions[p.uniqueId] ?: return
        p.closeInventory()
        s.capture = Capture.Single(key)
        p.sendTitle(
            color(plugin.messages.get("setup.single-title", mapOf("key" to key))),
            color(plugin.messages.get("setup.single-subtitle")),
            5, 30, 5
        )
    }

    private fun color(s: String) = org.bukkit.ChatColor.translateAlternateColorCodes('&', s)
}
