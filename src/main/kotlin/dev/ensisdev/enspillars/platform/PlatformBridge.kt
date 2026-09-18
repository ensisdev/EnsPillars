package dev.ensisdev.enspillars.platform
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
class PlatformBridge(private val plugin: Plugin) {
    val serverVersion: String = runCatching { Bukkit.getMinecraftVersion() }.getOrDefault("1.20.1")
    val bukkitVersion: String = runCatching { Bukkit.getBukkitVersion() }.getOrDefault("unknown")
    fun supportsDialogLayer(): Boolean {
        val clean = serverVersion.substringBefore('-').substringBefore('+').trim()
        val p = clean.split('.')
        val minor = p.getOrNull(1)?.filter { it.isDigit() }?.toIntOrNull() ?: return false
        val patch = p.getOrNull(2)?.filter { it.isDigit() }?.toIntOrNull() ?: 0
        return minor > 21 || (minor == 21 && patch >= 7)
    }
}
