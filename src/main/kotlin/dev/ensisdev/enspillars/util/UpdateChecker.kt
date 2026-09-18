package dev.ensisdev.enspillars.util

import dev.ensisdev.enspillars.EnsPillarsPlugin
import java.net.HttpURLConnection
import java.net.URI

/**
 * Hafif güncelleme denetimi (konsola bilgi verir, indirme yapmaz).
 *
 * `config.yml -> update-check` doldurulmadan pasiftir; vitrin slug/repo'su
 * belli olunca aktif edilir. Desteklenen platformlar: `github`, `modrinth`.
 */
class UpdateChecker(private val plugin: EnsPillarsPlugin) {

    fun checkAsync() {
        val enabled = plugin.config.getBoolean("update-check.enabled", false)
        if (!enabled) return
        val platform = plugin.config.getString("update-check.platform", "github")!!.lowercase()
        val slug = plugin.config.getString("update-check.slug", "")!!.trim()
        val repo = plugin.config.getString("update-check.repo", "")!!.trim()
        if ((platform == "github" && repo.isEmpty()) || (platform == "modrinth" && slug.isEmpty())) {
            plugin.logger.warning("update-check açık ama hedef tanımsız (github için repo, modrinth için slug gerekir).")
            return
        }
        if (platform != "github" && platform != "modrinth") {
            plugin.logger.warning("update-check platformu desteklenmiyor: $platform (github|modrinth).")
            return
        }
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val latest = runCatching {
                when (platform) {
                    "github" -> readJsonField("https://api.github.com/repos/$repo/releases/latest", "tag_name")
                    else -> readJsonArrayFirst("https://api.modrinth.com/v2/project/$slug/version", "version_number")
                }
            }.getOrNull()?.trim()?.trimStart('v', 'V')
            if (latest.isNullOrEmpty()) return@Runnable
            val current = plugin.description.version.trim().trimStart('v', 'V')
            if (compareVersions(latest, current) > 0) {
                plugin.logger.info("Yeni EnsPillars sürümü mevcut: $latest (kurulu: $current).")
            }
        })
    }

    private fun get(url: String): String {
        val c = URI(url).toURL().openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "EnsPillars-UpdateChecker")
        c.setRequestProperty("Accept", "application/json")
        return c.inputStream.bufferedReader().use { it.readText() }
    }

    private fun readJsonField(url: String, field: String): String? {
        val m = Regex("\"$field\"\\s*:\\s*\"([^\"]+)\"").find(get(url)) ?: return null
        return m.groupValues[1]
    }

    private fun readJsonArrayFirst(url: String, field: String): String? {
        val body = get(url)
        val start = body.indexOf('{')
        if (start < 0) return null
        return Regex("\"$field\"\\s*:\\s*\"([^\"]+)\"").find(body.substring(start))?.groupValues?.get(1)
    }

    /** >0: latest daha yeni, 0: eşit, <0: kurulu daha yeni/bilinmiyor. */
    fun compareVersions(a: String, b: String): Int {
        val pa = a.split('.', '-')
        val pb = b.split('.', '-')
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrNull(i) ?: "0"
            val y = pb.getOrNull(i) ?: "0"
            val xn = x.toIntOrNull()
            val yn = y.toIntOrNull()
            val c = if (xn != null && yn != null) xn.compareTo(yn) else x.compareTo(y)
            if (c != 0) return c
        }
        return 0
    }
}
