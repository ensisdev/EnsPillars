package dev.ensisdev.enspillars.command

import dev.ensisdev.enspillars.EnsPillarsPlugin
import org.bukkit.command.CommandSender
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Yıkıcı komutlar için iki-aşamalı onay servisi (confirm-token deseni).
 *
 * Kullanım: `request(...)` ilk çağrıda uyarı mesajı gönderir ve `false` döner
 * (işlem YAPILMAZ). Aynı gönderen aynı anahtarla 60 sn içinde tekrar
 * çağırırsa `true` döner ve çağıran işlemi uygular. `confirm` kelimesi
 * içeren açık sözdizimi de kabul edilir: `/pof arena delete <id> confirm`.
 *
 * Legacy renk sistemi korunur: mesajlar messages.yml'de `&` kodludur.
 */
class ConfirmService(private val plugin: EnsPillarsPlugin) {
    companion object {
        const val WINDOW_MS = 60_000L
        const val CONFIRM_WORD = "confirm"
    }

    private data class Pending(val token: String, val at: Long, val detail: String)
    private val pending = ConcurrentHashMap<String, Pending>()

    private fun senderKey(s: CommandSender): String =
        (s as? org.bukkit.entity.Player)?.uniqueId?.toString() ?: "console:${s.name}"

    /**
     * İki-aşamalı onay kapısı.
     * @param explicitConfirm komut satırında `confirm` kelimesi varsa true geçilir.
     * @return true ise işlem uygulanabilir, false ise uyarı gönderildi ve beklenmeli.
     */
    fun check(s: CommandSender, actionKey: String, detail: String, explicitConfirm: Boolean = false): Boolean {
        val k = "${senderKey(s)}|$actionKey|${detail.lowercase()}"
        prune()
        if (explicitConfirm) {
            pending.remove(k)
            return true
        }
        val p = pending[k]
        if (p != null && System.currentTimeMillis() - p.at <= WINDOW_MS) {
            pending.remove(k)
            return true
        }
        val token = UUID.randomUUID().toString().take(8)
        pending[k] = Pending(token, System.currentTimeMillis(), detail)
        plugin.messages.send(s, "confirm.warn", mapOf("detail" to detail, "token" to token, "seconds" to (WINDOW_MS / 1000)))
        plugin.messages.send(s, "confirm.hint")
        return false
    }

    /** Bekleyen onayları temizler (logout/reload sonrası çağrılabilir). */
    fun clearFor(uuid: UUID) {
        pending.keys.removeIf { it.startsWith("$uuid|") }
    }

    fun clearAll() = pending.clear()

    private fun prune() {
        val now = System.currentTimeMillis()
        pending.entries.removeIf { now - it.value.at > WINDOW_MS }
    }
}
