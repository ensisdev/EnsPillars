package dev.ensisdev.enspillars.util
import dev.ensisdev.enspillars.EnsPillarsPlugin
import org.bukkit.ChatColor
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
class MessageService(private val plugin: EnsPillarsPlugin) {
    private val file = File(plugin.dataFolder, "messages.yml")
    private var yaml = YamlConfiguration()
    init { reload() }
    fun reload() { yaml = YamlConfiguration.loadConfiguration(file) }
    fun get(path: String, replacements: Map<String, Any> = emptyMap()): String {
        var value = yaml.getString(path) ?: path
        replacements.forEach { (k, v) -> value = value.replace("{$k}", v.toString()) }
        return ChatColor.translateAlternateColorCodes('&', value)
    }
    fun list(path: String) = yaml.getStringList(path).map { ChatColor.translateAlternateColorCodes('&', it) }
    fun send(sender: CommandSender, path: String, replacements: Map<String, Any> = emptyMap()) {
        var msg = get(path, replacements)
        if (sender is org.bukkit.entity.Player && plugin.placeholdersReady()) msg = plugin.placeholders.apply(sender, msg)
        sender.sendMessage(msg)
    }

    /** Title gönderir (PAPI + renk destekli). */
    fun title(p: org.bukkit.entity.Player, headerKey: String, footerKey: String, replacements: Map<String, Any> = emptyMap()) {
        var h = get(headerKey, replacements)
        var f = get(footerKey, replacements)
        if (plugin.placeholdersReady()) {
            h = plugin.placeholders.apply(p, h)
            f = plugin.placeholders.apply(p, f)
        }
        p.sendTitle(h, f, 10, 40, 10)
    }
}
