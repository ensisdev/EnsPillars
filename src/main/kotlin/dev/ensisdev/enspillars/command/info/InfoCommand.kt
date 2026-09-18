package dev.ensisdev.enspillars.command.info

import dev.ensisdev.enspillars.EnsPillarsPlugin
import org.bukkit.command.*
import org.bukkit.entity.Player

/** /enspillars — bilgi ve bakım komutu. */
class InfoCommand(private val plugin: EnsPillarsPlugin) : CommandExecutor, TabCompleter {
    private val importer = ImportRunner(plugin)

    override fun onCommand(s: CommandSender, c: Command, l: String, a: Array<out String>): Boolean {
        if (!s.hasPermission("enspillars.use")) {
            deny(s)
            return true
        }
        if (!plugin.isCommandEnabled("enspillars")) {
            plugin.messages.send(s, "cmd.disabled")
            return true
        }
        if (a.isEmpty() || a[0].equals("info", true)) {
            info(s)
            return true
        }
        when (a[0].lowercase()) {
            "version", "info" -> info(s)
            "help" -> plugin.messages.list("help").forEach { msg ->
                s.sendMessage(if (s is Player) plugin.placeholders.apply(s, msg) else msg)
            }
            "reload" -> reload(s, a.drop(1))
            "import" -> {
                if (!s.hasPermission("enspillars.admin.import") && !s.hasPermission("enspillars.admin")) deny(s)
                else importer.run(s, a.drop(1))
            }
            "debug" -> if (s.hasPermission("enspillars.admin.debug") || s.hasPermission("enspillars.admin")) {
                plugin.messages.send(s, "debug.line", mapOf(
                    "version" to plugin.description.version,
                    "arenas" to plugin.arenas.size,
                    "platform" to plugin.platform.serverVersion,
                    "dialog" to plugin.platform.supportsDialogLayer(),
                    "papi" to plugin.placeholders.isHooked()
                ))
            } else deny(s)
            else -> plugin.messages.send(s, "unknown-command")
        }
        return true
    }

    private fun info(s: CommandSender) {
        plugin.messages.list("info").forEach { line ->
            var msg = line.replace("{version}", plugin.description.version)
            if (s is Player) msg = plugin.placeholders.apply(s, msg)
            s.sendMessage(msg)
        }
    }

    private val pendingReload = mutableSetOf<String>()

    private fun reload(s: CommandSender, a: List<String>) {
        if (!s.hasPermission("enspillars.admin.reload") && !s.hasPermission("enspillars.admin")) {
            deny(s)
            return
        }
        when (a.firstOrNull()?.lowercase() ?: "all") {
            "all" -> {
                val confirm = a.getOrNull(1)?.equals("confirm", true) == true || pendingReload.remove(s.name)
                if (plugin.arenas.hasActiveGames() && !confirm) {
                    plugin.messages.send(s, "reload.confirm-warn")
                    pendingReload.add(s.name)
                    return
                }
                plugin.reloadPlugin()
                plugin.messages.send(s, "reloaded")
            }
            "arena" -> {
                val confirm = a.getOrNull(1)?.equals("confirm", true) == true || pendingReload.remove(s.name)
                if (plugin.arenas.hasActiveGames() && !confirm) {
                    plugin.messages.send(s, "reload.confirm-warn")
                    pendingReload.add(s.name)
                    return
                }
                plugin.reloadArenas()
                plugin.messages.send(s, "reload.done", mapOf("category" to "arena"))
            }
            "config" -> {
                plugin.reloadConfigOnly()
                plugin.messages.send(s, "reload.done", mapOf("category" to "config"))
            }
            "messages" -> {
                plugin.reloadMessages()
                plugin.messages.send(s, "reload.done", mapOf("category" to "messages"))
            }
            "shop" -> {
                plugin.reloadShop()
                plugin.messages.send(s, "reload.done", mapOf("category" to "shop"))
            }
            "scoreboards" -> {
                plugin.reloadBoards()
                plugin.messages.send(s, "reload.done", mapOf("category" to "scoreboards"))
            }
            "hotbar" -> {
                plugin.reloadHotbar()
                plugin.messages.send(s, "reload.done", mapOf("category" to "hotbar"))
            }
            else -> plugin.messages.send(s, "reload.usage")
        }
    }

    private fun deny(s: CommandSender) = plugin.messages.send(s, "no-permission")

    override fun onTabComplete(s: CommandSender, c: Command, l: String, a: Array<out String>): MutableList<String>? {
        if (a.size == 1) {
            // P0: yetkisize admin dalları completion'da sızdırılmasın.
            val subs = mutableListOf("info", "version", "help")
            if (s.hasPermission("enspillars.admin.reload") || s.hasPermission("enspillars.admin")) subs += "reload"
            if (s.hasPermission("enspillars.admin.import") || s.hasPermission("enspillars.admin")) subs += "import"
            if (s.hasPermission("enspillars.admin.debug") || s.hasPermission("enspillars.admin")) subs += "debug"
            return subs.filter { it.startsWith(a[0], true) }.toMutableList()
        }
        if (a.size == 2 && a[0].equals("reload", true)) {
            return listOf("all", "config", "messages", "arena", "shop", "scoreboards", "hotbar")
                .filter { it.startsWith(a[1], true) }.toMutableList()
        }
        return mutableListOf()
    }
}
