package dev.ensisdev.enspillars.command

import dev.ensisdev.enspillars.EnsPillarsPlugin
import dev.ensisdev.enspillars.command.pof.ReplayCommands
import dev.ensisdev.enspillars.util.MessageService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.bukkit.command.CommandSender
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap

/**
 * 10/10 kapatma turu — journey kanıtı (seçenek b).
 *
 * Canlı sunucu koşusu yerine, istenen üç davranışı executable test olarak kilitler:
 * confirm-expiry, permission-denied, completion-filter.
 *
 * Not: tam MockBukkit sunucu önyüklemesi yerine JUnit5 + MockK kullanıldı;
 * komut sınıfları sunucu durumuna değil, izin/onay/completion dallarına göre
 * test edilir (daha hızlı, deterministik, aynı dallar).
 */
class ConfirmJourneyTest {

    private fun pluginWithMessages(): Pair<EnsPillarsPlugin, MessageService> {
        val plugin = mockk<EnsPillarsPlugin>()
        val messages = mockk<MessageService>(relaxed = true)
        every { plugin.messages } returns messages
        return plugin to messages
    }

    private fun sender(name: String, perms: Set<String>): CommandSender {
        val s = mockk<CommandSender>()
        every { s.name } returns name
        every { s.hasPermission(any<String>()) } answers { perms.contains(firstArg()) }
        return s
    }

    @Test
    fun `confirm two-phase warns first then consumes`() {
        val (plugin, messages) = pluginWithMessages()
        val svc = ConfirmService(plugin)
        val s = sender("Tester", emptySet())

        assertFalse(svc.check(s, "replay-delete", "id1", false))
        verify { messages.send(s, "confirm.warn", any()) }

        assertTrue(svc.check(s, "replay-delete", "id1", false))

        // Token tüketildi: yeni döngü yine uyarır.
        assertFalse(svc.check(s, "replay-delete", "id1", false))
    }

    @Test
    fun `confirm expires after window`() {
        val (plugin, _) = pluginWithMessages()
        val svc = ConfirmService(plugin)
        val s = sender("Tester", emptySet())
        assertEquals(60_000L, ConfirmService.WINDOW_MS)

        assertFalse(svc.check(s, "arena-delete", "arena1", false))

        // Bekleyen kaydı pencere dışına yaşlandır (beyaz-kutu: prune yolu).
        val f = ConfirmService::class.java.getDeclaredField("pending").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val pending = f.get(svc) as ConcurrentHashMap<String, *>
        val value = pending.values.first()
        val at = value.javaClass.getDeclaredField("at").apply { isAccessible = true }
        at.setLong(value, System.currentTimeMillis() - ConfirmService.WINDOW_MS - 1_000L)

        // Süresi dolmuş onay yürütmeye izin vermez, yeniden uyarır.
        assertFalse(svc.check(s, "arena-delete", "arena1", false))
    }

    @Test
    fun `replay denies without permission`() {
        val (plugin, messages) = pluginWithMessages()
        val cmds = ReplayCommands(plugin)
        val s = sender("NoPerm", emptySet())

        assertTrue(cmds.execute(s, listOf("list")))
        verify { messages.send(s, "no-permission", any()) }
    }

    @Test
    fun `replay delete requires admin branch permission`() {
        val (plugin, messages) = pluginWithMessages()
        val cmds = ReplayCommands(plugin)
        // Sadece replay izni var: delete dalı reddedilir, confirm kapısına hiç ulaşılmaz
        // (plugin.confirms stub'lanmadı — dokunulsa MockKException ile test düşer).
        val s = sender("User", setOf("enspillars.replay"))

        assertTrue(cmds.execute(s, listOf("delete", "id1")))
        verify { messages.send(s, "no-permission", any()) }
    }

    @Test
    fun `replay delete hidden from completion without admin`() {
        val (plugin, _) = pluginWithMessages()
        val cmds = ReplayCommands(plugin)
        val user = sender("User", setOf("enspillars.replay"))
        val admin = sender("Admin", setOf("enspillars.replay", "enspillars.admin.arena"))

        assertFalse("delete" in cmds.tabComplete(user, listOf("")))
        assertTrue("delete" in cmds.tabComplete(admin, listOf("")))
    }
}
